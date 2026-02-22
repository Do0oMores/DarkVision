package top.mores.darkvision.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import top.mores.darkvision.Darkvision;
import top.mores.darkvision.net.*;

import java.util.*;

@Mod.EventBusSubscriber(modid = Darkvision.MODID)
public class DarkSightServer {
    private DarkSightServer() {}

    private static final int DURATION_TICKS = 20 * 10; // 按住上限10秒(示例)
    private static final int TARGET_PUSH_INTERVAL = 10; // 每10 tick推一次目标
    private static final int PULSE_INTERVAL = 20; // 每秒一次脉冲

    private static final Map<UUID, Active> active = new HashMap<>();

    private static class Active {
        int remaining;
        int seq = 0;
        int tick = 0;
        int pulseTick = 0;

        Active(int remaining) { this.remaining = remaining; }
    }

    public static void onPress(ServerPlayer sp) {
        // 规则校验（MVP先用世界名判断，后面桥接到插件 match）
        if (!isInHuntWorld(sp.level())) {
            DarkSightNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new S2C_DarkSightEnd((byte)3));
            return;
        }

        active.put(sp.getUUID(), new Active(DURATION_TICKS));
        DarkSightNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new S2C_DarkSightStart(DURATION_TICKS, (byte)0));
    }

    public static void onRelease(ServerPlayer sp) {
        Active a = active.remove(sp.getUUID());
        if (a != null) {
            DarkSightNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new S2C_DarkSightEnd((byte)2));
        }
    }

    private static boolean isInHuntWorld(Level level) {
        // MVP：对局世界名前缀判断；后续改成 BridgeAPI: plugin.isInMatch(uuid)
        //return level.dimension().location().getPath().contains("overworld");
        return true;
    }

    @SubscribeEvent
    public static void onServerPlayerTick(TickEvent.PlayerTickEvent e) {
        if (!(e.player instanceof ServerPlayer sp)) return;
        if (e.phase != TickEvent.Phase.END) return;

        if (sp.tickCount % 10 == 0) {
            EchoTracker.sample(sp);
        }

        Active a = active.get(sp.getUUID());
        if (a == null) return;

        a.tick++;
        a.pulseTick++;

        // 续航（按住）——这里用remaining做“能量”，按tick消耗
        a.remaining--;
        if (a.remaining <= 0) {
            active.remove(sp.getUUID());
            DarkSightNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new S2C_DarkSightEnd((byte)1));
            return;
        }

        // 每秒脉冲一次（强度由最近目标距离决定）
        if (a.pulseTick >= PULSE_INTERVAL) {
            a.pulseTick = 0;
            byte strength = computePulseStrength(sp);
            DarkSightNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new S2C_DarkSightPulse(strength, (byte)0));
        }

        // 周期推送目标
        if (a.tick % TARGET_PUSH_INTERVAL == 0) {
            List<S2C_DarkSightTargets.Target> targets = computeTargets(sp);
            DarkSightNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new S2C_DarkSightTargets(++a.seq, targets));
        }
    }

    private static byte computePulseStrength(ServerPlayer sp) {
        // MVP：用撤离点/线索点等“关键点”最近距离；这里先写死示例
        double d = 30.0; // TODO：算最近目标距离
        // 映射：越近越强
        int s = (int) Math.max(0, Math.min(100, 100 - (d * 3)));
        return (byte) s;
    }

    private static List<S2C_DarkSightTargets.Target> computeTargets(ServerPlayer sp) {
        List<S2C_DarkSightTargets.Target> list = new ArrayList<>();

        // 玩家回声：遍历其他玩家
        var server = sp.server;
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == sp) continue;
            if (other.level() != sp.level()) continue;

            // 距离过滤（80m）
            double maxDistSq = 80.0 * 80.0;
            if (other.distanceToSqr(sp) > maxDistSq) continue;

            List<EchoTracker.EchoPoint> echoes = EchoTracker.getEchoes(other.getUUID());
            for (EchoTracker.EchoPoint ep : echoes) {
                // 计算强度：越新越强（0..100）
                // age 0 -> 100, age MAX_AGE -> 10
                int maxAge = 20 * 6;
                float t = Math.min(1f, ep.ageTicks() / (float) maxAge);
                int strength = (int) (100 - t * 80); // 100..20
                if (strength < 10) strength = 10;

                // TTL短一点，让客户端平滑刷新
                short ttl = 30;

                list.add(new S2C_DarkSightTargets.Target(
                        (byte)4,  // type=4 回声点
                        -1,
                        ep.pos(),
                        (byte) strength,
                        ttl
                ));
            }
        }
        return list;
    }
}

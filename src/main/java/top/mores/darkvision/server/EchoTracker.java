package top.mores.darkvision.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.mores.darkvision.Darkvision;

import java.util.*;

@Mod.EventBusSubscriber(modid = Darkvision.MODID)
public class EchoTracker {
    private EchoTracker() {}

    // 参数：0.5s采样，保留6s => 12点
    private static final int SAMPLE_INTERVAL_TICKS = 10;
    private static final int MAX_POINTS = 12;
    private static final int MAX_AGE_TICKS = 20 * 6; // 6s
    private static final double MIN_MOVE_SQ = 1.0;   // <1m不采样(可调)

    private static final Map<UUID, Deque<EchoPoint>> ECHOS = new HashMap<>();
    private static final Map<UUID, BlockPos> LAST_POS = new HashMap<>();
    private static int tickCounter = 0;

    public record EchoPoint(BlockPos pos, int ageTicks) {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter % SAMPLE_INTERVAL_TICKS != 0) return;

        // 这里不直接遍历玩家（需要在某处拿到player list）
        // 你可以在 DarkSightServer.onServerPlayerTick 里采样，也可以在这里通过 server.getPlayerList()
        // 为了骨架清晰，这里提供一个 public API：sample(player)
    }

    public static void sample(ServerPlayer p) {
        UUID id = p.getUUID();

        // 量化：按1格（BlockPos本身就是1m网格）。如果你想2m网格：pos = new BlockPos((x/2)*2, y, (z/2)*2)
        BlockPos cur = p.blockPosition();

        BlockPos last = LAST_POS.get(id);
        if (last != null) {
            double dx = cur.getX() + 0.5 - (last.getX() + 0.5);
            double dz = cur.getZ() + 0.5 - (last.getZ() + 0.5);
            if ((dx*dx + dz*dz) < MIN_MOVE_SQ) {
                decay(id);
                return;
            }
        }
        LAST_POS.put(id, cur);

        Deque<EchoPoint> q = ECHOS.computeIfAbsent(id, k -> new ArrayDeque<>());
        // 新点 age=0
        q.addLast(new EchoPoint(cur, 0));

        while (q.size() > MAX_POINTS) q.pollFirst();
        decay(id);
    }

    private static void decay(UUID id) {
        Deque<EchoPoint> q = ECHOS.get(id);
        if (q == null) return;

        // 增加所有点的age，并移除过期点
        Deque<EchoPoint> newQ = new ArrayDeque<>(q.size());
        for (EchoPoint ep : q) {
            int age = ep.ageTicks() + SAMPLE_INTERVAL_TICKS;
            if (age <= MAX_AGE_TICKS) {
                newQ.addLast(new EchoPoint(ep.pos(), age));
            }
        }
        ECHOS.put(id, newQ);
    }

    public static List<EchoPoint> getEchoes(UUID playerId) {
        Deque<EchoPoint> q = ECHOS.get(playerId);
        if (q == null || q.isEmpty()) return List.of();
        return List.copyOf(q);
    }

    public static void remove(UUID playerId) {
        ECHOS.remove(playerId);
        LAST_POS.remove(playerId);
    }
}

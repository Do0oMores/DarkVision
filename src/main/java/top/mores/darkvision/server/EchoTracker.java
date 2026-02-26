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

    private static final int SAMPLE_INTERVAL_TICKS = 10;     // 0.5s
    private static final int MAX_POINTS = 2;                // 6s * 2
    private static final int MAX_AGE_TICKS = 20 * 6;         // 6s
    private static final double MIN_MOVE_SQ = 1.0;           // <1m 不采样

    private static final Map<UUID, Deque<EchoPoint>> ECHOS = new HashMap<>();
    private static final Map<UUID, BlockPos> LAST_POS = new HashMap<>();

    public record EchoPoint(BlockPos pos, int ageTicks) {}

    /** ✅ 自动采样入口：每个玩家每 10 tick 采样一次 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.player instanceof ServerPlayer sp)) return;

        // 每 10 tick 采样一次
        if (sp.tickCount % SAMPLE_INTERVAL_TICKS != 0) return;

        sample(sp);
    }

    public static void sample(ServerPlayer p) {
        UUID id = p.getUUID();
        BlockPos cur = p.blockPosition(); // 1m 量化

        BlockPos last = LAST_POS.get(id);
        if (last != null) {
            double dx = (cur.getX() + 0.5) - (last.getX() + 0.5);
            double dz = (cur.getZ() + 0.5) - (last.getZ() + 0.5);

            // ✅ 无明显移动：只做老化（让回声自然消失）
            if ((dx * dx + dz * dz) < MIN_MOVE_SQ) {
                decay(id);
                return;
            }
        }
        LAST_POS.put(id, cur);

        Deque<EchoPoint> q = ECHOS.computeIfAbsent(id, k -> new ArrayDeque<>());

        // ✅ 新点：age=0
        q.addLast(new EchoPoint(cur, 0));
        while (q.size() > MAX_POINTS) q.pollFirst();

        // ✅ 每次采样都老化一次
        decay(id);
    }

    /** 老化：每次调用相当于过了 SAMPLE_INTERVAL_TICKS */
    private static void decay(UUID id) {
        Deque<EchoPoint> q = ECHOS.get(id);
        if (q == null || q.isEmpty()) return;

        Deque<EchoPoint> newQ = new ArrayDeque<>(q.size());
        for (EchoPoint ep : q) {
            int age = ep.ageTicks() + SAMPLE_INTERVAL_TICKS;
            if (age <= MAX_AGE_TICKS) newQ.addLast(new EchoPoint(ep.pos(), age));
        }
        if (newQ.isEmpty()) {
            ECHOS.remove(id);
            LAST_POS.remove(id);
        } else {
            ECHOS.put(id, newQ);
        }
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

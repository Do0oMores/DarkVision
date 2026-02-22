package top.mores.darkvision.client;

import net.minecraft.core.BlockPos;
import top.mores.darkvision.net.S2C_DarkSightTargets;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DarkSightClientState {
    private DarkSightClientState(){}

    private static boolean active = false;
    private static int remainingTicks = 0;
    private static int lastSeq = 0;

    private static int pulseTicks = 0;
    private static float pulseStrength = 0f; // 0..1

    // 目标缓存：pos -> (strength, ttl)
    private static final Map<BlockPos, TargetEntry> targets = new HashMap<>();

    public static class TargetEntry {
        public float strength01;
        public int ttl;
        public byte type;
        public TargetEntry(float s, int ttl, byte type){ this.strength01 = s; this.ttl = ttl; this.type = type; }
    }

    public static boolean isActive(){ return active; }
    public static float getPulseStrength(){ return pulseStrength; }
    public static Map<BlockPos, TargetEntry> getTargets(){ return targets; }

    public static void start(int durationTicks, byte mode){
        active = true;
        remainingTicks = durationTicks;
        // 可按mode切不同滤镜强度
        lastSeq=0;
        targets.clear();
    }

    public static void end(){
        active = false;
        remainingTicks = 0;
        lastSeq=0;
        targets.clear();
        pulseStrength = 0f;
        pulseTicks = 0;
    }

    public static void pulse(byte strength){
        pulseStrength = Math.max(0f, Math.min(1f, (strength & 0xFF) / 100f));
        pulseTicks = 10; // 脉冲持续10tick衰减
    }

    public static void updateTargets(int seq, List<S2C_DarkSightTargets.Target> list){
        if (seq <= lastSeq) return;
        lastSeq = seq;

        for (S2C_DarkSightTargets.Target t : list) {
            float s01 = Math.max(0f, Math.min(1f, (t.strength() & 0xFF) / 100f));
            targets.put(t.pos(), new TargetEntry(s01, t.ttlTicks(), t.type()));
        }
    }

    public static void clientTick(){
        if (!active) return;

        remainingTicks--;
        if (remainingTicks <= 0) {
            end();
            return;
        }

        // 脉冲衰减
        if (pulseTicks > 0) {
            pulseTicks--;
            pulseStrength *= 0.92f;
        } else {
            pulseStrength *= 0.85f;
        }

        // 目标ttl衰减
        Iterator<Map.Entry<BlockPos, TargetEntry>> it = targets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, TargetEntry> e = it.next();
            e.getValue().ttl--;
            if (e.getValue().ttl <= 0) it.remove();
        }
    }
}

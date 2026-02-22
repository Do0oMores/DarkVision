package top.mores.darkvision.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.mores.darkvision.Darkvision;

import java.util.Map;

@Mod.EventBusSubscriber(modid = Darkvision.MODID, value = Dist.CLIENT)
public class DarkSightWorldRenderer {

    // 回声点 type（如果你不确定服务端type是否为4，先把下面这行注释掉过滤做验证）
    private static final byte TYPE_ECHO = 4;

    // 调试期建议 true，确保不会被地面遮挡
    private static final boolean IGNORE_DEPTH = true;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!DarkSightClientState.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack ps = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        var lineConsumer = bufferSource.getBuffer(RenderType.lines());

        float time = (mc.level.getGameTime() + event.getPartialTick()) * 0.15f;

        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);

        if (IGNORE_DEPTH) RenderSystem.disableDepthTest();

        for (Map.Entry<BlockPos, DarkSightClientState.TargetEntry> en : DarkSightClientState.getTargets().entrySet()) {
            BlockPos pos = en.getKey();
            DarkSightClientState.TargetEntry te = en.getValue();

            // ✅ 如果你怀疑服务端发的type不是4，先注释这行，确保“任何target都画”
           // if (te.type != TYPE_ECHO) continue;

            // 距离过滤
            double dx = (pos.getX() + 0.5) - mc.player.getX();
            double dz = (pos.getZ() + 0.5) - mc.player.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq > 80.0 * 80.0) continue;

            float s = Mth.clamp(te.strength01, 0f, 1f);

            // 呼吸闪烁：每个点相位不同
            float breathe = 0.72f + 0.28f * Mth.sin(time + (pos.asLong() % 1000) * 0.01f);

            // 金黄色（你要的）
            float r = 1.00f;
            float g = 0.84f;
            float b = 0.20f;

            // 残影“大小”随强度变化（贴地）
            double radius = 0.10 + 0.30 * s;      // 0.10 ~ 0.40
            double y = pos.getY() + 0.06;          // 抬高防Z-fighting
            double cx = pos.getX() + 0.5;
            double cz = pos.getZ() + 0.5;

            // 画一个很薄的“贴地小框”（像残影点的底座）
            AABB base = new AABB(
                    cx - radius, y, cz - radius,
                    cx + radius, y + 0.001, cz + radius
            );

            // 用 renderLineBox 画出来（稳定可见）
            float alpha = 0.35f + 0.65f * s * breathe; // 0..1
            LevelRenderer.renderLineBox(ps, lineConsumer, base,
                    r, g, b, alpha);

            // 再画一个更小的内框，让它更像“残影印记”
            double inner = radius * 0.55;
            AABB innerBox = new AABB(
                    cx - inner, y, cz - inner,
                    cx + inner, y + 0.001, cz + inner
            );
            LevelRenderer.renderLineBox(ps, lineConsumer, innerBox,
                    r, g, b, Math.min(1f, alpha + 0.15f));
        }

        if (IGNORE_DEPTH) RenderSystem.enableDepthTest();

        ps.popPose();

        // 提交 lines
        bufferSource.endBatch(RenderType.lines());
    }
}
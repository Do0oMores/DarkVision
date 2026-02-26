package top.mores.darkvision.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.mores.darkvision.Darkvision;

import java.util.Map;

@Mod.EventBusSubscriber(modid = Darkvision.MODID, value = Dist.CLIENT)
public class DarkSightWorldRenderer {

    private static final byte TYPE_ECHO = 4;

    // 你想“灵视透雾”就 true；想更真实就 false
    private static final boolean IGNORE_DEPTH = true;

    // 每个回声点绘制的雾片层数（3~6）
    private static final int LAYERS = 4;

    private static final ResourceLocation FOG_A =
            new ResourceLocation(Darkvision.MODID, "textures/misc/echo_fog_a.png");
    private static final ResourceLocation FOG_B =
            new ResourceLocation(Darkvision.MODID, "textures/misc/echo_fog_b.png");

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!DarkSightClientState.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack ps = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();

        // 两个纹理各自一个 batch（交替使用）
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer vcA = bufferSource.getBuffer(RenderType.entityTranslucent(FOG_A));
        VertexConsumer vcB = bufferSource.getBuffer(RenderType.entityTranslucent(FOG_B));

        // 时间参数：影响漂浮/呼吸/交替
        float time = (mc.level.getGameTime() + event.getPartialTick()) * 0.05f;
        var camRot = event.getCamera().rotation();

        ps.pushPose();
        ps.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.enableBlend();
        RenderSystem.disableCull(); // 雾双面更自然
        if (IGNORE_DEPTH) RenderSystem.disableDepthTest();

        for (Map.Entry<BlockPos, DarkSightClientState.TargetEntry> en : DarkSightClientState.getTargets().entrySet()) {
            BlockPos pos = en.getKey();
            DarkSightClientState.TargetEntry te = en.getValue();
            if (te.type != TYPE_ECHO) continue;

            // 距离过滤
            double dx = (pos.getX() + 0.5) - mc.player.getX();
            double dz = (pos.getZ() + 0.5) - mc.player.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq > 80.0 * 80.0) continue;

            float strength = Mth.clamp(te.strength01, 0f, 1f);

            // ✅ 渐渐消失：用 ttl 做 life（你服务端 ttl=30）
            float life01 = Mth.clamp(te.ttl / 30f, 0f, 1f);

            // 雾团中心：略微抬高，像雾浮在地面上
            float cx = pos.getX() + 0.5f;
            float cy = pos.getY() + 0.25f + 0.12f * strength;
            float cz = pos.getZ() + 0.5f;

            // 金黄色（你要的）
            float r = 1.00f;
            float g = 0.84f;
            float b = 0.20f;

            // 基础大小与透明度
            float baseSize = 0.40f + 0.95f * strength;   // 0.40 ~ 1.35
            float baseAlpha = (0.10f + 0.32f * strength) * life01; // fade out

            // 呼吸闪烁：每个点相位不同
            float phase = (pos.asLong() % 1000) * 0.01f;
            float breathe = 0.78f + 0.22f * Mth.sin(time * 6f + phase);

            // 距离衰减：远处更淡（别让远处一堆雾抢视线）
            float dist = (float) Math.sqrt(distSq);
            float distFade = Mth.clamp(1.0f - (dist / 80.0f), 0.0f, 1.0f);

            for (int i = 0; i < LAYERS; i++) {
                float layer = i / (float) LAYERS;

                // ✅ 两张纹理交替：层 + 时间一起决定（更“翻涌”）
                int swap = ((i + (int)(time * 2f)) & 1);
                VertexConsumer vc = (swap == 0) ? vcA : vcB;

                // 运动：绕点轻微盘旋 + 上下漂浮
                float t = time + phase + i * 1.7f;
                float ox = 0.16f * Mth.sin(t * 0.9f) * (0.6f + strength);
                float oz = 0.16f * Mth.cos(t * 0.7f) * (0.6f + strength);
                float oy = 0.10f * Mth.sin(t * 0.6f);

                // 层越外越大、越淡
                float size = baseSize * (0.85f + layer * 0.65f) * (0.92f + 0.08f * breathe);
                float alpha = baseAlpha * (1.0f - layer * 0.38f) * (0.80f + 0.20f * breathe);
                alpha *= (0.35f + 0.65f * distFade);

                drawBillboardQuad(ps, vc,
                        cx + ox, cy + oy, cz + oz,
                        size,
                        r, g, b, alpha,
                        camRot);
            }
        }

        if (IGNORE_DEPTH) RenderSystem.enableDepthTest();
        RenderSystem.enableCull();

        ps.popPose();

        // ✅ 两个 batch 都要提交
        bufferSource.endBatch(RenderType.entityTranslucent(FOG_A));
        bufferSource.endBatch(RenderType.entityTranslucent(FOG_B));
    }

    /** billboard 雾片：始终朝向相机 */
    private static void drawBillboardQuad(PoseStack ps, VertexConsumer vc,
                                          float x, float y, float z,
                                          float size,
                                          float r, float g, float b, float a,
                                          org.joml.Quaternionf camRot) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(camRot);

        PoseStack.Pose pose = ps.last();
        var mat = pose.pose();

        float hs = size * 0.5f;

        // UV：整张图
        vc.vertex(mat, -hs, -hs, 0).color(r, g, b, a).uv(0f, 1f).endVertex();
        vc.vertex(mat, -hs,  hs, 0).color(r, g, b, a).uv(0f, 0f).endVertex();
        vc.vertex(mat,  hs,  hs, 0).color(r, g, b, a).uv(1f, 0f).endVertex();
        vc.vertex(mat,  hs, -hs, 0).color(r, g, b, a).uv(1f, 1f).endVertex();

        ps.popPose();
    }
}
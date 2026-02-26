package top.mores.darkvision.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
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

    private static final boolean IGNORE_DEPTH = true;

    private static final int LAYERS = 4;

    private static final ResourceLocation FOG_A =
            new ResourceLocation(Darkvision.MODID, "textures/misc/fog_a.png");
    private static final ResourceLocation FOG_B =
            new ResourceLocation(Darkvision.MODID, "textures/misc/fog_b.png");

    // 交叉淡入淡出速度（越小越慢）
    private static final float XFADE_SPEED = 0.55f;

    // 呼吸速度（越小越慢）
    private static final float BREATHE_SPEED = 2.0f;

    // 透明度增强
    private static final float ALPHA_BOOST = 2.35f;

    // 雾团整体抬高
    private static final float HEIGHT_BOOST = 0.45f;

    // 漂浮幅度增强
    private static final float FLOAT_BOOST = 1.55f;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!DarkSightClientState.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack ps = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();

        // 时间参数：影响漂浮/呼吸/交替
        float time = (mc.level.getGameTime() + event.getPartialTick()) * 0.02f;
        var camRot = event.getCamera().rotation();

        ps.pushPose();
        ps.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.blendFunc(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE
        );

        if (IGNORE_DEPTH) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        }

        RenderSystem.setShader(GameRenderer::getPositionColorTexLightmapShader);

        renderFogBatch(mc, ps, time, camRot, true);
        renderFogBatch(mc, ps, time, camRot, false);

        if (IGNORE_DEPTH) {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        ps.popPose();
    }

    private static void renderFogBatch(Minecraft mc, PoseStack ps, float time, org.joml.Quaternionf camRot, boolean drawA) {
        var tess = Tesselator.getInstance();
        var bb = tess.getBuilder();

        RenderSystem.setShaderTexture(0, drawA ? FOG_A : FOG_B);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);

        for (Map.Entry<BlockPos, DarkSightClientState.TargetEntry> en : DarkSightClientState.getTargets().entrySet()) {
            BlockPos pos = en.getKey();
            DarkSightClientState.TargetEntry te = en.getValue();
            if (te.type != TYPE_ECHO) continue;

            double dx = (pos.getX() + 0.5) - mc.player.getX();
            double dz = (pos.getZ() + 0.5) - mc.player.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq > 80.0 * 80.0) continue;

            float strength = Mth.clamp(te.strength01, 0f, 1f);
            float life01 = Mth.clamp(te.ttl / 30f, 0f, 1f);

            float cx = pos.getX() + 0.5f;
            float cy = pos.getY() + 0.5f + HEIGHT_BOOST + 0.18f * strength;
            float cz = pos.getZ() + 0.5f;

            float r = 1.00f;
            float g = 0.92f;
            float b = 0.35f;

            float baseSize  = 0.40f + 0.95f * strength;

            float baseAlpha = (0.14f + 0.42f * strength) * life01;

            float phase = (pos.asLong() % 1000) * 0.01f;

            float breathe = 0.88f + 0.12f * Mth.sin(time * BREATHE_SPEED + phase);

            for (int i = 0; i < LAYERS; i++) {
                float layer = i / (float) LAYERS;

                float t = time + phase + i * 1.7f;

                float ox = 0.16f * Mth.sin(t * 0.9f) * (0.6f + strength);
                float oz = 0.16f * Mth.cos(t * 0.7f) * (0.6f + strength);

                float oy = (0.10f * Mth.sin(t * 0.6f)) * FLOAT_BOOST;

                float uOff = 0.10f * Mth.sin(t * 0.35f);
                float vOff = 0.10f * Mth.cos(t * 0.28f);

                float xPhase = ((pos.asLong() & 1023) * 0.006f) + i * 0.35f;
                float mixA = 0.5f + 0.5f * Mth.sin(t * XFADE_SPEED + xPhase);
                float mix = drawA ? mixA : (1.0f - mixA);

                float size = baseSize * (0.85f + layer * 0.65f) * (0.94f + 0.06f * breathe);
                float glowCurve = life01 * life01;

                float alpha = baseAlpha
                        * (1.0f - layer * 0.34f)
                        * (0.86f + 0.14f * breathe)
                        * glowCurve
                        * mix;

                alpha *= ALPHA_BOOST;

                alpha = Mth.clamp(alpha, 0.0f, 0.95f);

                drawBillboardQuadImmediate(ps, bb,
                        cx + ox, cy + oy, cz + oz,
                        size,
                        r, g, b, alpha,
                        camRot,
                        uOff, vOff);
            }
        }

        BufferUploader.drawWithShader(bb.end());
    }

    private static void drawBillboardQuadImmediate(PoseStack ps, BufferBuilder bb,
                                                   float x, float y, float z,
                                                   float size,
                                                   float r, float g, float b, float a,
                                                   org.joml.Quaternionf camRot,
                                                   float uOff, float vOff) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(camRot);

        PoseStack.Pose pose = ps.last();
        var mat = pose.pose();

        float hs = size * 0.5f;

        int light = LightTexture.FULL_BRIGHT;

        float u0 = uOff, v0 = vOff;
        float u1 = uOff + 1f, v1 = vOff + 1f;

        bb.vertex(mat, -hs, -hs, 0).color(r, g, b, a).uv(u0, v1).uv2(light).endVertex();
        bb.vertex(mat, -hs,  hs, 0).color(r, g, b, a).uv(u0, v0).uv2(light).endVertex();
        bb.vertex(mat,  hs,  hs, 0).color(r, g, b, a).uv(u1, v0).uv2(light).endVertex();
        bb.vertex(mat,  hs, -hs, 0).color(r, g, b, a).uv(u1, v1).uv2(light).endVertex();

        ps.popPose();
    }
}
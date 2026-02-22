package top.mores.darkvision.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.mores.darkvision.Darkvision;

import java.util.Map;

import static java.lang.Math.sin;

@Mod.EventBusSubscriber(modid = Darkvision.MODID, value = Dist.CLIENT)
public class DarkSightWorldRenderer {
    // type=4 回声点
    private static final byte TYPE_ECHO = 4;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!DarkSightClientState.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // 相机位置，用于把世界坐标转换到渲染坐标
        Vec3 cam = e.getCamera().getPosition();

        // 轻微呼吸闪烁：基于世界时间
        float time = (mc.level.getGameTime() + e.getPartialTick()) * 0.15f;

        // 我们用半透明三角形渲染一个贴地“十字残影”
        //（后续你换成纹理环形即可）
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        // 某些环境下需要手动设置shader
        com.mojang.blaze3d.systems.RenderSystem.setShader(GameRenderer::getPositionColorShader);

        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (Map.Entry<BlockPos, DarkSightClientState.TargetEntry> entry : DarkSightClientState.getTargets().entrySet()) {
            BlockPos pos = entry.getKey();
            DarkSightClientState.TargetEntry te = entry.getValue();
            if (te.type != TYPE_ECHO) continue;

            // 距离过滤：客户端再过滤一次，省性能
            double dx = (pos.getX() + 0.5) - mc.player.getX();
            double dz = (pos.getZ() + 0.5) - mc.player.getZ();
            double distSq = dx*dx + dz*dz;
            if (distSq > 80.0 * 80.0) continue;

            // 残影点位置（贴地略抬高避免Z-fighting）
            // RenderLevelStageEvent 使用相机相对坐标，必须减去相机位置。
            double x = (pos.getX() + 0.5) - cam.x;
            double y = (pos.getY() + 0.05) - cam.y; // 贴地
            double z = (pos.getZ() + 0.5) - cam.z;

            // 强度：0..1
            float s = clamp01(te.strength01);

            // 呼吸：强度越高闪烁越明显
            float breathe = 0.75f + 0.25f * (float)sin(time + (pos.asLong() % 1000) * 0.01);
            float alpha = 0.15f + 0.55f * s * breathe;

            // 尺寸：强度越大越明显（但仍然“隐蔽”）
            float size = 0.15f + 0.25f * s; // 0.15~0.40

            // 颜色：偏冷的淡蓝白（你也可以改成偏金/偏红）
            float r = 0.75f;
            float g = 0.85f;
            float b = 1.00f;

            // 画一个“十字残影”（两个薄矩形叠加）
            // 横条
            addQuad(bb, x - size, y, z - 0.03, x + size, y, z + 0.03, r, g, b, alpha);
            // 竖条
            addQuad(bb, x - 0.03, y, z - size, x + 0.03, y, z + size, r, g, b, alpha);
        }

        // 提交
        BufferUploader.drawWithShader(bb.end());

    }

    private static void addQuad(BufferBuilder bb,
                                double x1, double y, double z1,
                                double x2, double y2, double z2,
                                float r, float g, float b, float a) {
        // 这里画的是水平面上的矩形（y1=y2），四个点按顺序
        bb.vertex(x1, y, z1).color(r, g, b, a).endVertex();
        bb.vertex(x1, y, z2).color(r, g, b, a).endVertex();
        bb.vertex(x2, y, z2).color(r, g, b, a).endVertex();
        bb.vertex(x2, y, z1).color(r, g, b, a).endVertex();
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }
}

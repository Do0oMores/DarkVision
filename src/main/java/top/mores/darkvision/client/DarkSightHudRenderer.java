package top.mores.darkvision.client;

import net.minecraft.client.gui.GuiGraphics;

public final class DarkSightHudRenderer {
    private DarkSightHudRenderer() {}

    public static void render(GuiGraphics gg, int w, int h) {
        // 1) 灰黑幕（ARGB）
        gg.fill(0, 0, w, h, 0x88000000);

        // 2) 暗角（简单四边）
        int b = 28;
        gg.fill(0, 0, w, b, 0xAA000000);
        gg.fill(0, h - b, w, h, 0xAA000000);
        gg.fill(0, 0, b, h, 0xAA000000);
        gg.fill(w - b, 0, w, h, 0xAA000000);

        // 3) 脉冲闪动（强度来自客户端状态）
        float s = DarkSightClientState.getPulseStrength(); // 0..1
        int alpha = (int) (s * 120);
        if (alpha > 0) {
            int pad = 70;
            gg.fill(pad, pad, w - pad, h - pad, (alpha << 24)); // 只有 alpha 的黑层
        }
    }
}

package top.mores.darkvision.client;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import top.mores.darkvision.Darkvision;
import top.mores.darkvision.net.C2S_ToggleDarkSight;
import top.mores.darkvision.net.DarkSightNet;

@Mod.EventBusSubscriber(modid = Darkvision.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DarkSightClient {

    public static KeyMapping DARKSIGHT_KEY;

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent e) {
        DARKSIGHT_KEY = new KeyMapping(
                "key.darkvision.darksight",
                GLFW.GLFW_KEY_V,
                "key.categories.darkvision"
        );
        e.register(DARKSIGHT_KEY);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("darksight", (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
            if (!DarkSightClientState.isActive()) return;
            DarkSightHudRenderer.render(guiGraphics, screenWidth, screenHeight);
        });
    }

    /** 在 ClientSetup 时调用：注册 FORGE 总线事件（tick、世界渲染等） */
    public static void registerForgeBus() {
        MinecraftForge.EVENT_BUS.register(ForgeBus.class);
        // 你后面做回声渲染时再打开：
        // MinecraftForge.EVENT_BUS.register(DarkSightWorldRenderer.class);
    }

    @Mod.EventBusSubscriber(modid = Darkvision.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {
        private static boolean lastDown = false;

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;

            // 驱动本地状态衰减等
            DarkSightClientState.clientTick();

            if (DARKSIGHT_KEY == null) return;
            boolean down = DARKSIGHT_KEY.isDown();
            if (down && !lastDown) DarkSightClientState.start(200, (byte)0);
            if (down && !lastDown) {
                DarkSightNet.CHANNEL.sendToServer(new C2S_ToggleDarkSight(C2S_ToggleDarkSight.Action.PRESS));
            } else if (!down && lastDown) {
                DarkSightNet.CHANNEL.sendToServer(new C2S_ToggleDarkSight(C2S_ToggleDarkSight.Action.RELEASE));
            }
            lastDown = down;
        }
    }
}

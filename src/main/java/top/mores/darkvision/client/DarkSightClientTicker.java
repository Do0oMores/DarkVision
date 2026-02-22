package top.mores.darkvision.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.mores.darkvision.Darkvision;

@Mod.EventBusSubscriber(modid = Darkvision.MODID, value = Dist.CLIENT)
public final class DarkSightClientTicker {
    private DarkSightClientTicker() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            DarkSightClientState.clientTick();
        }
    }
}

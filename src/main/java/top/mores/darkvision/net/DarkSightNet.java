package top.mores.darkvision.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class DarkSightNet {
    private DarkSightNet() {}

    public static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("darkvision", "darksight"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals
    );

    private static int id = 0;

    public static void register() {
        CHANNEL.messageBuilder(C2S_ToggleDarkSight.class, id++)
                .encoder(C2S_ToggleDarkSight::encode)
                .decoder(C2S_ToggleDarkSight::decode)
                .consumerMainThread(C2S_ToggleDarkSight::handle)
                .add();

        CHANNEL.messageBuilder(S2C_DarkSightStart.class, id++)
                .encoder(S2C_DarkSightStart::encode)
                .decoder(S2C_DarkSightStart::decode)
                .consumerMainThread(S2C_DarkSightStart::handle)
                .add();

        CHANNEL.messageBuilder(S2C_DarkSightEnd.class, id++)
                .encoder(S2C_DarkSightEnd::encode)
                .decoder(S2C_DarkSightEnd::decode)
                .consumerMainThread(S2C_DarkSightEnd::handle)
                .add();

        CHANNEL.messageBuilder(S2C_DarkSightPulse.class, id++)
                .encoder(S2C_DarkSightPulse::encode)
                .decoder(S2C_DarkSightPulse::decode)
                .consumerMainThread(S2C_DarkSightPulse::handle)
                .add();

        CHANNEL.messageBuilder(S2C_DarkSightTargets.class, id++)
                .encoder(S2C_DarkSightTargets::encode)
                .decoder(S2C_DarkSightTargets::decode)
                .consumerMainThread(S2C_DarkSightTargets::handle)
                .add();
    }
}

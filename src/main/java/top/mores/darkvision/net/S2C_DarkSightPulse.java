package top.mores.darkvision.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import top.mores.darkvision.client.DarkSightClientState;

import java.util.function.Supplier;

public class S2C_DarkSightPulse {
    public final byte strength; // 0-100
    public final byte pulseType;

    public S2C_DarkSightPulse(byte strength, byte pulseType) {
        this.strength = strength;
        this.pulseType = pulseType;
    }

    public static void encode(S2C_DarkSightPulse msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.strength);
        buf.writeByte(msg.pulseType);
    }

    public static S2C_DarkSightPulse decode(FriendlyByteBuf buf) {
        return new S2C_DarkSightPulse(buf.readByte(), buf.readByte());
    }

    public static void handle(S2C_DarkSightPulse msg, Supplier<NetworkEvent.Context> ctxSup) {
        ctxSup.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> DarkSightClientState.pulse(msg.strength)));
        ctxSup.get().setPacketHandled(true);
    }
}

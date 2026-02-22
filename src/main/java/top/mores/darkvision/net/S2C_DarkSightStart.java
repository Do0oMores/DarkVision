package top.mores.darkvision.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.mores.darkvision.client.DarkSightClientState;

import java.util.function.Supplier;

public class S2C_DarkSightStart {
    public final int durationTicks;
    public final byte mode;

    public S2C_DarkSightStart(int durationTicks, byte mode) {
        this.durationTicks = durationTicks;
        this.mode = mode;
    }

    public static void encode(S2C_DarkSightStart msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.durationTicks);
        buf.writeByte(msg.mode);
    }

    public static S2C_DarkSightStart decode(FriendlyByteBuf buf) {
        return new S2C_DarkSightStart(buf.readVarInt(), buf.readByte());
    }

    public static void handle(S2C_DarkSightStart msg, Supplier<NetworkEvent.Context> ctxSup) {
        ctxSup.get().enqueueWork(() -> DarkSightClientState.start(msg.durationTicks, msg.mode));
        ctxSup.get().setPacketHandled(true);
    }
}

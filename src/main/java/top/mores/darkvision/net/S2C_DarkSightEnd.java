package top.mores.darkvision.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.mores.darkvision.client.DarkSightClientState;

import java.util.function.Supplier;

public class S2C_DarkSightEnd {
    public final byte reason; // 0=自然 1=耗尽 2=松开 3=强制

    public S2C_DarkSightEnd(byte reason) {
        this.reason = reason;
    }

    public static void encode(S2C_DarkSightEnd msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.reason);
    }

    public static S2C_DarkSightEnd decode(FriendlyByteBuf buf) {
        return new S2C_DarkSightEnd(buf.readByte());
    }

    public static void handle(S2C_DarkSightEnd msg, Supplier<NetworkEvent.Context> ctxSup) {
        ctxSup.get().enqueueWork(DarkSightClientState::end);
        ctxSup.get().setPacketHandled(true);
    }
}

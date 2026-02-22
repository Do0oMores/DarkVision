package top.mores.darkvision.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import top.mores.darkvision.server.DarkSightServer;

import java.util.function.Supplier;

public class C2S_ToggleDarkSight {
    public enum Action {
        PRESS((byte)1),
        RELEASE((byte)2);

        public final byte id;
        Action(byte id){ this.id = id; }

        public static Action from(byte b){
            return b == 1 ? PRESS : RELEASE;
        }
    }

    private final byte action;

    public C2S_ToggleDarkSight(Action action) {
        this.action = action.id;
    }

    public static void encode(C2S_ToggleDarkSight msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.action);
    }

    public static C2S_ToggleDarkSight decode(FriendlyByteBuf buf) {
        return new C2S_ToggleDarkSight(Action.from(buf.readByte()));
    }

    public static void handle(C2S_ToggleDarkSight msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ServerPlayer sp = ctx.getSender();
        if (sp == null) return;

        ctx.enqueueWork(() -> {
            Action a = Action.from(msg.action);
            if (a == Action.PRESS) {
                DarkSightServer.onPress(sp);
            } else {
                DarkSightServer.onRelease(sp);
            }
        });
        ctx.setPacketHandled(true);
    }
}

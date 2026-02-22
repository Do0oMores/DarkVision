package top.mores.darkvision.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.mores.darkvision.client.DarkSightClientState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2C_DarkSightTargets {
    public record Target(byte type, int entityId, BlockPos pos, byte strength, short ttlTicks) {}

    public final int seq;
    public final List<Target> targets;

    public S2C_DarkSightTargets(int seq, List<Target> targets) {
        this.seq = seq;
        this.targets = targets;
    }

    public static void encode(S2C_DarkSightTargets msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.seq);
        buf.writeVarInt(msg.targets.size());
        for (Target t : msg.targets) {
            buf.writeByte(t.type());
            buf.writeVarInt(t.entityId());
            buf.writeBlockPos(t.pos());
            buf.writeByte(t.strength());
            buf.writeShort(t.ttlTicks());
        }
    }

    public static S2C_DarkSightTargets decode(FriendlyByteBuf buf) {
        int seq = buf.readVarInt();
        int n = buf.readVarInt();
        List<Target> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            byte type = buf.readByte();
            int entityId = buf.readVarInt();
            BlockPos pos = buf.readBlockPos();
            byte strength = buf.readByte();
            short ttl = buf.readShort();
            list.add(new Target(type, entityId, pos, strength, ttl));
        }
        return new S2C_DarkSightTargets(seq, list);
    }

    public static void handle(S2C_DarkSightTargets msg, Supplier<NetworkEvent.Context> ctxSup) {
        ctxSup.get().enqueueWork(() -> DarkSightClientState.updateTargets(msg.seq, msg.targets));
        ctxSup.get().setPacketHandled(true);
    }
}

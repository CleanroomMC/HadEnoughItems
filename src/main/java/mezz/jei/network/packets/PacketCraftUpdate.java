package mezz.jei.network.packets;

import mezz.jei.Internal;
import mezz.jei.network.IPacketId;
import mezz.jei.network.PacketIdClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;

public class PacketCraftUpdate extends PacketJei {
    private final boolean success;
    private final int itemsCrafted;

    public PacketCraftUpdate(boolean success, int itemsCrafted) {
        this.success = success;
        this.itemsCrafted = itemsCrafted;
    }

    @Override
    public IPacketId getPacketId() {
        return PacketIdClient.CRAFT_UPDATE;
    }

    @Override
    public void writePacketData(PacketBuffer buf) {
        buf.writeBoolean(success);
        if (success) {
            buf.writeVarInt(itemsCrafted);
        }
    }

    public static void readPacketData(PacketBuffer packetBuffer, EntityPlayer entityPlayer) {
        boolean success = packetBuffer.readBoolean();
        int itemsCrafted = success ? packetBuffer.readVarInt() : 0;
        Internal.getRuntime().getAutocraftingHandler().stepFinished(success, itemsCrafted);
    }
}

package mezz.jei.network.packets;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;

import mezz.jei.network.IPacketId;
import mezz.jei.network.PacketIdServer;
import mezz.jei.transfer.BasicRecipeTransferHandlerServer;

public class PacketRecipeTransfer extends PacketJei {
	public final Int2IntMap recipeMap;
	public final IntList craftingSlots;
	public final IntList inventorySlots;
	private final boolean maxTransfer;
	private final boolean requireCompleteSets;

	public PacketRecipeTransfer(Int2IntMap recipeMap, IntList craftingSlots, IntList inventorySlots, boolean maxTransfer, boolean requireCompleteSets) {
		this.recipeMap = recipeMap;
		this.craftingSlots = craftingSlots;
		this.inventorySlots = inventorySlots;
		this.maxTransfer = maxTransfer;
		this.requireCompleteSets = requireCompleteSets;
	}

	@Override
	public IPacketId getPacketId() {
		return PacketIdServer.RECIPE_TRANSFER;
	}

	@Override
	public void writePacketData(PacketBuffer buf) {
		buf.writeVarInt(recipeMap.size());
		for (Int2IntMap.Entry recipeMapEntry : recipeMap.int2IntEntrySet()) {
			buf.writeVarInt(recipeMapEntry.getIntKey());
			buf.writeVarInt(recipeMapEntry.getIntValue());
		}

		buf.writeVarInt(craftingSlots.size());
		for (int craftingSlot : craftingSlots) {
			buf.writeVarInt(craftingSlot);
		}

		buf.writeVarInt(inventorySlots.size());
		for (int inventorySlot : inventorySlots) {
			buf.writeVarInt(inventorySlot);
		}

		buf.writeBoolean(maxTransfer);
		buf.writeBoolean(requireCompleteSets);
	}

	public static void readPacketData(PacketBuffer buf, EntityPlayer player) {
		int recipeMapSize = buf.readVarInt();
		Int2IntMap recipeMap = new Int2IntOpenHashMap(recipeMapSize);
		for (int i = 0; i < recipeMapSize; i++) {
			int slotIndex = buf.readVarInt();
			int recipeItem = buf.readVarInt();
			recipeMap.put(slotIndex, recipeItem);
		}

		int craftingSlotsSize = buf.readVarInt();
		IntList craftingSlots = new IntArrayList(craftingSlotsSize);
		for (int i = 0; i < craftingSlotsSize; i++) {
			int slotIndex = buf.readVarInt();
			craftingSlots.add(slotIndex);
		}

		int inventorySlotsSize = buf.readVarInt();
		IntList inventorySlots = new IntArrayList(inventorySlotsSize);
		for (int i = 0; i < inventorySlotsSize; i++) {
			int slotIndex = buf.readVarInt();
			inventorySlots.add(slotIndex);
		}
		boolean maxTransfer = buf.readBoolean();
		boolean requireCompleteSets = buf.readBoolean();

		BasicRecipeTransferHandlerServer.setItems(player, recipeMap, craftingSlots, inventorySlots, maxTransfer, requireCompleteSets);
	}

}

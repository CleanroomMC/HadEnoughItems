package mezz.jei.network.packets;

import java.util.*;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;

import mezz.jei.network.IPacketId;
import mezz.jei.network.PacketIdServer;
import mezz.jei.transfer.BasicRecipeTransferHandlerServer;

public class PacketRecipeTransfer extends PacketJei {
	public final Map<Integer, Integer> recipeMap;
	public final List<Integer> craftingSlots;
	public final List<Integer> inventorySlots;
	public final Map<Integer, Integer> itemCounts;
	private final boolean maxTransfer;
	private final boolean requireCompleteSets;

	public PacketRecipeTransfer(Map<Integer, Integer> recipeMap, List<Integer> craftingSlots, List<Integer> inventorySlots, boolean maxTransfer, boolean requireCompleteSets) {
		this(recipeMap, craftingSlots, inventorySlots, maxTransfer, requireCompleteSets, Collections.emptyMap());
	}

	public PacketRecipeTransfer(Map<Integer, Integer> recipeMap, List<Integer> craftingSlots, List<Integer> inventorySlots, boolean maxTransfer, boolean requireCompleteSets,
								Map<Integer, Integer> itemCounts) {
		this.recipeMap = recipeMap;
		this.craftingSlots = craftingSlots;
		this.inventorySlots = inventorySlots;
		this.maxTransfer = maxTransfer;
		this.requireCompleteSets = requireCompleteSets;
		this.itemCounts = itemCounts;
	}

	@Override
	public IPacketId getPacketId() {
		return PacketIdServer.RECIPE_TRANSFER;
	}

	@Override
	public void writePacketData(PacketBuffer buf) {
		buf.writeVarInt(recipeMap.size());
		for (Map.Entry<Integer, Integer> recipeMapEntry : recipeMap.entrySet()) {
			buf.writeVarInt(recipeMapEntry.getKey());
			buf.writeVarInt(recipeMapEntry.getValue());
		}

		buf.writeVarInt(craftingSlots.size());
		for (Integer craftingSlot : craftingSlots) {
			buf.writeVarInt(craftingSlot);
		}

		buf.writeVarInt(inventorySlots.size());
		for (Integer inventorySlot : inventorySlots) {
			buf.writeVarInt(inventorySlot);
		}

		buf.writeBoolean(maxTransfer);
		buf.writeBoolean(requireCompleteSets);

		if (!itemCounts.isEmpty()) {
			buf.writeBoolean(true);
			buf.writeVarInt(itemCounts.size());
			for (Map.Entry<Integer, Integer> itemCount : itemCounts.entrySet()) {
				buf.writeVarInt(itemCount.getKey());
				buf.writeVarInt(itemCount.getValue());
			}
		} else {
			buf.writeBoolean(false);
		}
	}

	public static void readPacketData(PacketBuffer buf, EntityPlayer player) {
		int recipeMapSize = buf.readVarInt();
		Map<Integer, Integer> recipeMap = new HashMap<>(recipeMapSize);
		for (int i = 0; i < recipeMapSize; i++) {
			int slotIndex = buf.readVarInt();
			int recipeItem = buf.readVarInt();
			recipeMap.put(slotIndex, recipeItem);
		}

		int craftingSlotsSize = buf.readVarInt();
		List<Integer> craftingSlots = new ArrayList<>(craftingSlotsSize);
		for (int i = 0; i < craftingSlotsSize; i++) {
			int slotIndex = buf.readVarInt();
			craftingSlots.add(slotIndex);
		}

		int inventorySlotsSize = buf.readVarInt();
		List<Integer> inventorySlots = new ArrayList<>(inventorySlotsSize);
		for (int i = 0; i < inventorySlotsSize; i++) {
			int slotIndex = buf.readVarInt();
			inventorySlots.add(slotIndex);
		}
		boolean maxTransfer = buf.readBoolean();
		boolean requireCompleteSets = buf.readBoolean();

		Map<Integer, Integer> itemCounts = null;
		if (buf.readBoolean()) {
			int itemCountsSize = buf.readVarInt();
			itemCounts = new HashMap<>(itemCountsSize);
			for (int i = 0; i < itemCountsSize; i++) {
				int slotIndex = buf.readVarInt();
				int itemCount = buf.readVarInt();
				itemCounts.put(slotIndex, itemCount);
			}
		}

		BasicRecipeTransferHandlerServer.setItems(player, recipeMap, craftingSlots, inventorySlots, maxTransfer, requireCompleteSets, itemCounts);
	}

}

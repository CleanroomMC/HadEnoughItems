package mezz.jei.network.packets;

import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.network.IPacketId;
import mezz.jei.network.PacketIdServer;
import mezz.jei.transfer.BasicRecipeTransferHandlerServer;
import mezz.jei.util.Log;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.network.PacketBuffer;

import javax.annotation.Nullable;
import java.util.*;

public class PacketRecipeTransfer extends PacketJei {
	public final Map<Integer, Integer> recipeMap;
	public final List<Integer> craftingSlots;
	public final List<Integer> inventorySlots;
	public int outputSlot = -1;
	public final Map<Integer, Integer> itemCounts;
	private final int maxTransfer;
	private final boolean performRecipe;
	private final boolean requireCompleteSets;

	public PacketRecipeTransfer(Map<Integer, Integer> recipeMap, List<Integer> craftingSlots, List<Integer> inventorySlots, int maxTransfer, boolean performRecipe, boolean requireCompleteSets,
								Map<Integer, Integer> itemCounts) {
		this.recipeMap = recipeMap;
		this.craftingSlots = craftingSlots;
		this.inventorySlots = inventorySlots;
		this.maxTransfer = maxTransfer;
		this.performRecipe = performRecipe;
		this.requireCompleteSets = requireCompleteSets;
		this.itemCounts = itemCounts;
	}

	public PacketRecipeTransfer(Map<Integer, Integer> recipeMap, List<Integer> craftingSlots, List<Integer> inventorySlots, int maxTransfer, boolean performRecipe, boolean requireCompleteSets) {
		this(recipeMap, craftingSlots, inventorySlots, maxTransfer, requireCompleteSets, performRecipe, Collections.emptyMap());
	}

	/**
	 * @deprecated kept for backwards compatibility, behaviour corresponds to
	 * {@link mezz.jei.transfer.BasicRecipeTransferHandler#transferRecipe(Container, IRecipeLayout, EntityPlayer, boolean, boolean)}
	 */
	@Deprecated
	public PacketRecipeTransfer(Map<Integer, Integer> recipeMap, List<Integer> craftingSlots, List<Integer> inventorySlots, boolean maxTransfer, boolean requireCompleteSets) {
		this(recipeMap, craftingSlots, inventorySlots, maxTransfer ? Integer.MAX_VALUE : 1, false, requireCompleteSets);
	}

	/**
	 * @deprecated kept for backwards compatibility, behaviour corresponds to
	 * {@link mezz.jei.transfer.BasicRecipeTransferHandler#transferRecipe(Container, IRecipeLayout, EntityPlayer, boolean, boolean)}
	 */
	@Deprecated
	public PacketRecipeTransfer(Map<Integer, Integer> recipeMap, List<Integer> craftingSlots, List<Integer> inventorySlots, boolean maxTransfer, boolean requireCompleteSets,
								Map<Integer, Integer> itemCounts) {
		this(recipeMap, craftingSlots, inventorySlots, maxTransfer ? Integer.MAX_VALUE : 1, false, requireCompleteSets, itemCounts);
	}

	public PacketRecipeTransfer setOutputSlot(int outputSlot) {
		this.outputSlot = outputSlot;
		return this;
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

		buf.writeVarInt(maxTransfer);
		buf.writeBoolean(requireCompleteSets);
		buf.writeBoolean(performRecipe);
		buf.writeVarInt(outputSlot);

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
		Container container = player.openContainer;

		int recipeMapSize = buf.readVarInt();
		if (!isValidCollectionSize(container, recipeMapSize, "recipe map")) {
			return;
		}
		Map<Integer, Integer> recipeMap = new HashMap<>();
		for (int i = 0; i < recipeMapSize; i++) {
			int slotIndex = buf.readVarInt();
			int recipeItem = buf.readVarInt();
			if (!isValidSlotIndex(container, recipeItem, "recipe item")) {
				return;
			}
			recipeMap.put(slotIndex, recipeItem);
		}

		List<Integer> craftingSlots = readSlotIndexes(buf, container);
		if (craftingSlots == null) {
			return;
		}
		for (Integer craftingSlotNumber : recipeMap.keySet()) {
			if (craftingSlotNumber < 0 || craftingSlotNumber >= craftingSlots.size()) {
				Log.get().error("Recipe transfer packet has invalid crafting slot number {} for {} crafting slots", craftingSlotNumber, craftingSlots.size());
				return;
			}
		}

		List<Integer> inventorySlots = readSlotIndexes(buf, container);
		if (inventorySlots == null) {
			return;
		}
		int maxTransfer = buf.readVarInt();
		boolean performRecipe = buf.readBoolean();
		boolean requireCompleteSets = buf.readBoolean();
		int outputSlot = buf.readVarInt();
		if (outputSlot != -1 && !isValidSlotIndex(container, outputSlot, "output slot")) {
			return;
		}

		Map<Integer, Integer> itemCounts = null;
		if (buf.readBoolean()) {
			int itemCountsSize = buf.readVarInt();
			if (!isValidCollectionSize(container, itemCountsSize, "item counts")) {
				return;
			}
			itemCounts = new HashMap<>();
			for (int i = 0; i < itemCountsSize; i++) {
				int slotIndex = buf.readVarInt();
				int itemCount = buf.readVarInt();
				if (itemCount < 1) {
					Log.get().error("Recipe transfer packet has invalid recipe item count {} for container {}", itemCount, container.getClass());
					return;
				}
				itemCounts.put(slotIndex, itemCount);
			}
		}

		BasicRecipeTransferHandlerServer.setItems(player, recipeMap, craftingSlots, inventorySlots, maxTransfer, requireCompleteSets, itemCounts);
		if (performRecipe && outputSlot != -1) {
			BasicRecipeTransferHandlerServer.performRecipe(player, outputSlot);
		}
	}

	@Nullable
	private static List<Integer> readSlotIndexes(PacketBuffer buf, Container container) {
		int slotCount = buf.readVarInt();
		if (!isValidCollectionSize(container, slotCount, "slot ids")) {
			return null;
		}
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < slotCount; i++) {
			int slotIndex = buf.readVarInt();
			if (!isValidSlotIndex(container, slotIndex, "slot")) {
				return null;
			}
			slots.add(slotIndex);
		}
		return slots;
	}

	private static boolean isValidCollectionSize(Container container, int count, String collectionName) {
		if (count < 0 || count > container.inventorySlots.size()) {
			Log.get().error("Recipe transfer packet has invalid {} count {} for container {} with {} slots", collectionName, count, container.getClass(), container.inventorySlots.size());
			return false;
		}
		return true;
	}

	private static boolean isValidSlotIndex(Container container, int slotIndex, String slotName) {
		if (slotIndex < 0 || slotIndex >= container.inventorySlots.size()) {
			Log.get().error("Recipe transfer packet has invalid {} id {} for container {}", slotName, slotIndex, container.getClass());
			return false;
		}
		return true;
	}

}

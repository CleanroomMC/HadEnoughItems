package mezz.jei.network.packets;

import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.network.IPacketId;
import mezz.jei.network.PacketIdServer;
import mezz.jei.transfer.BasicRecipeTransferHandlerServer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.network.PacketBuffer;

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
		int maxTransfer = buf.readVarInt();
		boolean performRecipe = buf.readBoolean();
		boolean requireCompleteSets = buf.readBoolean();
		int outputSlot = buf.readVarInt();

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
		if (performRecipe && outputSlot != -1) {
			BasicRecipeTransferHandlerServer.performRecipe(player, outputSlot);
		}
	}

}

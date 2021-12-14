package mezz.jei.ingredients;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import mezz.jei.search.PrefixInfo;
import mezz.jei.search.PrefixedSearchable;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.Minecraft;
import net.minecraft.util.NonNullList;

import mezz.jei.config.Config.SearchMode;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.search.GeneralizedSuffixTree;

public class IngredientFilterBackgroundBuilder {

	private final Map<PrefixInfo, PrefixedSearchable<GeneralizedSuffixTree>> prefixedSearchTrees;
	private final NonNullList<IIngredientListElement<?>> elementList;

	public IngredientFilterBackgroundBuilder(Map<PrefixInfo, PrefixedSearchable<GeneralizedSuffixTree>> prefixedSearchTrees, NonNullList<IIngredientListElement<?>> elementList) {
		this.prefixedSearchTrees = prefixedSearchTrees;
		this.elementList = elementList;
	}

	public void start() {
		boolean finished = run(10000);
		if (!finished) {
			MinecraftForge.EVENT_BUS.register(this);
		}
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.side == Side.CLIENT && Minecraft.getMinecraft().player != null) {
			boolean finished = run(20);
			if (!finished) {
				return;
			}
		}
		MinecraftForge.EVENT_BUS.unregister(this);
	}

	private boolean run(final int timeoutMs) {
		final long startTime = System.currentTimeMillis();
		List<PrefixedSearchable<GeneralizedSuffixTree>> activeTrees = new ArrayList<>();
		int startIndex = Integer.MAX_VALUE;
		for (PrefixedSearchable<GeneralizedSuffixTree> prefixedTree : this.prefixedSearchTrees.values()) {
			SearchMode mode = prefixedTree.getMode();
			if (mode != SearchMode.DISABLED) {
				GeneralizedSuffixTree searchable = prefixedTree.getSearchable();
				int nextFreeIndex = searchable.getHighestIndex() + 1;
				startIndex = Math.min(nextFreeIndex, startIndex);
				if (nextFreeIndex < elementList.size()) {
					activeTrees.add(prefixedTree);
				}
			}
		}

		if (activeTrees.isEmpty()) {
			return true;
		}

		for (int i = startIndex; i < elementList.size(); i++) {
			IIngredientListElement<?> element = elementList.get(i);
			for (PrefixedSearchable<GeneralizedSuffixTree> prefixedTree : activeTrees) {
				GeneralizedSuffixTree searchable = prefixedTree.getSearchable();
				int nextFreeIndex = searchable.getHighestIndex() + 1;
				if (nextFreeIndex >= i) {
					Collection<String> strings = prefixedTree.getStrings(element);
					if (strings.isEmpty()) {
						searchable.put("", i);
					} else {
						for (String string : strings) {
							searchable.put(string, i);
						}
					}
				}
			}
			if (System.currentTimeMillis() - startTime >= timeoutMs) {
				return false;
			}
		}
		return true;
	}
}

package mezz.jei.plugins.vanilla.furnace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.Hash.Strategy;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

import mezz.jei.api.IJeiHelpers;
import mezz.jei.api.recipe.IStackHelper;

public final class SmeltingRecipeMaker {

	private SmeltingRecipeMaker() {
	}

	public static List<SmeltingRecipe> getFurnaceRecipes(IJeiHelpers helpers) {
		IStackHelper stackHelper = helpers.getStackHelper();
		FurnaceRecipes furnaceRecipes = FurnaceRecipes.instance();
		Map<ItemStack, ItemStack> smeltingMap = furnaceRecipes.getSmeltingList();
		Map<ItemStack, List<ItemStack>> outputMap = new Object2ObjectOpenCustomHashMap<>(smeltingMap.size(), new Strategy<ItemStack>() {
			@Override
			public int hashCode(ItemStack o) {
				return (o.getItem().hashCode() + 31) * o.getCount();
			}
			@Override
			public boolean equals(ItemStack a, ItemStack b) {
				if (a == null || b == null) {
					return false;
				}
				return a.getItem() == b.getItem() && a.getMetadata() == b.getMetadata() && a.getCount() == b.getCount();
			}
		});
		List<SmeltingRecipe> recipes = new ArrayList<>(smeltingMap.size());

		for (Map.Entry<ItemStack, ItemStack> entry : smeltingMap.entrySet()) {
			ItemStack input = entry.getKey();
			ItemStack output = entry.getValue();
			outputMap.computeIfAbsent(output, k -> new ArrayList<>()).addAll(stackHelper.getSubtypes(input));
		}

		for (Entry<ItemStack, List<ItemStack>> entry : outputMap.entrySet()) {
			recipes.add(new SmeltingRecipe(entry.getValue(), entry.getKey()));
		}

		return recipes;
	}

}

package mezz.jei.plugins.vanilla.furnace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.IJeiHelpers;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.recipe.IStackHelper;

public final class FuelRecipeMaker {

	private FuelRecipeMaker() {
	}

	public static List<FuelRecipe> getFuelRecipes(IIngredientRegistry ingredientRegistry, IJeiHelpers helpers) {
		IStackHelper stackHelper = helpers.getStackHelper();
		List<ItemStack> fuelStacks = ingredientRegistry.getFuels();
		Int2ObjectMap<List<ItemStack>> fuels = new Int2ObjectOpenHashMap<>(fuelStacks.size() / 8);
		List<FuelRecipe> recipes = new ArrayList<>(fuelStacks.size() / 8);
		for (ItemStack fuelStack : fuelStacks) {
			for (ItemStack subtype : stackHelper.getSubtypes(fuelStack)) {
				int burnTime = TileEntityFurnace.getItemBurnTime(subtype);
				fuels.computeIfAbsent(burnTime, k -> new ArrayList<>(8)).add(subtype);
			}
		}
		IGuiHelper guiHelper = helpers.getGuiHelper();
		fuels.int2ObjectEntrySet().stream().sorted(Comparator.comparingInt(Entry::getIntKey))
				.forEach(entry -> recipes.add(new FuelRecipe(guiHelper, entry.getValue(), entry.getIntKey())));

		return recipes;
	}

}

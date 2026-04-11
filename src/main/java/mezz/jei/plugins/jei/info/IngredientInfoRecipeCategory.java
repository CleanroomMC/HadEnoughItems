package mezz.jei.plugins.jei.info;

import javax.annotation.Nullable;

import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.config.Constants;
import mezz.jei.gui.GuiHelper;
import mezz.jei.plugins.jei.JEIInternalPlugin;
import mezz.jei.util.Translator;

public class IngredientInfoRecipeCategory implements IRecipeCategory<IngredientInfoRecipe> {
	public static final int recipeWidth = 160;
	public static final int recipeHeight = 125;
	private final IDrawable background;
	private final IDrawable icon;
	private final IDrawable slotBackground;
	private final String localizedName;

	public IngredientInfoRecipeCategory(GuiHelper guiHelper) {
		background = guiHelper.createBlankDrawable(recipeWidth, recipeHeight);
		icon = guiHelper.getInfoIcon();
		slotBackground = guiHelper.getSlotDrawable();
		localizedName = Translator.translateToLocal("gui.jei.category.itemInformation");
	}

	@Override
	public String getUid() {
		return VanillaRecipeCategoryUid.INFORMATION;
	}

	@Override
	public String getTitle() {
		return localizedName;
	}

	@Override
	public String getModName() {
		return Constants.NAME;
	}

	@Nullable
	@Override
	public IDrawable getIcon() {
		return icon;
	}

	@Override
	public IDrawable getBackground() {
		return background;
	}

	@Override
	public void setRecipe(IRecipeLayout recipeLayout, IngredientInfoRecipe recipeWrapper, IIngredients ingredients) {
		int xPos = (recipeWidth - 18) / 2;
		for (IIngredientType<?> ingredientType : JEIInternalPlugin.ingredientRegistry.getRegisteredIngredientTypes()) {
			if (ingredients.getInputs(ingredientType).isEmpty() || ingredients.getOutputs(ingredientType).isEmpty()) continue;
			IGuiIngredientGroup<?> group = recipeLayout.getIngredientsGroup(ingredientType);
			group.init(0, true, xPos + 1, 1);
			// only render the background for items
			if (ingredientType == VanillaTypes.ITEM) group.setBackground(0, slotBackground);
			group.set(ingredients);
		}
	}
}

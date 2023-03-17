package mezz.jei.plugins.vanilla.furnace;

import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.util.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

import java.awt.*;
import java.util.Collections;
import java.util.List;

public class SmeltingRecipe implements IRecipeWrapper {

    private final List<ItemStack> input;
    private final ItemStack output;

    private float experience = -1;

    public SmeltingRecipe(List<ItemStack> input, ItemStack output) {
        this.input = input;
        this.output = output;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInputLists(VanillaTypes.ITEM, Collections.singletonList(this.input));
        ingredients.setOutput(VanillaTypes.ITEM, output);
    }

    @Override
    public void drawInfo(Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        FurnaceRecipes furnaceRecipes = FurnaceRecipes.instance();
        if (this.experience == -1) {
            this.experience = furnaceRecipes.getSmeltingExperience(output);
        }
        if (this.experience > 0) {
            String experienceString = Translator.translateToLocalFormatted("gui.jei.category.smelting.experience", experience);
            FontRenderer fontRenderer = minecraft.fontRenderer;
            int stringWidth = fontRenderer.getStringWidth(experienceString);
            fontRenderer.drawString(experienceString, recipeWidth - stringWidth, 0, Color.gray.getRGB());
        }
    }
}

package mezz.jei.gui.recipes;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import mezz.jei.Internal;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.autocrafting.favorites.FavoriteRecipes;
import mezz.jei.gui.elements.GuiIconButton;
import mezz.jei.util.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecipeFavoriteButton extends GuiIconButton {
	private final IRecipeWrapper recipe;
	private final IRecipeCategory<?> category;
	private List<IGuiIngredient<?>> supportedIngredients;
	private final Set<Integer> favoriteSlots = new IntOpenHashSet();
	private int selectedSlot = 0;
	private RecipeLayout layout;

	private static final Color selectedColor = new Color(0.0f, 0.0f, 1.0f, 0.3f);
	private static final Color favoritedColor = new Color(0.0f, 1.0f, 0.0f, 0.3f);


	public RecipeFavoriteButton(int index, int width, int height, IDrawable offIcon, IDrawable onIcon, IRecipeWrapper recipe, IRecipeCategory<?> category, RecipeLayout layout) {
		super(index, null, null); // We're going to replace these, but it doesn't let me pass in lambdas referring to the object yet.
		this.tooltipCallback = this::getTooltips;
		this.iconSupplier = () -> isIconToggledOn() ? onIcon : offIcon;
		this.mouseClickCallback = this::onMouseClicked;
		this.recipe = recipe;
		this.category = category;
		this.width = width;
		this.height = height;
		this.layout = layout;
		setSupportedIngredients(layout);
	}

	private void setSupportedIngredients(RecipeLayout layout) {
		Function<Map, Stream<IGuiIngredient<?>>> filter = (map) -> map.values().stream()
				.filter(ing -> ing != null && ((IGuiIngredient<?>) ing).getDisplayedIngredient() != null && !((IGuiIngredient<?>) ing).isInput());
		supportedIngredients = Internal.getIngredientRegistry().getCraftableIngredientTypes().stream()
				.map(t -> layout.getIngredientsGroup(t).getGuiIngredients())
				.flatMap(filter)
				.collect(Collectors.toList());
		supportedIngredients.forEach(ing -> {
			if (FavoriteRecipes.isFavoriteFor(recipe, ing.getDisplayedIngredient())) {
				favoriteSlots.add(supportedIngredients.indexOf(ing));
			}
		});
		this.enabled = this.visible = !supportedIngredients.isEmpty();
	}

	public void init(RecipeLayout layout) {
		this.layout = layout;
		setSupportedIngredients(layout);
	}

	protected void getTooltips(List<String> tooltip) {
		if (isIconToggledOn()) {
			tooltip.add(Translator.translateToLocal("hei.tooltip.unfavorite"));
		} else {
			tooltip.add(Translator.translateToLocal("hei.tooltip.favorite"));
		}
		tooltip.add(Translator.translateToLocal("hei.tooltip.favorite_scroll"));
	}

	protected boolean isIconToggledOn() {
		return FavoriteRecipes.isFavorite(recipe);
	}

	protected boolean onMouseClicked(Minecraft mc, int mouseX, int mouseY) {
		if (GuiScreen.isShiftKeyDown() && isIconToggledOn()) {
			FavoriteRecipes.removeFavorite(recipe);
			favoriteSlots.clear();
			return true;
		}
		FavoriteRecipes.toggleFavorite(supportedIngredients.get(selectedSlot).getDisplayedIngredient(), recipe, category);
		if (favoriteSlots.contains(selectedSlot)) { // We also have to update it in this GUI.
			favoriteSlots.remove(selectedSlot);
		} else {
			favoriteSlots.add(selectedSlot);
		}
		return true;
	}

	@Override
	public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
		super.drawButton(mc, mouseX, mouseY, partialTicks);
		if (!isMouseOver() && (!visible || !layout.getRecipeBookmarkButton().isMouseOver())) {
			return;
		}
		supportedIngredients.get(selectedSlot).drawHighlight(mc, selectedColor, this.layout.getPosX(), this.layout.getPosY());
		if (isIconToggledOn()) {
			for (int slot : favoriteSlots) {
				supportedIngredients.get(slot).drawHighlight(mc, favoritedColor, this.layout.getPosX(), this.layout.getPosY()); // Should blend nicely
			}
		}
	}

	public boolean handleMouseScrolled(int mouseX, int mouseY, int scrollDelta) {
		if (!this.enabled || !this.visible || !isMouseOver()) {
			return false;
		}
		// Wrapping scroll
		if (scrollDelta < 0) {
			selectedSlot = (selectedSlot + supportedIngredients.size() - 1) % supportedIngredients.size();
		} else if (scrollDelta > 0) {
			selectedSlot = (selectedSlot + 1) % supportedIngredients.size();
		}

		return true;
	}

	@Nullable
	public Object getDisplayedIngredient() {
		return supportedIngredients.get(selectedSlot).getDisplayedIngredient();
	}
}

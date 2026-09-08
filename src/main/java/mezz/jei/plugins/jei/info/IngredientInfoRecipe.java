package mezz.jei.plugins.jei.info;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mezz.jei.Internal;
import mezz.jei.gui.GuiHelper;
import net.minecraft.client.Minecraft;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.gui.elements.ScrollBar;
import mezz.jei.util.Translator;
import net.minecraft.client.renderer.GlStateManager;

public class IngredientInfoRecipe<T> implements IRecipeWrapper {
	private static final int lineSpacing = 2;
	private final List<String> description;
	private final List<T> ingredients;
	private final IIngredientType<T> ingredientType;
	private final IDrawable slotDrawable;
	private final ScrollBar scrollBar;
	private float scrollOffset = 0;

	public static <T> List<IngredientInfoRecipe<T>> create(IGuiHelper guiHelper, List<T> ingredients, IIngredientType<T> ingredientType, String... descriptionKeys) {
		List<IngredientInfoRecipe<T>> recipes = new ArrayList<>(1);

		List<String> descriptionLines = translateDescriptionLines(descriptionKeys);
		descriptionLines = expandNewlines(descriptionLines);
		descriptionLines = wrapDescriptionLines(descriptionLines);

		IngredientInfoRecipe<T> recipe = new IngredientInfoRecipe<>(guiHelper, ingredients, ingredientType, descriptionLines);
		recipes.add(recipe);

		return recipes;
	}

	private static List<String> translateDescriptionLines(String... descriptionKeys) {
		List<String> descriptionLines = new ArrayList<>();
		for (String descriptionKey : descriptionKeys) {
			String translatedLine = Translator.translateToLocal(descriptionKey);
			descriptionLines.add(translatedLine);
		}
		return descriptionLines;
	}

	private static List<String> expandNewlines(List<String> descriptionLines) {
		List<String> descriptionLinesExpanded = new ArrayList<>();
		for (String descriptionLine : descriptionLines) {
			String[] descriptionLineExpanded = descriptionLine.split("\\\\n");
			Collections.addAll(descriptionLinesExpanded, descriptionLineExpanded);
		}
		return descriptionLinesExpanded;
	}

	private static List<String> wrapDescriptionLines(List<String> descriptionLines) {
		Minecraft minecraft = Minecraft.getMinecraft();
		List<String> descriptionLinesWrapped = new ArrayList<>();
		for (String descriptionLine : descriptionLines) {
			List<String> textLines = minecraft.fontRenderer.listFormattedStringToWidth(descriptionLine, IngredientInfoRecipeCategory.recipeWidth - ScrollBar.WIDTH - 2);
			descriptionLinesWrapped.addAll(textLines);
		}
		return descriptionLinesWrapped;
	}

	private IngredientInfoRecipe(IGuiHelper guiHelper, List<T> ingredients, IIngredientType<T> ingredientType, List<String> description) {
		this.description = description;
		this.ingredients = ingredients;
		this.ingredientType = ingredientType;
		this.slotDrawable = guiHelper.getSlotDrawable();

		int contentY = slotDrawable.getHeight() + 4;
		int contentHeight = IngredientInfoRecipeCategory.recipeHeight - contentY;
		int scrollbarX = IngredientInfoRecipeCategory.recipeWidth - ScrollBar.WIDTH;

		GuiHelper exactHelper = Internal.getHelpers().getGuiHelper();
        this.scrollBar = new ScrollBar(
			scrollbarX,
			contentY,
			contentHeight,
            exactHelper.getScrollbarBackground(),
			exactHelper.getScrollbarMarker()
        );
	}

	@Override
	public void getIngredients(IIngredients ingredients) {
		ingredients.setInputLists(this.ingredientType, Collections.singletonList(this.ingredients));
		ingredients.setOutputs(this.ingredientType, this.ingredients);
	}

	@Override
	public void drawInfo(Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
		int lineHeight = minecraft.fontRenderer.FONT_HEIGHT + lineSpacing;

		int maxVisibleLines = (IngredientInfoRecipeCategory.recipeHeight - (slotDrawable.getHeight() + 4)) / lineHeight;
		int totalLines = description.size();
		int maxScrollLine = Math.max(0, totalLines - maxVisibleLines);

		if (maxScrollLine > 0) {
			scrollBar.draw(minecraft, maxVisibleLines, totalLines - maxVisibleLines, scrollOffset);
		}

		// Convert float scroll offset to integer line index
		int scrollLine = Math.round(scrollOffset * maxScrollLine);
		scrollLine = Math.max(0, Math.min(maxScrollLine, scrollLine));

		int endLine = Math.min(scrollLine + maxVisibleLines, totalLines);
		int yPos = slotDrawable.getHeight() + 4;

		for (int i = scrollLine; i < endLine; i++) {
			minecraft.fontRenderer.drawString(description.get(i), 0, yPos, Color.black.getRGB());
			yPos += lineHeight;
		}
	}

	@Override
	public boolean handleClick(Minecraft minecraft, int mouseX, int mouseY, int mouseButton) {
		if (mouseButton != 0) {
			return false;
		}

		int lineHeight = minecraft.fontRenderer.FONT_HEIGHT + lineSpacing;
		int maxVisibleLines = (IngredientInfoRecipeCategory.recipeHeight - (slotDrawable.getHeight() + 4)) / lineHeight;
		int totalLines = description.size();
		int hiddenAmount = Math.max(0, totalLines - maxVisibleLines);

		ScrollBar.ScrollResult result = scrollBar.startDrag(mouseX, mouseY, maxVisibleLines, hiddenAmount, scrollOffset);
		if (result.isHandled()) {
			scrollOffset = result.getScrollOffsetY();
			return true;
		}
		return false;
	}

	@Override
	public boolean handleMouseScroll(int mouseX, int mouseY, int scrollDelta) {
		int lineHeight = Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT + lineSpacing;
		int maxVisibleLines = (IngredientInfoRecipeCategory.recipeHeight - (slotDrawable.getHeight() + 4)) / lineHeight;
		int totalLines = description.size();
		int hiddenAmount = Math.max(0, totalLines - maxVisibleLines);

		ScrollBar.ScrollResult result = scrollBar.scroll(mouseX, mouseY, scrollDelta, maxVisibleLines, hiddenAmount, scrollOffset);
		if (result.isHandled()) {
			scrollOffset = result.getScrollOffsetY();
			return true;
		}
		return false;
	}

	@Override
	public boolean handleMouseDrag(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
		if (!scrollBar.isDragging()) {
			return false;
		}

		int lineHeight = Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT + lineSpacing;
		int maxVisibleLines = (IngredientInfoRecipeCategory.recipeHeight - (slotDrawable.getHeight() + 4)) / lineHeight;
		int totalLines = description.size();
		int hiddenAmount = Math.max(0, totalLines - maxVisibleLines);

		ScrollBar.ScrollResult result = scrollBar.dragTo(mouseY, maxVisibleLines, hiddenAmount, scrollOffset);
		if (result.isHandled()) {
			scrollOffset = result.getScrollOffsetY();
			return true;
		}
		return false;
	}

	@Override
	public boolean handleMouseReleased(int mouseX, int mouseY, int state) {
		scrollBar.stopDrag();
		return false;
	}

	public ScrollBar getScrollBar() {
		return scrollBar;
	}

	public List<String> getDescription() {
		return description;
	}
}

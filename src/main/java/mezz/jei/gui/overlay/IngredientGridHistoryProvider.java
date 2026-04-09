package mezz.jei.gui.overlay;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.IngredientListElement;
import mezz.jei.input.ClickedIngredient;
import mezz.jei.input.IClickedIngredient;
import mezz.jei.render.IngredientListBatchRenderer;
import mezz.jei.render.IngredientListSlot;
import mezz.jei.render.IngredientRenderer;
import mezz.jei.startup.ForgeModIdHelper;
import mezz.jei.util.LegacyUtil;
import mezz.jei.util.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static mezz.jei.gui.overlay.IngredientGrid.*;
import static mezz.jei.ingredients.IngredientListElementFactory.ORDER_TRACKER;
import static mezz.jei.plugins.jei.JEIInternalPlugin.ingredientRegistry;

/**
 * Extends the original layout logic from JEI to support history rows.
 * Original implementation by vfyjxf <a href="https://github.com/vfyjxf/JEI-Utilities">JEI-Utilities</a>.
 */
public class IngredientGridHistoryProvider {

    private static final List<IngredientGridHistoryProvider> GLOBAL_HISTORY_CONTAINER = new ArrayList<>();

    public static final int USE_ROWS = 2;
    public static final int MIN_ROWS = 6;
    public static final int BACKGROUND_COLOR = 0xee555555;
    public static final boolean HISTORY_MATCH_NBT = true;

    private final boolean enable;

    public boolean isEnable() {
        return enable;
    }

    private int historySize;
    private int columns;
    private final IngredientListBatchRenderer guiHistoryIngredientSlots;
    @SuppressWarnings("rawtypes")
    private final List<IIngredientListElement> historyIngredientElements = new ArrayList<>();

    private boolean showHistory;

    public IngredientGridHistoryProvider(boolean enable) {
        this.enable = enable;
        this.guiHistoryIngredientSlots = new IngredientListBatchRenderer();

        GLOBAL_HISTORY_CONTAINER.add(this);
    }

    /**
     * @see mezz.jei.gui.recipes.RecipesGui#show(IFocus)
     */
    public static <V> void onSetFocus(IFocus<V> focus) {
        for (IngredientGridHistoryProvider historyProvider : GLOBAL_HISTORY_CONTAINER) {
            if (historyProvider.isEnable()) {
                historyProvider.addHistoryIngredient(focus.getValue());
            }
        }
    }

    public void addHistoryIngredient(@Nullable Object value) {
        if (!enable) {
            return;
        }
        if (value == null) {
            return;
        }

        Object normalized = getNormalize(Objects.requireNonNull(ingredientRegistry), value);
        IIngredientListElement<?> ingredient = IngredientListElement.create(
                normalized,
                ingredientRegistry.getIngredientHelper(normalized),
                ingredientRegistry.getIngredientRenderer(normalized),
                ForgeModIdHelper.getInstance(),
                ORDER_TRACKER.getOrderIndex(normalized, ingredientRegistry.getIngredientHelper(normalized)));

        historyIngredientElements.removeIf(element -> areIngredientEqual(element.getIngredient(), normalized, HISTORY_MATCH_NBT));
        historyIngredientElements.add(0, ingredient);
        if (historyIngredientElements.size() > historySize) {
            historyIngredientElements.remove(historyIngredientElements.size() - 1);
        }

        while (historyIngredientElements.size() > USE_ROWS * Config.largestNumColumns) {
            historyIngredientElements.remove(historyIngredientElements.size() - 1);
        }

        guiHistoryIngredientSlots.set(0, historyIngredientElements);
    }

    public void removeElement(int index) {
        if (!enable) {
            return;
        }

        historyIngredientElements.remove(index);
        guiHistoryIngredientSlots.set(0, historyIngredientElements);
    }

    // internal methods

    void updateColumns(int columns) {
        if (!enable) {
            return;
        }

        this.columns = columns;
    }

    void updateHistorySize(int columns) {
        if (!enable) {
            return;
        }

        historySize = columns * USE_ROWS;
    }

    void clearHistorySlots() {
        if (!enable) {
            return;
        }

        guiHistoryIngredientSlots.clear();
    }

    boolean updateBoundsExtra(
            int columns,
            int rows,
            int y,
            int xOffset,
            Collection<Rectangle> exclusionAreas,
            IngredientListBatchRenderer guiIngredientSlots) {

        if (!enable) {
            return false;
        }

        this.columns = columns;
        this.historySize = columns * USE_ROWS;

        if (rows >= MIN_ROWS) {
            rows = rows - USE_ROWS;
            showHistory = true;
        } else {
            showHistory = false;
        }

        if (!showHistory) {
            return false;
        }

        for (int row = 0; row < rows; row++) {
            List<IngredientListSlot> ingredientRow = new ArrayList<>();
            int y1 = y + (row * INGREDIENT_HEIGHT);
            for (int column = 0; column < columns; column++) {
                int x1 = xOffset + (column * INGREDIENT_WIDTH);
                IngredientListSlot ingredientListSlot = new IngredientListSlot(x1, y1, INGREDIENT_PADDING);
                Rectangle stackArea = ingredientListSlot.getArea();
                final boolean blocked = MathUtil.intersects(exclusionAreas, stackArea);
                ingredientListSlot.setBlocked(blocked);
                ingredientRow.add(ingredientListSlot);
            }
            guiIngredientSlots.add(ingredientRow);
        }

        for (int row = 0; row < USE_ROWS; row++) {
            List<IngredientListSlot> ingredientRow = new ArrayList<>();
            int y1 = y + ((row + rows) * INGREDIENT_HEIGHT);
            for (int column = 0; column < columns; column++) {
                int x1 = xOffset + (column * INGREDIENT_WIDTH);
                IngredientListSlot ingredientListSlot = new IngredientListSlot(x1, y1, INGREDIENT_PADDING);
                Rectangle stackArea = ingredientListSlot.getArea();
                final boolean blocked = MathUtil.intersects(exclusionAreas, stackArea);
                ingredientListSlot.setBlocked(blocked);
                ingredientRow.add(ingredientListSlot);
            }
            guiHistoryIngredientSlots.add(ingredientRow);
        }

        guiHistoryIngredientSlots.set(0, historyIngredientElements);

        return true;
    }

    void drawExtra(Minecraft minecraft) {
        if (!enable) {
            return;
        }
        if (!showHistory) {
            return;
        }

        Rectangle firstRect = guiHistoryIngredientSlots.getAllGuiIngredientSlots().get(0).getArea();

        drawSpillingArea(
                firstRect.x, firstRect.y,
                firstRect.width * columns,
                firstRect.height * USE_ROWS,
                BACKGROUND_COLOR);

        guiHistoryIngredientSlots.render(minecraft);
    }

    @SuppressWarnings("rawtypes")
    void drawTooltipsExtra(Minecraft minecraft, int mouseX, int mouseY) {
        if (!enable) {
            return;
        }
        if (!showHistory) {
            return;
        }

        IngredientRenderer hoveredHistory = guiHistoryIngredientSlots.getHovered(mouseX, mouseY);
        if (hoveredHistory != null) {
            hoveredHistory.drawTooltip(minecraft, mouseX, mouseY);
        }
    }

    @Nullable
    IClickedIngredient<?> getIngredientUnderMouseExtra(
            @Nullable IClickedIngredient<?> result,
            int mouseX,
            int mouseY) {

        if (result != null) {
            return result;
        }
        if (!enable) {
            return null;
        }
        if (!showHistory) {
            return null;
        }

        ClickedIngredient<?> clickedHistory = guiHistoryIngredientSlots.getIngredientUnderMouse(mouseX, mouseY);
        if (clickedHistory != null) {
            clickedHistory.setAllowsCheating();
        }
        return clickedHistory;
    }

    // helper methods

    private static boolean areIngredientEqual(Object ingredient1, Object ingredient2, boolean matchesNbt) {
        if (ingredient1 == ingredient2) {
            return true;
        }

        if (ingredient1.getClass() == ingredient2.getClass()) {
            IIngredientHelper<Object> ingredientHelper = Objects.requireNonNull(ingredientRegistry).getIngredientHelper(ingredient1);
            if (matchesNbt) {
                return ingredientHelper.getUniqueId(ingredient1).equals(ingredientHelper.getUniqueId(ingredient2));
            }
            return ingredientHelper.getWildcardId(ingredient1).equals(ingredientHelper.getWildcardId(ingredient2));
        }

        return false;
    }

    private static <T> T getNormalize(IIngredientRegistry ingredientRegistry, T ingredient) {
        IIngredientHelper<T> ingredientHelper = ingredientRegistry.getIngredientHelper(ingredient);
        T copy = LegacyUtil.getIngredientCopy(ingredient, ingredientHelper);
        if (copy instanceof ItemStack) {
            ((ItemStack) copy).setCount(1);
        } else if (copy instanceof FluidStack) {
            ((FluidStack) copy).amount = 1000;
        }
        return copy;
    }

    private static void drawSpillingArea(int x, int y, int width, int height, int color) {
        float alpha = (float) (color >> 24 & 255) / 255f;
        float red = (float) (color >> 16 & 255) / 255f;
        float green = (float) (color >> 8 & 255) / 255f;
        float blue = (float) (color & 255) / 255f;

        GlStateManager.pushMatrix();

        GlStateManager.disableTexture2D();
        GL11.glEnable(GL11.GL_LINE_STIPPLE);
        GlStateManager.color(red, green, blue, alpha);
        GL11.glLineWidth(2F);
        GL11.glLineStipple(2, (short) 0x00FF);

        GL11.glBegin(GL11.GL_LINE_LOOP);

        GL11.glVertex2i(x, y);
        GL11.glVertex2i(x + width, y);
        GL11.glVertex2i(x + width, y + height);
        GL11.glVertex2i(x, y + height);

        GL11.glEnd();

        GL11.glLineStipple(2, (short) 0xFFFF);
        GL11.glLineWidth(2F);
        GL11.glDisable(GL11.GL_LINE_STIPPLE);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1F, 1F, 1F, 1F);

        GlStateManager.popMatrix();
    }
}

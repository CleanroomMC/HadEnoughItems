package mezz.jei.gui.overlay;

import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.autocrafting.IngredientUtil;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.IngredientListElementFactory;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.ingredients.group.CollapsedGroupIngredient;
import mezz.jei.input.ClickedIngredient;
import mezz.jei.render.IngredientListBatchRenderer;
import mezz.jei.render.IngredientListSlot;
import mezz.jei.render.IngredientRenderer;
import mezz.jei.startup.ForgeModIdHelper;
import mezz.jei.util.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static mezz.jei.gui.overlay.IngredientGrid.INGREDIENT_HEIGHT;
import static mezz.jei.gui.overlay.IngredientGrid.INGREDIENT_PADDING;
import static mezz.jei.gui.overlay.IngredientGrid.INGREDIENT_WIDTH;

/**
 * The ingredients that were last looked up, shown in the bottom rows of an ingredient grid.
 * Based on <a href="https://github.com/vfyjxf/JEI-Utilities">JEI-Utilities</a> by vfyjxf.
 */
public class IngredientGridHistoryProvider {
	private static final int MIN_INGREDIENT_ROWS = 4;
	private static final int OUTLINE_COLOR = 0xEE555555;

	private static int outlineList = -1;

	private final IngredientRegistry ingredientRegistry;
	private final IngredientListBatchRenderer slots = new IngredientListBatchRenderer(false);
	@SuppressWarnings("rawtypes")
	private final List<IIngredientListElement> elements = new ArrayList<>();
	private Rectangle area = new Rectangle();
	private boolean outlineDirty = true;

	public IngredientGridHistoryProvider(IngredientRegistry ingredientRegistry) {
		this.ingredientRegistry = ingredientRegistry;
	}

	@SuppressWarnings("unchecked")
	public <V> void add(V ingredient) {
		final int rows = Config.getHistoryRows();
		if (rows == 0 || ingredient instanceof CollapsedGroupIngredient
			|| Internal.getHelpers().getIngredientBlacklist().isIngredientBlacklisted(ingredient)) {
			return;
		}

		V normalized = IngredientUtil.normalizeCopy(ingredient);
		IIngredientListElement<V> element = IngredientListElementFactory.createUnorderedElement(
			ingredientRegistry, ingredientRegistry.getIngredientType(normalized), normalized, ForgeModIdHelper.getInstance());
		if (element == null) {
			return;
		}

		String uid = getUid(element);
		elements.removeIf(other -> other.getIngredient().getClass() == normalized.getClass() && uid.equals(getUid(other)));
		elements.add(0, element);

		final int maxSize = rows * Config.getMaxColumns();
		if (elements.size() > maxSize) {
			elements.subList(maxSize, elements.size()).clear();
		}
		slots.set(0, elements);
	}

	private static <V> String getUid(IIngredientListElement<V> element) {
		IIngredientHelper<V> helper = element.getIngredientHelper();
		V ingredient = element.getIngredient();
		return Config.isHistoryMatchingNbt() ? helper.getUniqueId(ingredient) : helper.getWildcardId(ingredient);
	}

	/**
	 * Lays the history out over the bottom rows of a grid.
	 *
	 * @return the number of rows taken from the grid
	 */
	int updateBounds(int columns, int rows, int x, int y, Collection<Rectangle> exclusionAreas) {
		area = new Rectangle();
		outlineDirty = true;
		slots.clear();

		final int historyRows = Config.getHistoryRows();
		if (historyRows == 0 || rows - historyRows < MIN_INGREDIENT_ROWS) {
			return 0;
		}

		final int top = y + (rows - historyRows) * INGREDIENT_HEIGHT;
		area = new Rectangle(x, top, columns * INGREDIENT_WIDTH, historyRows * INGREDIENT_HEIGHT);
		for (int row = 0; row < historyRows; row++) {
			List<IngredientListSlot> slotRow = new ArrayList<>();
			for (int column = 0; column < columns; column++) {
				IngredientListSlot slot = new IngredientListSlot(x + column * INGREDIENT_WIDTH, top + row * INGREDIENT_HEIGHT, INGREDIENT_PADDING);
				slot.setBlocked(MathUtil.intersects(exclusionAreas, slot.getArea()));
				slotRow.add(slot);
			}
			slots.add(slotRow);
		}
		slots.set(0, elements);
		return historyRows;
	}

	public boolean isEmpty() {
		return elements.isEmpty() || area.isEmpty();
	}

	void draw(Minecraft minecraft) {
		if (isEmpty()) {
			return;
		}

		GlStateManager.disableTexture2D();
		GL11.glEnable(GL11.GL_LINE_STIPPLE);
		GlStateManager.color(
			(OUTLINE_COLOR >> 16 & 255) / 255.0F,
			(OUTLINE_COLOR >> 8 & 255) / 255.0F,
			(OUTLINE_COLOR & 255) / 255.0F,
			(OUTLINE_COLOR >> 24 & 255) / 255.0F
		);
		GL11.glLineWidth(2F);
		GL11.glLineStipple(2, (short) 0x00FF);

		if (outlineDirty) {
			if (outlineList == -1) {
				outlineList = GLAllocation.generateDisplayLists(1);
			}
			GlStateManager.glNewList(outlineList, GL11.GL_COMPILE);
			Tessellator tessellator = Tessellator.getInstance();
			BufferBuilder buffer = tessellator.getBuffer();
			buffer.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION);
			buffer.pos(area.x, area.y, 0).endVertex();
			buffer.pos(area.x + area.width, area.y, 0).endVertex();
			buffer.pos(area.x + area.width, area.y + area.height, 0).endVertex();
			buffer.pos(area.x, area.y + area.height, 0).endVertex();
			tessellator.draw();
			GlStateManager.glEndList();
			outlineDirty = false;
		}
		GlStateManager.callList(outlineList);

		GL11.glDisable(GL11.GL_LINE_STIPPLE);
		GlStateManager.enableTexture2D();
		GlStateManager.color(1F, 1F, 1F, 1F);

		slots.render(minecraft);
	}

	@Nullable
	IngredientRenderer<?> getHovered(int mouseX, int mouseY) {
		return slots.getHovered(mouseX, mouseY);
	}

	@Nullable
	ClickedIngredient<?> getIngredientUnderMouse(int mouseX, int mouseY) {
		return slots.getIngredientUnderMouse(mouseX, mouseY);
	}
}

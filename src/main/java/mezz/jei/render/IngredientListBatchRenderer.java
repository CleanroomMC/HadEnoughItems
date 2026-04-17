package mezz.jei.render;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.ingredients.ISlowRenderItem;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.group.CollapsedGroupIngredient;
import mezz.jei.input.ClickedIngredient;
import mezz.jei.util.ErrorUtil;
import mezz.jei.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.Gui;

import javax.annotation.Nullable;
import java.awt.Rectangle;
import java.util.*;
import java.util.stream.Collectors;

import static mezz.jei.gui.overlay.IngredientGrid.INGREDIENT_HEIGHT;
import static mezz.jei.gui.overlay.IngredientGrid.INGREDIENT_WIDTH;

public class IngredientListBatchRenderer {
    protected final List<List<IngredientListSlot>> slots = new ObjectArrayList<>();

    protected final List<ItemStackFastRenderer> renderItems2d = new ArrayList<>();
    protected final List<ItemStackFastRenderer> renderItems3d = new ArrayList<>();
    protected final List<IngredientRenderer> renderOther = new ArrayList<>();
    protected final List<CollapsedGroupRenderer> renderCollapsed = new ArrayList<>();
    protected final Map<Integer, CollapsedGroupIngredient> collapsedStackIndexed = new HashMap<>();
    protected final Map<IIngredientListElement<?>, CollapsedGroupIngredient> expandedElementToGroup = new HashMap<>();
    // Per-group list of individual slot rectangles (used for per-slot fill + edge-detection border).
    protected final Map<CollapsedGroupIngredient, List<Rectangle>> expandedGroupSlots = new HashMap<>();

    @Nullable
    private Framebuffer framebuffer = null;
    private boolean allowBuffering;
    private boolean refreshBuffer = true;
    protected int size = 0;
    protected int maxSize = 0;
    private int width;
    private int maxWidth;
    private int height;


    public IngredientListBatchRenderer() {
        this(true);
    }

    public IngredientListBatchRenderer(boolean allowBuffering) {
        this.allowBuffering = allowBuffering;
    }

    public void clear() {
        slots.clear();

        renderItems2d.clear();
        renderItems3d.clear();
        renderOther.clear();
        renderCollapsed.clear();
        collapsedStackIndexed.clear();
        expandedElementToGroup.clear();
        expandedGroupSlots.clear();
        size = 0;
        maxSize = 0;

        width = 0;
        maxWidth = 0;
        height = 0;
    }

    public int size() {
        return size;
    }

    public void add(List<IngredientListSlot> ingredientListSlot) {
        slots.add(ingredientListSlot);
    }

    public List<IngredientListSlot> getAllGuiIngredientSlots() {
        return slots.stream().flatMap(List::stream).collect(Collectors.toList());
    }

    protected void setSlots(final int startIndex, List<IIngredientListElement> ingredientList) {
        // We need to clear all of them anyway.
        for (List<IngredientListSlot> row : slots) {
            for (IngredientListSlot slot : row) {
                slot.clear();
            }
        }

        int i = startIndex;
        for (List<IngredientListSlot> row : slots) {
            maxSize += (int) row.stream().filter(IngredientListSlot::isFree).count();
            for (int column = 0; column < row.size(); column++) {
                if (i >= ingredientList.size()) {
                    break;
                }
                IIngredientListElement<?> element = ingredientList.get(i);
                IngredientListSlot ingredientListSlot = row.get(column);
                if (ingredientListSlot.isBlocked()) {
                    continue;
                }
                set(ingredientListSlot, element);
                size++;
                i++;
            }
        }
    }

    public void set(final int startIndex, List<IIngredientListElement> ingredientList) {
        renderItems2d.clear();
        renderItems3d.clear();
        renderOther.clear();
        renderCollapsed.clear();
        collapsedStackIndexed.clear();
        expandedElementToGroup.clear();
        expandedGroupSlots.clear();
        maxSize = 0;
        size = 0;

        setSlots(startIndex, ingredientList);

        invalidateBuffer();
    }

    /**
     * Sets the grid contents from a collapsed ingredient list (mixed IIngredientListElement and CollapsedStack objects).
     * Collapsed groups are rendered as a single slot; expanded groups have their items rendered individually.
     */
    public void setCollapsed(final int startIndex, List<IIngredientListElement> collapsedList) {
        renderItems2d.clear();
        renderItems3d.clear();
        renderOther.clear();
        renderCollapsed.clear();
        collapsedStackIndexed.clear();
        expandedElementToGroup.clear();
        expandedGroupSlots.clear();
        maxSize = 0;
        size = 0;

        for (List<IngredientListSlot> row : slots) {
            for (IngredientListSlot slot : row) {
                slot.clear();
            }
        }

        // Flatten the ENTIRE collapsed list into display items first, then slice at startIndex.
        // This ensures expanded groups don't break pagination — firstItemIndex is an index into
        // the flattened view, which matches what collapsedSize() now returns.
        List<IIngredientListElement> displayItems = new ArrayList<>();
        Map<IIngredientListElement, CollapsedGroupIngredient> itemToCollapsed = new HashMap<>();
        for (IIngredientListElement obj : collapsedList) {
            if (obj instanceof CollapsedGroupIngredient) {
                CollapsedGroupIngredient collapsed = (CollapsedGroupIngredient) obj;
                if (collapsed.isExpanded()) {
                    List<IIngredientListElement<?>> filterIngredients = collapsed.getFilterIngredients();
                    if (filterIngredients.size() == 1) {
                        // Expanded but filtered to a single item: treat as a plain slot, no group border.
                        displayItems.add(filterIngredients.get(0));
                    } else {
                        // Expanded: add each ingredient individually, track which belong to this group
                        for (IIngredientListElement<?> element : filterIngredients) {
                            displayItems.add(element);
                            itemToCollapsed.put(element, collapsed);
                        }
                    }
                } else if (collapsed.size() == 1) {
                    // Single-item group: render as a plain ingredient slot without collapsed visuals.
                    // Not tracked in itemToCollapsed so clicks/hover treat it as a normal item.
                    displayItems.add(collapsed.getDisplayIngredients().get(0));
                } else {
                    // Collapsed: add the CollapsedStack itself as a single display item
                    displayItems.add(collapsed);
                }
            } else {
                displayItems.add(obj);
            }
        }

        int i = startIndex;
        int slotIndex = 0;
        for (List<IngredientListSlot> row : slots) {
            maxSize += (int) row.stream().filter(IngredientListSlot::isFree).count();
            for (int column = 0; column < row.size(); column++) {
                if (i >= displayItems.size()) {
                    break;
                }
                IngredientListSlot ingredientListSlot = row.get(column);
                if (ingredientListSlot.isBlocked()) {
                    slotIndex++;
                    continue;
                }
                IIngredientListElement displayItem = displayItems.get(i);
                if (displayItem instanceof CollapsedGroupIngredient) {
                    CollapsedGroupIngredient collapsed = (CollapsedGroupIngredient) displayItem;
                    CollapsedGroupRenderer renderer = new CollapsedGroupRenderer(collapsed);
                    renderer.setArea(ingredientListSlot.getArea());
                    renderer.setPadding(1);
                    renderCollapsed.add(renderer);
                    collapsedStackIndexed.put(slotIndex, collapsed);
                } else {
                    set(ingredientListSlot, displayItem);
                    CollapsedGroupIngredient parentCollapsed = itemToCollapsed.get(displayItem);
                    if (parentCollapsed != null) {
                        collapsedStackIndexed.put(slotIndex, parentCollapsed);
                        expandedElementToGroup.put(displayItem, parentCollapsed);
                        expandedGroupSlots.computeIfAbsent(parentCollapsed, k -> new ArrayList<>())
                            .add(new Rectangle(ingredientListSlot.getArea()));
                    }
                }
                size++;
                i++;
                slotIndex++;
            }
        }

        invalidateBuffer();
    }

    /**
     * Returns the maximum number of ingredients that can be displayed, if none of them ended rows early.
     * @return the maximum number of ingredients.
     */
    public int getMaxSize() {
        return maxSize;
    }

    public void invalidateBuffer() {
        refreshBuffer = true;
    }

    protected <V> void set(IngredientListSlot ingredientListSlot, IIngredientListElement<V> element) {
        V ingredient = element.getIngredient();
        if (ingredient instanceof ItemStack) {
            //noinspection unchecked
            IIngredientListElement<ItemStack> itemStackElement = (IIngredientListElement<ItemStack>) element;
            ItemStack itemStack = itemStackElement.getIngredient();
            IBakedModel bakedModel;
            try {
                bakedModel = Minecraft.getMinecraft().getRenderItem().getItemModelWithOverrides(itemStack, null, null);
                Preconditions.checkNotNull(bakedModel, "IBakedModel must not be null.");
            } catch (Throwable throwable) {
                String stackInfo = ErrorUtil.getItemStackInfo(itemStack);
                Log.get().error("ItemStack crashed getting IBakedModel. {}", stackInfo, throwable);
                return;
            }

            if (!bakedModel.isBuiltInRenderer() && !(itemStack.getItem() instanceof ISlowRenderItem)) {
                ItemStackFastRenderer renderer = new ItemStackFastRenderer(itemStackElement, bakedModel);
                ingredientListSlot.setIngredientRenderer(renderer);
                if (bakedModel.isGui3d()) {
                    renderItems3d.add(renderer);
                } else {
                    renderItems2d.add(renderer);
                }
                return;
            }
        }

        IngredientRenderer<V> renderer = new IngredientRenderer<>(element);
        ingredientListSlot.setIngredientRenderer(renderer);
        renderOther.add(renderer);
    }

    /**
     * Moves the slots around to fit the given width. Used for tooltip rendering, which can have width resizing.
     * @param maxWidth The maximum width allowed for the grid.
     */
    public void moveSlotsToFit(int maxWidth) {
        if (this.maxWidth / INGREDIENT_WIDTH == maxWidth / INGREDIENT_WIDTH) {
            return;
        }
        int xPos = 0;
        int yPos = 0;
        this.maxWidth = maxWidth;
        width = 0;
        for (List<IngredientListSlot> row : slots) {
            for (IngredientListSlot slot : row) {
                if (xPos >= maxWidth) {
                    xPos = 0;
                    yPos += INGREDIENT_HEIGHT;
                }
                slot.getArea().setLocation(xPos, yPos);
                xPos += INGREDIENT_WIDTH;
                if (xPos > width) {
                    width = xPos;
                }
            }
            xPos = 0;
            yPos += INGREDIENT_HEIGHT;
        }
        this.height = yPos;
    }

    @Nullable
    public ClickedIngredient<?> getIngredientUnderMouse(int mouseX, int mouseY) {
        // Check collapsed renderers first
        CollapsedGroupRenderer collapsedHovered = getHoveredCollapsed(mouseX, mouseY);
        if (collapsedHovered != null) {
            // If the search has filtered this group to a single item, act as if the user
            // clicked that item directly — no expand step needed.
            CollapsedGroupIngredient stack = collapsedHovered.getCollapsedStack();
            if (stack.size() == 1) {
                IIngredientListElement<?> single = stack.getDisplayIngredients().get(0);
                return ClickedIngredient.create(single.getIngredient(), collapsedHovered.getArea());
            }
            return collapsedHovered.getClickedIngredient();
        }
        IngredientRenderer hovered = getHovered(mouseX, mouseY);
        if (hovered != null) {
            IIngredientListElement element = hovered.getElement();
            return ClickedIngredient.create(element.getIngredient(), hovered.getArea());
        }
        return null;
    }

    @Nullable
    public IngredientRenderer getHovered(int mouseX, int mouseY) {
        for (List<IngredientListSlot> row : slots)
            for (IngredientListSlot slot : row)
                if (slot.isMouseOver(mouseX, mouseY))
                    return slot.getIngredientRenderer();
        return null;
    }

    @Nullable
    public CollapsedGroupIngredient getExpandedCollapsedGroupAt(int mouseX, int mouseY) {
        IngredientRenderer hovered = getHovered(mouseX, mouseY);
        if (hovered == null) {
            return null;
        }
        return expandedElementToGroup.get(hovered.getElement());
    }

    public void renderExpandedGroupOutlines() {
        if (expandedGroupSlots.isEmpty()) {
            return;
        }
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        );
        for (Map.Entry<CollapsedGroupIngredient, List<Rectangle>> slots : expandedGroupSlots.entrySet()) {
            int bgColor = slots.getKey().getBackgroundColor();
            int borderColor = slots.getKey().getBorderColor();
            // Build a fast lookup set keyed by "x,y" to detect adjacent group slots.
            Set<String> keys = new HashSet<>();
            for (Rectangle r : slots.getValue()) keys.add(r.x + "," + r.y);
            for (Rectangle r : slots.getValue()) {
                // Background fill for each slot in group
                Gui.drawRect(r.x, r.y, r.x + r.width, r.y + r.height, bgColor);

                // Determine which cardinal neighbors are part of this group
                boolean hasTop = keys.contains(r.x + "," + (r.y - INGREDIENT_HEIGHT));
                boolean hasBottom = keys.contains(r.x + "," + (r.y + INGREDIENT_HEIGHT));
                boolean hasLeft = keys.contains((r.x - INGREDIENT_WIDTH) + "," + r.y);
                boolean hasRight = keys.contains((r.x + INGREDIENT_WIDTH) + "," + r.y);

                // Horizontal edges own the full width including corner pixels — drawn exactly once.
                if (!hasTop) {
                    Gui.drawRect(r.x, r.y, r.x + r.width, r.y + 1, borderColor); // top
                }
                if (!hasBottom) {
                    Gui.drawRect(r.x, r.y + r.height - 1, r.x + r.width, r.y + r.height, borderColor); // bottom
                }

                // Vertical edges are inset by 1px at each end where a horizontal edge already owns that corner,
                int vTop = r.y + (!hasTop ? 1 : 0);
                int vBottom = r.y + r.height - (!hasBottom ? 1 : 0);
                if (!hasLeft && vTop < vBottom) {
                    Gui.drawRect(r.x, vTop, r.x + 1, vBottom, borderColor); // left
                }
                if (!hasRight && vTop < vBottom) {
                    Gui.drawRect(r.x + r.width - 1, vTop, r.x + r.width, vBottom, borderColor); // right
                }

                // Inner concave corner pixels: both cardinal neighbors are part of the group so neither draws.
                if (hasTop && hasLeft && !keys.contains((r.x - INGREDIENT_WIDTH) + "," + (r.y - INGREDIENT_HEIGHT))) {
                    Gui.drawRect(r.x, r.y, r.x + 1, r.y + 1, borderColor); // top-left inner corner
                }
                if (hasTop && hasRight && !keys.contains((r.x + INGREDIENT_WIDTH) + "," + (r.y - INGREDIENT_HEIGHT))) {
                    Gui.drawRect(r.x + r.width - 1, r.y, r.x + r.width, r.y + 1, borderColor); // top-right inner corner
                }
                if (hasBottom && hasLeft && !keys.contains((r.x - INGREDIENT_WIDTH) + "," + (r.y + INGREDIENT_HEIGHT))) {
                    Gui.drawRect(r.x, r.y + r.height - 1, r.x + 1, r.y + r.height, borderColor); // bottom-left inner corner
                }
                if (hasBottom && hasRight && !keys.contains((r.x + INGREDIENT_WIDTH) + "," + (r.y + INGREDIENT_HEIGHT))) {
                    Gui.drawRect(r.x + r.width - 1, r.y + r.height - 1, r.x + r.width, r.y + r.height, borderColor); // bottom-right inner corner
                }
            }
        }
        GlStateManager.disableBlend();
    }

    @Nullable
    public CollapsedGroupRenderer getHoveredCollapsed(int mouseX, int mouseY) {
        for (CollapsedGroupRenderer renderer : renderCollapsed) {
            if (renderer.isMouseOver(mouseX, mouseY)) {
                return renderer;
            }
        }
        return null;
    }

    public Map<Integer, CollapsedGroupIngredient> getCollapsedStackIndexed() {
        return collapsedStackIndexed;
    }

    public void render(Minecraft minecraft) {
        if (allowBuffering && !Config.isEditModeEnabled() && Config.bufferIngredientRenders() && OpenGlHelper.isFramebufferEnabled()) {
            if (framebuffer == null) {
                framebuffer = new Framebuffer(minecraft.displayWidth, minecraft.displayHeight, true);
                framebuffer.framebufferColor[0] = 0.0F;
                framebuffer.framebufferColor[1] = 0.0F;
                framebuffer.framebufferColor[2] = 0.0F;
            }
            if (refreshBuffer) {
                framebuffer.createBindFramebuffer(minecraft.displayWidth, minecraft.displayHeight);
                framebuffer.framebufferClear();
                framebuffer.bindFramebuffer(false);
                GlStateManager.disableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            } else {
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                framebuffer.bindFramebufferTexture();
                GlStateManager.enableTexture2D();
                ScaledResolution res = new ScaledResolution(minecraft);
                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder buffer = tessellator.getBuffer();
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
                buffer.pos(0, res.getScaledHeight_double(), 0.0).tex(0, 0).endVertex();
                buffer.pos(res.getScaledWidth_double(), res.getScaledHeight_double(), 0.0).tex(1, 0).endVertex();
                buffer.pos(res.getScaledWidth_double(), 0, 0.0).tex(1, 1).endVertex();
                buffer.pos(0, 0, 0.0).tex(0, 1).endVertex();
                tessellator.draw();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
                return;
            }
        }

        renderImpl(minecraft);

        if (allowBuffering && refreshBuffer && !Config.isEditModeEnabled() && Config.bufferIngredientRenders() && OpenGlHelper.isFramebufferEnabled()) {
            refreshBuffer = false;
            minecraft.getFramebuffer().bindFramebuffer(false);
            // ensure that we actually render the new items
            render(minecraft);
        }
    }

    /**
     * renders all ItemStacks
     */
    protected void renderImpl(Minecraft minecraft) {
        RenderHelper.enableGUIStandardItemLighting();

        RenderItem renderItem = minecraft.getRenderItem();
        TextureManager textureManager = minecraft.getTextureManager();
        renderItem.zLevel += 50.0F;

        textureManager.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        textureManager.getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // 3d Items
        GlStateManager.enableLighting();
        for (ItemStackFastRenderer slot : renderItems3d) {
            slot.renderItemAndEffectIntoGUI();
        }

        // 2d Items
        GlStateManager.disableLighting();
        for (ItemStackFastRenderer slot : renderItems2d) {
            slot.renderItemAndEffectIntoGUI();
        }

        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();

        textureManager.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        textureManager.getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();

        renderItem.zLevel -= 50.0F;

        // overlays
        for (ItemStackFastRenderer slot : renderItems3d) {
            slot.renderOverlay();
        }

        for (ItemStackFastRenderer slot : renderItems2d) {
            slot.renderOverlay();
        }

        GlStateManager.disableLighting();

        // other rendering
        for (IngredientRenderer slot : renderOther) {
            slot.renderSlow();
        }

        // collapsed group rendering — lighting enabled once for all groups; each renderer
        // assumes it is on and does not toggle it per item.
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        for (CollapsedGroupRenderer collapsed : renderCollapsed) {
            collapsed.render(minecraft);
        }

        RenderHelper.disableStandardItemLighting();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}

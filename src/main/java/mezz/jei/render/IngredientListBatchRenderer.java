package mezz.jei.render;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.ingredients.ISlowRenderItem;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static mezz.jei.gui.overlay.IngredientGrid.INGREDIENT_HEIGHT;
import static mezz.jei.gui.overlay.IngredientGrid.INGREDIENT_WIDTH;

public class IngredientListBatchRenderer {
    protected final List<List<IngredientListSlot>> slots = new ObjectArrayList<>();

    protected final List<ItemStackFastRenderer> renderItems2d = new ArrayList<>();
    protected final List<ItemStackFastRenderer> renderItems3d = new ArrayList<>();
    protected final List<IngredientRenderer> renderOther = new ArrayList<>();

    @Nullable
    private Framebuffer framebuffer = null;
    private boolean refreshBuffer = true;
    protected int size = 0;
    protected int maxSize = 0;
    private int width;
    private int maxWidth;
    private int height;

    public void clear() {
        slots.clear();

        renderItems2d.clear();
        renderItems3d.clear();
        renderOther.clear();
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

    public void set(final int startIndex, List<IIngredientListElement> ingredientList) {
        renderItems2d.clear();
        renderItems3d.clear();
        renderOther.clear();
        maxSize = 0;
        size = 0;

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
                ItemStackFastRenderer renderer = new ItemStackFastRenderer(itemStackElement);
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

    public void render(Minecraft minecraft) {
        if (!Config.isEditModeEnabled() && Config.bufferIngredientRenders() && OpenGlHelper.isFramebufferEnabled()) {
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

        if (!Config.isEditModeEnabled() && Config.bufferIngredientRenders() && refreshBuffer && OpenGlHelper.framebufferSupported) {
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

        RenderHelper.disableStandardItemLighting();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}

package mezz.jei.render;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import mezz.jei.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;

import com.google.common.base.Preconditions;
import mezz.jei.api.ingredients.ISlowRenderItem;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.input.ClickedIngredient;
import mezz.jei.util.ErrorUtil;
import mezz.jei.util.Log;
import org.lwjgl.opengl.GL11;

public class IngredientListBatchRenderer {
	private final List<IngredientListSlot> slots = new ArrayList<>();

	private final List<ItemStackFastRenderer> renderItems2d = new ArrayList<>();
	private final List<ItemStackFastRenderer> renderItems3d = new ArrayList<>();
	private final List<IngredientRenderer> renderOther = new ArrayList<>();

	@Nullable private Framebuffer framebuffer = null;
	private boolean refreshBuffer = true;
	private int blocked = 0;

	public void clear() {
		slots.clear();

		renderItems2d.clear();
		renderItems3d.clear();
		renderOther.clear();
		blocked = 0;
	}

	public int size() {
		return slots.size() - blocked;
	}

	public void add(IngredientListSlot ingredientListSlot) {
		slots.add(ingredientListSlot);
	}

	public List<IngredientListSlot> getAllGuiIngredientSlots() {
		return slots;
	}

	public void set(final int startIndex, List<IIngredientListElement> ingredientList) {
		renderItems2d.clear();
		renderItems3d.clear();
		renderOther.clear();
		blocked = 0;

		int i = startIndex;
		for (IngredientListSlot ingredientListSlot : slots) {
			if (ingredientListSlot.isBlocked()) {
				ingredientListSlot.clear();
				blocked++;
			} else {
				if (i >= ingredientList.size()) {
					ingredientListSlot.clear();
				} else {
					IIngredientListElement<?> element = ingredientList.get(i);
					set(ingredientListSlot, element);
				}
				i++;
			}
		}

		invalidateBuffer();
	}

	public void invalidateBuffer() {
		refreshBuffer = true;
	}

	private <V> void set(IngredientListSlot ingredientListSlot, IIngredientListElement<V> element) {
		ingredientListSlot.clear();

		V ingredient = element.getIngredient();
		if (ingredient instanceof ItemStack) {
			//noinspection unchecked
			IIngredientListElement<ItemStack> itemStackElement = (IIngredientListElement<ItemStack>) element;
			ItemStack itemStack = itemStackElement.getIngredient();
			IBakedModel bakedModel;
			ItemModelMesher itemModelMesher = Minecraft.getMinecraft().getRenderItem().getItemModelMesher();
			try {
				bakedModel = itemModelMesher.getItemModel(itemStack);
				bakedModel = bakedModel.getOverrides().handleItemState(bakedModel, itemStack, null, null);
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
		for (IngredientListSlot slot : slots) {
			if (slot.isMouseOver(mouseX, mouseY)) {
				return slot.getIngredientRenderer();
			}
		}
		return null;
	}

	public void render(Minecraft minecraft) {
		if (!Config.isEditModeEnabled() && Config.bufferIngredientRenders() && OpenGlHelper.framebufferSupported) {
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
	private void renderImpl(Minecraft minecraft) {
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

}

package mezz.jei.render;

import java.awt.Rectangle;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemModelMesher;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.color.ItemColors;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.lwjgl.opengl.GL11;

import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.util.ErrorUtil;

public class ItemStackFastRenderer extends IngredientRenderer<ItemStack> {
	private static final ResourceLocation RES_ITEM_GLINT = new ResourceLocation("textures/misc/enchanted_item_glint.png");

	public ItemStackFastRenderer(IIngredientListElement<ItemStack> itemStackElement, IBakedModel model) {
		super(itemStackElement);
	}

	public void renderItemAndEffectIntoGUI() {
		try {
			uncheckedRenderItemAndEffectIntoGUI();
		} catch (RuntimeException | LinkageError e) {
			throw ErrorUtil.createRenderIngredientException(e, element.getIngredient());
		}
	}

	private IBakedModel getBakedModel() {
		ItemModelMesher itemModelMesher = Minecraft.getMinecraft().getRenderItem().getItemModelMesher();
		ItemStack itemStack = element.getIngredient();
		IBakedModel bakedModel = itemModelMesher.getItemModel(itemStack);
		return bakedModel.getOverrides().handleItemState(bakedModel, itemStack, null, null);
	}

	private void uncheckedRenderItemAndEffectIntoGUI() {
		if (Config.isEditModeEnabled()) {
			renderEditMode(element, area, padding);
			GlStateManager.enableBlend();
		}

		ItemStack itemStack = element.getIngredient();
		IBakedModel bakedModel = getBakedModel();

		renderItemAndEffectIntoGUI(Minecraft.getMinecraft(), itemStack, bakedModel, area.x + padding, area.y + padding);
	}

	public static void renderItemAndEffectIntoGUI(Minecraft minecraft, ItemStack itemStack, IBakedModel bakedModel, int x, int y) {
		GlStateManager.pushMatrix();
		try {
			GlStateManager.translate(x + 8.0f, y + 8.0f, 150.0F);
			GlStateManager.scale(16F, -16F, 16F);
			bakedModel = ForgeHooksClient.handleCameraTransforms(bakedModel, ItemCameraTransforms.TransformType.GUI, false);
			GlStateManager.translate(-0.5F, -0.5F, -0.5F);

			renderModel(minecraft, bakedModel, -1, itemStack);

			if (itemStack.hasEffect()) {
				renderEffect(minecraft, bakedModel);
			}
		} finally {
			GlStateManager.popMatrix();
		}
	}

	private static void renderEffect(Minecraft minecraft, IBakedModel model) {
		TextureManager textureManager = minecraft.getTextureManager();

		GlStateManager.depthMask(false);
		GlStateManager.depthFunc(514);
		GlStateManager.blendFunc(768, 1);
		textureManager.bindTexture(RES_ITEM_GLINT);
		GlStateManager.matrixMode(5890);

		GlStateManager.pushMatrix();
		try {
			GlStateManager.scale(8.0F, 8.0F, 8.0F);
			float f = (float) (Minecraft.getSystemTime() % 3000L) / 3000.0F / 8.0F;
			GlStateManager.translate(f, 0.0F, 0.0F);
			GlStateManager.rotate(-50.0F, 0.0F, 0.0F, 1.0F);
			renderModel(minecraft, model, -8372020, ItemStack.EMPTY);
		} finally {
			GlStateManager.popMatrix();
		}

		GlStateManager.pushMatrix();
		try {
			GlStateManager.scale(8.0F, 8.0F, 8.0F);
			float f1 = (float) (Minecraft.getSystemTime() % 4873L) / 4873.0F / 8.0F;
			GlStateManager.translate(-f1, 0.0F, 0.0F);
			GlStateManager.rotate(10.0F, 0.0F, 0.0F, 1.0F);
			renderModel(minecraft, model, -8372020, ItemStack.EMPTY);
		} finally {
			GlStateManager.popMatrix();
		}

		GlStateManager.matrixMode(5888);
		GlStateManager.blendFunc(770, 771);
		GlStateManager.depthFunc(515);
		GlStateManager.depthMask(true);
		textureManager.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
	}

	private static void renderModel(Minecraft minecraft, IBakedModel model, int color, ItemStack itemStack) {
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		bufferBuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.ITEM);
		try {
			for (EnumFacing facing : EnumFacing.values()) {
				renderQuads(minecraft, bufferBuilder, model.getQuads(null, facing, 0L), color, itemStack);
			}
			renderQuads(minecraft, bufferBuilder, model.getQuads(null, null, 0L), color, itemStack);
		} finally {
			tessellator.draw();
		}
	}

	private static void renderQuads(Minecraft minecraft, BufferBuilder bufferBuilder, List<BakedQuad> quads, int color, ItemStack itemStack) {
		if (quads == null) {
			return;
		}

		boolean applyTint = color == -1 && !itemStack.isEmpty();
		ItemColors itemColors = minecraft.getItemColors();
		for (BakedQuad bakedQuad : quads) {
			if (bakedQuad == null) {
				continue;
			}

			int quadColor = color;
			if (applyTint && bakedQuad.hasTintIndex()) {
				quadColor = itemColors.colorMultiplier(itemStack, bakedQuad.getTintIndex());
				if (EntityRenderer.anaglyphEnable) {
					quadColor = TextureUtil.anaglyphColor(quadColor);
				}
				quadColor |= 0xFF000000;
			}
			LightUtil.renderQuadColor(bufferBuilder, bakedQuad, quadColor);
		}
	}

	public void renderOverlay() {
		ItemStack itemStack = element.getIngredient();
		try {
			renderOverlay(itemStack, area, padding);
		} catch (RuntimeException | LinkageError e) {
			throw ErrorUtil.createRenderIngredientException(e, element.getIngredient());
		}
	}

	private void renderOverlay(ItemStack itemStack, Rectangle area, int padding) {
		FontRenderer font = getFontRenderer(itemStack);
		RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
		renderItem.renderItemOverlayIntoGUI(font, itemStack, area.x + padding, area.y + padding, null);
	}

	public static FontRenderer getFontRenderer(ItemStack itemStack) {
		Item item = itemStack.getItem();
		FontRenderer fontRenderer = item.getFontRenderer(itemStack);
		if (fontRenderer == null) {
			fontRenderer = Minecraft.getMinecraft().fontRenderer;
		}
		return fontRenderer;
	}
}

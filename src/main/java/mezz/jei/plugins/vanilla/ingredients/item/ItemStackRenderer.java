package mezz.jei.plugins.vanilla.ingredients.item;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.util.ErrorUtil;
import mezz.jei.util.Log;
import mezz.jei.util.Translator;
import net.minecraftforge.common.IRarity;
import org.apache.commons.lang3.concurrent.ConcurrentRuntimeException;

public class ItemStackRenderer implements IIngredientRenderer<ItemStack> {
	@Override
	public void render(Minecraft minecraft, int xPosition, int yPosition, @Nullable ItemStack ingredient) {
		if (ingredient != null) {
			GlStateManager.enableDepth();
			RenderHelper.enableGUIStandardItemLighting();
			FontRenderer font = getFontRenderer(minecraft, ingredient);
			minecraft.getRenderItem().renderItemAndEffectIntoGUI(ingredient, xPosition, yPosition);

			if (ingredient.getCount() > 1) {
				renderCustomStackSize(font, ingredient, xPosition, yPosition);
			} else {
				ItemStack overlayStack = ingredient.copy();
				overlayStack.setCount(1);
				minecraft.getRenderItem().renderItemOverlayIntoGUI(font, overlayStack, xPosition, yPosition, null);
			}

			GlStateManager.disableBlend();
			RenderHelper.disableStandardItemLighting();
		}
	}

	/**
	 * Custom method for rendering item stack count
	 * @param font The font renderer
	 * @param stack The item stack
	 * @param xPosition X coordinate
	 * @param yPosition Y coordinate
	 */
	private void renderCustomStackSize(FontRenderer font, ItemStack stack, int xPosition, int yPosition) {
		String countText = formatStackCount(stack.getCount());

		GlStateManager.pushMatrix();
		GlStateManager.disableLighting();
		GlStateManager.disableDepth();
		GlStateManager.disableBlend();

		boolean shouldScale = stack.getCount() > 99;
		if (shouldScale) {
			GlStateManager.scale(0.5F, 0.5F, 1.0F);
		}

		int x = shouldScale ?
				(xPosition + 16) * 2 - font.getStringWidth(countText) :
				xPosition + 16 - font.getStringWidth(countText);
		int y = shouldScale ?
				(yPosition + 16) * 2 - 8 :
				yPosition + 16 - 8;

		font.drawStringWithShadow(countText, x, y, 0xFFFFFF);

		GlStateManager.popMatrix();
		GlStateManager.enableLighting();
		GlStateManager.enableDepth();
		GlStateManager.enableBlend();
	}

	/**
	 * Formats the stack count for display
	 */
	private String formatStackCount(int count) {
		if (count <= 99) {
			return String.valueOf(count);
		}

		if (count <= 9999) {
			return String.valueOf(count);
		}

		if (count <= 999999) {
			float k = count / 1000f;
			return String.format(k % 1 == 0 ? "%.0fk" : "%.1fk", k);
		}

		if (count <= 999999999) {
			float m = count / 1000000f;
			return String.format(m % 1 == 0 ? "%.0fm" : "%.1fm", m);
		}

        float g = count / 1000000000f;
        return String.format(g % 1 == 0 ? "%.0fg" : "%.1fg", g);

    }

	@Override
	public List<String> getTooltip(Minecraft minecraft, ItemStack ingredient, ITooltipFlag tooltipFlag) {
		EntityPlayer player = minecraft.player;
		List<String> list;
		try {
			list = ingredient.getTooltip(player, tooltipFlag);
		} catch (RuntimeException | LinkageError e) {
			String itemStackInfo = ErrorUtil.getItemStackInfo(ingredient);
			Log.get().error("Failed to get tooltip: {}", itemStackInfo, e);
			if (Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
				list = new ArrayList<>();
				list.add(TextFormatting.RED + Translator.translateToLocal("jei.tooltip.error.crash"));
				return list;
			}
			throw new ConcurrentRuntimeException(e);
		}

		IRarity rarity;
		try {
			rarity = ingredient.getItem().getForgeRarity(ingredient);
		} catch (RuntimeException | LinkageError e) {
			String itemStackInfo = ErrorUtil.getItemStackInfo(ingredient);
			Log.get().error("Failed to get rarity: {}", itemStackInfo, e);
			rarity = EnumRarity.COMMON;
		}

		for (int k = 0; k < list.size(); ++k) {
			if (k == 0) {
				list.set(k, rarity.getColor() + list.get(k));
			} else {
				list.set(k, TextFormatting.GRAY + list.get(k));
			}
		}

		return list;
	}

	@Override
	public FontRenderer getFontRenderer(Minecraft minecraft, ItemStack ingredient) {
		FontRenderer fontRenderer = ingredient.getItem().getFontRenderer(ingredient);
		if (fontRenderer == null) {
			fontRenderer = minecraft.fontRenderer;
		}
		return fontRenderer;
	}
}
package mezz.jei.transfer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextFormatting;

import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.gui.TooltipRenderer;
import mezz.jei.util.Translator;

import javax.annotation.Nullable;

public class RecipeTransferErrorTooltip implements IRecipeTransferError {
	private final List<String> message = new ArrayList<>();
	private final String reason;

	public RecipeTransferErrorTooltip(String message) {
		this.reason = message;
		this.message.add(Translator.translateToLocal("jei.tooltip.transfer"));
		this.message.add(TextFormatting.RED + message);
	}

	/**
	 * The bare reason, without the heading this error draws for itself, for callers folding it into a
	 * tooltip of their own — the bookmark group tooltip says why a chain cannot be crafted where the
	 * player is standing.
	 */
	public String getReason() {
		return reason;
	}

	@Nullable
	@Override
	public String getSimpleReason() {
		return getReason();
	}

	@Override
	public Type getType() {
		return Type.USER_FACING;
	}

	@Override
	public void showError(Minecraft minecraft, int mouseX, int mouseY, IRecipeLayout recipeLayout, int recipeX, int recipeY) {
		TooltipRenderer.drawHoveringText(minecraft, message, mouseX, mouseY, 150);
	}
}

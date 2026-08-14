package mezz.jei.autocrafting;

import mezz.jei.Internal;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IAutocraftingHandler;
import mezz.jei.api.recipe.transfer.IRecipeCraftingHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.recipes.RecipeRegistry;
import mezz.jei.transfer.RecipeTransferErrorInternal;
import mezz.jei.transfer.RecipeTransferErrorTooltip;
import mezz.jei.util.Log;
import mezz.jei.util.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.inventory.Container;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Driver for recipe bookmark groups.
 * <p>
 * The plan lists recipes before the ingredients they need - by walking backwards.
 * With the deepest dependency being crafted first. Crafting is asynchronous.
 * The transfer handler answers through {@link #stepFinished}.
 * Once the server has replied, only then does the next step of the chain start.
 */
public class AutocraftingHandler implements IAutocraftingHandler {

	/**
	 * Asks the open container whether it could run, without running it.
	 * <p>
	 * Shared with the bookmark group tooltip.
	 *
	 * @return null if the step could run, otherwise the reason it could not
	 */
	@Nullable
	public static IRecipeTransferError isRecipeAutoCraftable(RecipeBookmarkItem<?> recipe, int craftCount) {
		if (!recipe.isPopulated()) {
			return RecipeTransferErrorInternal.INSTANCE;
		}
		IRecipeLayout recipeLayout = recipe.createLayout();
		if (recipeLayout == null) {
			return RecipeTransferErrorInternal.INSTANCE;
		}
		return isRecipeAutoCraftable(recipe, recipeLayout, craftCount);
	}

	@Nullable
	private static IRecipeTransferError isRecipeAutoCraftable(RecipeBookmarkItem<?> recipe, IRecipeLayout recipeLayout, int craftCount) {
		EntityPlayerSP player = Minecraft.getMinecraft().player;
		if (player == null || player.openContainer == null || craftCount <= 0) {
			return RecipeTransferErrorInternal.INSTANCE;
		}
		Container openContainer = player.openContainer;

		RecipeRegistry recipeRegistry = Internal.getRuntime().getRecipeRegistry();
		IRecipeCategory<?> recipeCategory = recipe.category;
		IRecipeTransferHandler<?> transferHandler = recipeRegistry.getRecipeTransferHandler(openContainer, recipeCategory);
		if (!(transferHandler instanceof IRecipeCraftingHandler)) {
			return new RecipeTransferErrorTooltip(
					Translator.translateToLocalFormatted("hei.tooltip.autocraft.wrong_container", recipeCategory.getTitle()));
		}

		@SuppressWarnings("unchecked")
		IRecipeCraftingHandler<Container> craftingHandler = (IRecipeCraftingHandler<Container>) transferHandler;
		return craftingHandler.craft(openContainer, recipeLayout, player, craftCount, false);
	}

	/** Steps still to run. Empty whenever nothing is in progress. */
	private final Deque<PendingStep> pending = new ArrayDeque<>();

	@Nullable
	private PendingStep currentStep;
	private boolean active;
	private boolean waitingForCraftResult;

	/** A step and how many of its crafts are still owed, tracked here rather than on the bookmark. */
	private static final class PendingStep {

		private final RecipeBookmarkItem<?> recipe;

		private long remainingCrafts;

		PendingStep(RecipeBookmarkItem<?> recipe, long remainingCrafts) {
			this.recipe = recipe;
			this.remainingCrafts = remainingCrafts;
		}

	}

	public void start(RecipeBookmarkGroup group) {
		stop();

		List<CraftingPlan.Step> steps = group.plan().steps();
		if (steps.isEmpty()) {
			return;
		}
		// Reversed on the way in, draining from the front to craft dependencies before their dependents
		for (int i = steps.size() - 1; i >= 0; i--) {
			CraftingPlan.Step step = steps.get(i);
			if (step.crafts() > 0L) {
				pending.addLast(new PendingStep(step.recipe(), step.crafts()));
			}
		}
		if (pending.isEmpty()) {
			return;
		}
		this.active = true;
		craftNextStep();
	}

	/**
	 * Works down the queue until a step is dispatched, or nothing is left.
	 * <p>
	 * A step the open container cannot craft is skipped rather than abandoning the chain,
	 * since a later step may well be craftable there.
	 */
	private void craftNextStep() {
		while (!pending.isEmpty()) {
			this.currentStep = pending.pollFirst();
			if (dispatch(this.currentStep)) {
				return;
			}
		}
		stop();
	}

	/**
	 * Asks the open container's transfer handler to run one step.
	 *
	 * @return true if the craft was dispatched and a {@link #stepFinished} callback is now owed
	 */
	private boolean dispatch(PendingStep step) {
		int craftCount = (int) Math.min(Integer.MAX_VALUE, step.remainingCrafts);
		RecipeBookmarkItem<?> recipe = step.recipe;
		if (!recipe.isPopulated()) {
			return false;
		}
		IRecipeLayout recipeLayout = recipe.createLayout();
		if (recipeLayout == null) {
			Log.get().warn("Skipping autocrafting step for {} x{} because its recipe layout could not be created", recipe.getIngredient(), craftCount);
			return false;
		}
		// Dry run first
		IRecipeTransferError error = isRecipeAutoCraftable(recipe, recipeLayout, craftCount);
		if (error != null) {
			Log.get().warn("Skipping autocrafting step for {} x{}: {}", step.recipe.getIngredient(), craftCount,
				error.getSimpleReason() != null ? error.getSimpleReason() : error.getClass().getSimpleName());
			return false;
		}

		EntityPlayerSP player = Minecraft.getMinecraft().player;
		if (player == null || player.openContainer == null) {
			return false;
		}
		Container openContainer = player.openContainer;
		IRecipeTransferHandler<?> transferHandler = Internal.getRuntime().getRecipeRegistry().getRecipeTransferHandler(openContainer, recipe.category);
		if (!(transferHandler instanceof IRecipeCraftingHandler)) {
			return false;
		}
		@SuppressWarnings("unchecked")
		IRecipeCraftingHandler<Container> craftingHandler = (IRecipeCraftingHandler<Container>) transferHandler;

		this.waitingForCraftResult = true;
		error = craftingHandler.craft(openContainer, recipeLayout, player, craftCount, true);
		if (error != null) {
			this.waitingForCraftResult = false;
			Log.get().warn("Failed to dispatch autocrafting step for {} x{}: {}", recipe.getIngredient(), craftCount,
				error.getSimpleReason() != null ? error.getSimpleReason() : error.getClass().getSimpleName());
			return false;
		}
		return true;
	}

	/**
	 * @param itemsCrafted output items the server saw come out of the slot, which is not the same
	 *                     as the number of times the recipe ran whenever it yields a stack at a time
	 */
	@Override
	public void stepFinished(boolean success, int itemsCrafted) {
		if (!this.waitingForCraftResult) {
			return;
		}
		this.waitingForCraftResult = false;

		PendingStep step = this.currentStep;
		if (!success || step == null) {
			stop();
			return;
		}
		if (itemsCrafted <= 0) {
			// No progress
			stop();
			return;
		}

		long craftsDone = step.recipe.outputAmount > 0L ? ChainSolution.craftsFor(itemsCrafted, step.recipe.outputAmount) : itemsCrafted;
		step.remainingCrafts -= craftsDone;
		if (step.remainingCrafts > 0L) {
			pending.addFirst(step);
		}
		craftNextStep();
	}

	@Override
	public void stop() {
		this.waitingForCraftResult = false;
		this.active = false;
		this.currentStep = null;
		this.pending.clear();
	}

	@Override
	public boolean isActive() {
		return this.active;
	}
}

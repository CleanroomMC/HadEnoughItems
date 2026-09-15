package mezz.jei.recipes;

import mezz.jei.util.Log;
import net.minecraft.inventory.Container;

import com.google.common.collect.ImmutableTable;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.recipe.transfer.IRecipeTransferRegistry;
import mezz.jei.collect.Table;
import mezz.jei.config.Config;
import mezz.jei.config.Constants;
import mezz.jei.startup.StackHelper;
import mezz.jei.transfer.BasicRecipeTransferHandler;
import mezz.jei.transfer.BasicRecipeTransferInfo;
import mezz.jei.util.ErrorUtil;

import java.util.Map;
import java.util.function.ToIntFunction;

public class RecipeTransferRegistry implements IRecipeTransferRegistry {
	private final Table<Class, String, IRecipeTransferHandler> recipeTransferHandlers = Table.hashBasedTable();
	private final Table<Class<?>, String, ToIntFunction<?>> outputSlotProviders = Table.hashBasedTable();
	private final StackHelper stackHelper;
	private final IRecipeTransferHandlerHelper handlerHelper;

	public RecipeTransferRegistry(StackHelper stackHelper, IRecipeTransferHandlerHelper handlerHelper) {
		this.stackHelper = stackHelper;
		this.handlerHelper = handlerHelper;
	}

	@Override
	public <C extends Container> void addRecipeTransferHandler(Class<C> containerClass, String recipeCategoryUid, int recipeSlotStart, int recipeSlotCount, int inventorySlotStart, int inventorySlotCount) {
		ErrorUtil.checkNotNull(containerClass, "containerClass");
		ErrorUtil.checkNotNull(recipeCategoryUid, "recipeCategoryUid");

		IRecipeTransferInfo<C> recipeTransferHelper = new BasicRecipeTransferInfo<>(containerClass, recipeCategoryUid, recipeSlotStart, recipeSlotCount, inventorySlotStart, inventorySlotCount);
		addRecipeTransferHandler(recipeTransferHelper);
	}

	@Override
	public <C extends Container> void addRecipeTransferHandlerWithOutput(Class<C> containerClass, String recipeCategoryUid, int recipeSlotStart, int recipeSlotCount, int inventorySlotStart, int inventorySlotCount, int outputSlot) {
		ErrorUtil.checkNotNull(containerClass, "containerClass");
		ErrorUtil.checkNotNull(recipeCategoryUid, "recipeCategoryUid");

		IRecipeTransferInfo<C> recipeTransferHelper = new BasicRecipeTransferInfo<>(containerClass, recipeCategoryUid, recipeSlotStart, recipeSlotCount, inventorySlotStart, inventorySlotCount)
				.setCraftingSlot(outputSlot);
		addRecipeTransferHandler(recipeTransferHelper);
	}

	@Override
	public <C extends Container> void addRecipeTransferHandler(IRecipeTransferInfo<C> recipeTransferInfo) {
		ErrorUtil.checkNotNull(recipeTransferInfo, "recipeTransferInfo");
		if (Config.isRecipeCategoryDisabled(recipeTransferInfo.getRecipeCategoryUid())) {
			return;
		}

		IRecipeTransferHandler<C> recipeTransferHandler = new BasicRecipeTransferHandler<>(stackHelper, handlerHelper, recipeTransferInfo);
		addRecipeTransferHandler(recipeTransferHandler, recipeTransferInfo.getRecipeCategoryUid());
	}

	@Override
	public void addRecipeTransferHandler(IRecipeTransferHandler<?> recipeTransferHandler, String recipeCategoryUid) {
		ErrorUtil.checkNotNull(recipeTransferHandler, "recipeTransferHandler");
		ErrorUtil.checkNotNull(recipeCategoryUid, "recipeCategoryUid");
		if (Config.isRecipeCategoryDisabled(recipeCategoryUid)) {
			return;
		}

		Class<?> containerClass = recipeTransferHandler.getContainerClass();
		this.recipeTransferHandlers.put(containerClass, recipeCategoryUid, recipeTransferHandler);
	}

	@Override
	public void addUniversalRecipeTransferHandler(IRecipeTransferHandler<?> recipeTransferHandler) {
		ErrorUtil.checkNotNull(recipeTransferHandler, "recipeTransferHandler");

		Class<?> containerClass = recipeTransferHandler.getContainerClass();
		this.recipeTransferHandlers.put(containerClass, Constants.UNIVERSAL_RECIPE_TRANSFER_UID, recipeTransferHandler);
	}

	@Override
	public <C extends Container> void attachOutputSlotProvider(
		Class<C> containerClass,
		String recipeCategoryUid,
		ToIntFunction<C> toOutputSlot
	) {
		ErrorUtil.checkNotNull(containerClass, "containerClass");
		ErrorUtil.checkNotNull(recipeCategoryUid, "recipeCategoryUid");
		ErrorUtil.checkNotNull(toOutputSlot, "toOutputSlot");

		this.outputSlotProviders.put(containerClass, recipeCategoryUid, toOutputSlot);
	}

	@SuppressWarnings("rawtypes")
	public ImmutableTable<Class, String, IRecipeTransferHandler> getRecipeTransferHandlers() {

		for (Class<?> containerClass : outputSlotProviders.viewRows()) {
			Map<String, ToIntFunction<?>> handlersByCategory = outputSlotProviders.getRow(containerClass);
			for (Map.Entry<String, ToIntFunction<?>> entry : handlersByCategory.entrySet()) {
				String recipeCategoryId = entry.getKey();
				ToIntFunction<?> toOutput = entry.getValue();

				IRecipeTransferHandler<?> handler = recipeTransferHandlers.get(containerClass, recipeCategoryId);
				if (handler instanceof BasicRecipeTransferHandler) {
					((BasicRecipeTransferHandler<?>) handler).setToOutputSlotOverride((ToIntFunction) toOutput);
				} else if (handler == null) {
					Log.get().error("Unable to attach output slot provider for type '{}' and category '{}', because no recipe transfer handler is found", containerClass, recipeCategoryId);
				} else {
					Log.get().error("Unable to attach output slot provider for type '{}' and category '{}', because recipe transfer handler is customized", containerClass, recipeCategoryId);
				}
			}
		}

		return recipeTransferHandlers.toImmutable();
	}
}

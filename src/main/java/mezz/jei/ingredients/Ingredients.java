package mezz.jei.ingredients;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IIngredientType;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Ingredients implements IIngredients {
	private final Map<IIngredientType, List<Object>> inputs = new Reference2ObjectOpenHashMap<>();
	private final Map<IIngredientType, List<Object>> outputs = new Reference2ObjectOpenHashMap<>();

	@Override
	public <T> void setInput(IIngredientType<T> ingredientType, T input) {
		setInputs(ingredientType, Collections.singletonList(input));
	}

	@Override
	@Deprecated
	public <T> void setInput(Class<? extends T> ingredientClass, T input) {
		setInput(Internal.getIngredientRegistry().getIngredientType(ingredientClass), input);
	}

	@Override
	public <T> void setInputs(IIngredientType<T> ingredientType, List<T> inputs) {
		IIngredientHelper<T> ingredientHelper = Internal.getIngredientRegistry().getIngredientHelper(ingredientType);
		List<Object> slots = new ArrayList<>(inputs.size());
		for (T input : inputs) {
			List<T> expanded = ingredientHelper.expandSubtypes(Collections.singletonList(input));
			slots.add(expanded.size() == 1 ? expanded.get(0) : expanded);
		}
		this.inputs.put(ingredientType, slots);
	}

	@Override
	@Deprecated
	public <T> void setInputs(Class<? extends T> ingredientClass, List<T> input) {
		setInputs(Internal.getIngredientRegistry().getIngredientType(ingredientClass), input);
	}

	@Override
	public <T> void setInputLists(IIngredientType<T> ingredientType, List<List<T>> inputs) {
		IIngredientHelper<T> ingredientHelper = Internal.getIngredientRegistry().getIngredientHelper(ingredientType);
		List<Object> slots = new ArrayList<>(inputs.size());
		for (List<T> input : inputs) {
			List<T> expanded = ingredientHelper.expandSubtypes(input);
			slots.add(expanded.size() == 1 ? expanded.get(0) : expanded);
		}
		this.inputs.put(ingredientType, slots);
	}

	@Override
	@Deprecated
	public <T> void setInputLists(Class<? extends T> ingredientClass, List<List<T>> inputs) {
		setInputLists(Internal.getIngredientRegistry().getIngredientType(ingredientClass), inputs);
	}

	@Override
	public <T> void setOutput(IIngredientType<T> ingredientType, T output) {
		setOutputs(ingredientType, Collections.singletonList(output));
	}

	@Override
	@Deprecated
	public <T> void setOutput(Class<? extends T> ingredientClass, T output) {
		setOutput(Internal.getIngredientRegistry().getIngredientType(ingredientClass), output);
	}

	@Override
	public <T> void setOutputs(IIngredientType<T> ingredientType, List<T> outputs) {
		IIngredientHelper<T> ingredientHelper = Internal.getIngredientRegistry().getIngredientHelper(ingredientType);
		List<Object> slots = new ArrayList<>(outputs.size());
		for (T output : outputs) {
			List<T> expanded = ingredientHelper.expandSubtypes(Collections.singletonList(output));
			slots.add(expanded.size() == 1 ? expanded.get(0) : expanded);
		}
		this.outputs.put(ingredientType, slots);
	}

	@Override
	@Deprecated
	public <T> void setOutputs(Class<? extends T> ingredientClass, List<T> outputs) {
		setOutputs(Internal.getIngredientRegistry().getIngredientType(ingredientClass), outputs);
	}

	@Override
	public <T> void setOutputLists(IIngredientType<T> ingredientType, List<List<T>> outputs) {
		IIngredientHelper<T> ingredientHelper = Internal.getIngredientRegistry().getIngredientHelper(ingredientType);
		List<Object> slots = new ArrayList<>(outputs.size());
		for (List<T> output : outputs) {
			List<T> expanded = ingredientHelper.expandSubtypes(output);
			slots.add(expanded.size() == 1 ? expanded.get(0) : expanded);
		}
		this.outputs.put(ingredientType, slots);
	}

	@Override
	@Deprecated
	public <T> void setOutputLists(Class<? extends T> ingredientClass, List<List<T>> outputs) {
		setOutputLists(Internal.getIngredientRegistry().getIngredientType(ingredientClass), outputs);
	}

	@Override
	public <T> List<List<T>> getInputs(IIngredientType<T> ingredientType) {
		List<Object> slots = this.inputs.get(ingredientType);
		return slots == null ? Collections.emptyList() : new SlotView<>(slots);
	}

	@Override
	@Deprecated
	public <T> List<List<T>> getInputs(Class<? extends T> ingredientClass) {
		return getInputs(Internal.getIngredientRegistry().getIngredientType(ingredientClass));
	}

	@Override
	public <T> List<List<T>> getOutputs(IIngredientType<T> ingredientType) {
		List<Object> slots = this.outputs.get(ingredientType);
		return slots == null ? Collections.emptyList() : new SlotView<>(slots);
	}

	@Override
	@Deprecated
	public <T> List<List<T>> getOutputs(Class<? extends T> ingredientClass) {
		return getOutputs(Internal.getIngredientRegistry().getIngredientType(ingredientClass));
	}

	public Map<IIngredientType, List> getInputIngredients() {
		Map<IIngredientType, List> result = new Reference2ObjectOpenHashMap<>();
		for (Map.Entry<IIngredientType, List<Object>> entry : inputs.entrySet()) {
			result.put(entry.getKey(), flatten(entry.getValue()));
		}
		return result;
	}

	public Map<IIngredientType, List> getOutputIngredients() {
		Map<IIngredientType, List> result = new Reference2ObjectOpenHashMap<>();
		for (Map.Entry<IIngredientType, List<Object>> entry : outputs.entrySet()) {
			result.put(entry.getKey(), flatten(entry.getValue()));
		}
		return result;
	}

	private static List<Object> flatten(List<Object> slots) {
		List<Object> flat = new ArrayList<>(slots.size());
		for (Object slot : slots) {
			if (slot instanceof List) {
				flat.addAll((List<?>) slot);
			} else {
				flat.add(slot);
			}
		}
		return flat;
	}

	private static class SlotView<T> extends AbstractList<List<T>> {
		private final List<Object> slots;

		SlotView(List<Object> slots) {
			this.slots = slots;
		}

		@Override
		@SuppressWarnings("unchecked")
		public List<T> get(int index) {
			Object slot = slots.get(index);
			return slot instanceof List ? (List<T>) slot : Collections.singletonList((T) slot);
		}

		@Override
		public int size() {
			return slots.size();
		}
	}
}

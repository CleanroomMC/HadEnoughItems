package mezz.jei.ingredients;

import java.util.Collection;

import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.suffixtree.GeneralizedSuffixTree;

class PrefixedSearchTree {
	private final String id;
	private final GeneralizedSuffixTree tree;
	private final IStringsGetter stringsGetter;
	private final IModeGetter modeGetter;

	public PrefixedSearchTree(String id, GeneralizedSuffixTree tree, IStringsGetter stringsGetter, IModeGetter modeGetter) {
		this.id = id;
		this.tree = tree;
		this.stringsGetter = stringsGetter;
		this.modeGetter = modeGetter;
	}

	public String getId() {
		return id;
	}

	public GeneralizedSuffixTree getTree() {
		return tree;
	}

	public IStringsGetter getStringsGetter() {
		return stringsGetter;
	}

	public Config.SearchMode getMode() {
		return modeGetter.getMode();
	}

	@FunctionalInterface
	interface IStringsGetter {
		Collection<String> getStrings(IIngredientListElement<?> element);
	}

	@FunctionalInterface
	interface IModeGetter {
		Config.SearchMode getMode();
	}
}

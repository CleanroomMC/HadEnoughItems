package mezz.jei.search;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class CombinedSearchables implements ISearchable {
	private final List<ISearchable> searchable = new ArrayList<>();

	@Override
	public IntSet search(String word) {
		IntSet searchResults = new IntOpenHashSet(0);
		for (ISearchable searchTree : searchable) {
			IntSet search = searchTree.search(word);
			searchResults = union(searchResults, search);
		}
		return searchResults;
	}

	public void addSearchable(ISearchable searchTree) {
		this.searchable.add(searchTree);
	}

	/**
	 * Efficiently get all the elements from both sets.
	 * Note that this implementation will alter the original sets.
	 */
	private static IntSet union(IntSet set1, IntSet set2) {
		if (set1.size() > set2.size()) {
			set1.addAll(set2);
			return set1;
		} else {
			set2.addAll(set1);
			return set2;
		}
	}
}

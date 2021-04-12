package mezz.jei.suffixtree;

import it.unimi.dsi.fastutil.ints.IntSet;

import java.io.Serializable;

public interface ISearchTree extends Serializable {
	IntSet search(String word);
}

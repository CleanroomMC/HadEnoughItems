package mezz.jei.search;

import mezz.jei.api.search.ISearchIndex;

import java.io.PrintWriter;

interface IPrintableSearchIndex<T> extends ISearchIndex<T> {

	void printTree(PrintWriter out, boolean includeSuffixLinks);
}

package mezz.jei.collect;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ListMultiMap<K, V> extends MultiMap<K, V, List<V>> {
	public ListMultiMap() {
		this(ObjectArrayList::new);
	}

	public ListMultiMap(Supplier<List<V>> collectionSupplier) {
		super(collectionSupplier);
	}

	public ListMultiMap(Map<K, List<V>> map, Supplier<List<V>> collectionSupplier) {
		super(map, collectionSupplier);
	}
}

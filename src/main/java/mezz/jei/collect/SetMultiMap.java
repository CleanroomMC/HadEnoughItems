package mezz.jei.collect;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class SetMultiMap<K, V> extends MultiMap<K, V, Set<V>> {
	public SetMultiMap() {
		this(ObjectOpenHashSet::new);
	}

	public SetMultiMap(Supplier<Set<V>> collectionSupplier) {
		super(collectionSupplier);
	}

	public SetMultiMap(Map<K, Set<V>> map, Supplier<Set<V>> collectionSupplier) {
		super(map, collectionSupplier);
	}
}

package mezz.jei.collect;

import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import it.unimi.dsi.fastutil.chars.CharSet;
import it.unimi.dsi.fastutil.chars.CharSets;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.Map;
import java.util.Objects;

public class Char2ObjectSingletonMap<V> implements Char2ObjectMap<V> {

    private final char key;
    private final V value;

    public Char2ObjectSingletonMap(char key, V value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public ObjectSet<Map.Entry<Character, V>> entrySet() {
        return ObjectSets.singleton(new SingletonEntry());
    }

    @Override
    public ObjectSet<Entry<V>> char2ObjectEntrySet() {
        return ObjectSets.singleton(new SingletonEntry());
    }

    @Override
    public CharSet keySet() {
        return CharSets.singleton(this.key);
    }

    @Override
    public ObjectCollection<V> values() {
        return ObjectLists.singleton(this.value);
    }

    @Override
    public V put(char key, V value) {
        throw new UnsupportedOperationException("Unmodifiable");
    }

    @Override
    public V get(char key) {
        return this.containsKey(key) ? this.value : null;
    }

    @Override
    public V remove(char key) {
        throw new UnsupportedOperationException("Unmodifiable");
    }

    @Override
    public boolean containsKey(char key) {
        return this.key == key;
    }

    @Override
    public void defaultReturnValue(V rv) {
        throw new UnsupportedOperationException("Unmodifiable");
    }

    @Override
    public V defaultReturnValue() {
        return null;
    }

    @Override
    public V put(Character key, V value) {
        throw new UnsupportedOperationException("Unmodifiable");
    }

    @Override
    public V get(Object key) {
        return key instanceof Character ? this.containsKey((char) key) ? this.value : null : null;
    }

    @Override
    public boolean containsKey(Object key) {
        return key instanceof Character && this.containsKey((char) key);
    }

    @Override
    public boolean containsValue(Object value) {
        return Objects.equals(this.value, value);
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException("Unmodifiable");
    }

    @Override
    public void putAll(Map<? extends Character, ? extends V> m) {
        throw new UnsupportedOperationException("Unmodifiable");
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Unmodifiable");
    }

    protected class SingletonEntry implements Char2ObjectMap.Entry<V>, Map.Entry<Character, V> {

        @Override
        @Deprecated
        public Character getKey() {
            return Char2ObjectSingletonMap.this.key;
        }

        @Override
        public V getValue() {
            return Char2ObjectSingletonMap.this.value;
        }

        @Override
        public char getCharKey() {
            return Char2ObjectSingletonMap.this.key;
        }

        @Override
        public V setValue(final V value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof Map.Entry)) return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            if (e.getKey() == null || !(e.getKey() instanceof Character)) return false;
            return ((Char2ObjectSingletonMap.this.key) == (((((Character) (e.getKey())).charValue())))) && ((Char2ObjectSingletonMap.this.value) == null ? ((e.getValue())) == null : (Char2ObjectSingletonMap.this.value).equals((e.getValue())));
        }

        @Override
        public int hashCode() {
            return (Char2ObjectSingletonMap.this.key) ^ ((Char2ObjectSingletonMap.this.value) == null ? 0 : (Char2ObjectSingletonMap.this.value).hashCode());
        }

        @Override
        public String toString() {
            return Char2ObjectSingletonMap.this.key + "->" + Char2ObjectSingletonMap.this.value;
        }

    }

}

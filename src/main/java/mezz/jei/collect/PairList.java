package mezz.jei.collect;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

public class PairList<T> extends AbstractList<T> {

    public final T a;
    public final T b;

    public PairList(T a, T b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public T get(int index) {
        if (index == 0) return a;
        if (index == 1) return b;
        throw new IndexOutOfBoundsException("Index: " + index + ", Size: 2");
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean contains(Object o) {
        return Objects.equals(o, a) || Objects.equals(o, b);
    }

    @Override
    public int indexOf(Object o) {
        if (Objects.equals(o, a)) return 0;
        if (Objects.equals(o, b)) return 1;
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        if (Objects.equals(o, b)) return 1;
        if (Objects.equals(o, a)) return 0;
        return -1;
    }

    @Override
    public Object[] toArray() {
        return new Object[]{ a, b } ;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> E[] toArray(E[] arr) {
        if (arr.length < 2) {
            arr = Arrays.copyOf(arr, 2);
        }
        arr[0] = (E) a;
        arr[1] = (E) b;
        if (arr.length > 2) {
            arr[2] = null;
        }
        return arr;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private int cursor = 0;

            @Override
            public boolean hasNext() {
                return cursor < 2;
            }

            @Override
            public T next() {
                switch (cursor++) {
                    case 0: return a;
                    case 1: return b;
                    default: throw new NoSuchElementException();
                }
            }
        };
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        action.accept(a);
        action.accept(b);
    }

}

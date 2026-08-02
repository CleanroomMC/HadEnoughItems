/*
 * Copyright (c) mezz
 *
 * Licensed under the MIT License. See LICENSE.txt in this package for details.
 *
 * Vendored from net.mezzdev:baked-substring-index:0.1.0
 * https://github.com/mezz/baked-substring-index
 *
 * Changes from upstream:
 * - Java 8 API compatibility ({@code toArray(new String[0])} instead of {@code toArray(String[]::new)},
 *   explicit type argument on the anonymous {@link Iterator})
 * - jspecify annotations replaced with javax.annotation
 * - Identity sets use fastutil's ReferenceOpenHashSet instead of IdentityHashMap
 */
package mezz.jei.search.bakedsubstring;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import javax.annotation.Nullable;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable substring index for data that is built once and queried many times.
 * <p>
 * The built index cannot be mutated. If the source data changes, create a new builder and build a replacement index.
 * This makes the index a good fit for data that is stable during lookup, even if the application occasionally rebuilds
 * it after a batch of changes.
 * <p>
 * The index stores each key by its unique short character fragments. Searches for tokens up to three UTF-16 code units
 * use an exact fragment lookup. Longer searches intersect unique three-character query fragments, shortest posting list
 * first, and then verify candidates with {@link String#contains(CharSequence)} to preserve exact substring semantics.
 * <p>
 * Values are de-duplicated by identity, not by {@link Object#equals(Object)}. If the same value object is indexed under
 * multiple matching keys, a search result includes it once. Equal but distinct value objects are returned separately.
 *
 * @param <T> the value type stored in the index
 */
public final class BakedSubstringIndex<T> {
    private static final int[] NO_ENTRIES = new int[0];

    private final String[] keys;
    private final Object[] values;
    private final Long2ObjectOpenHashMap<int[]> entriesByGram;
    private final boolean deduplicateResults;

    private BakedSubstringIndex(
            String[] keys,
            Object[] values,
            Long2ObjectOpenHashMap<int[]> entriesByGram,
            boolean deduplicateResults
    ) {
        this.keys = keys;
        this.values = values;
        this.entriesByGram = entriesByGram;
        this.deduplicateResults = deduplicateResults;
    }

    /**
     * Creates a builder for a baked substring index.
     *
     * @param <T> the value type stored in the index
     * @return a new builder
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Finds all values whose indexed key contains {@code token}.
     * <p>
     * Empty query tokens produce an empty collection.
     *
     * @param token the substring to search for
     * @return an immutable collection of matching values
     */
    public Collection<T> getSearchResults(String token) {
        Objects.requireNonNull(token, "token");
        if (token.isEmpty()) {
            return Collections.emptyList();
        }

        int[] candidateEntries = candidateEntries(token);
        if (candidateEntries == null || candidateEntries.length == 0) {
            return Collections.emptyList();
        }

        boolean verifyCandidates = token.length() > 3;
        if (deduplicateResults) {
            return deduplicatedResults(token, candidateEntries, verifyCandidates);
        }

        CandidateResultCollection results = new CandidateResultCollection(token, candidateEntries, verifyCandidates);
        if (results.isEmpty()) {
            return Collections.emptyList();
        }
        return results;
    }

    /**
     * Returns all unique value identities stored in the index.
     *
     * @return an immutable collection containing every indexed value identity once
     */
    public Collection<T> getAllElements() {
        if (values.length == 0) {
            return Collections.emptyList();
        }
        if (!deduplicateResults) {
            List<T> results = new ArrayList<>(values.length);
            for (Object value : values) {
                results.add(BakedSubstringIndex.value(value));
            }
            return Collections.unmodifiableList(results);
        }

        Set<T> results = new ReferenceOpenHashSet<>();
        for (Object value : values) {
            results.add(BakedSubstringIndex.value(value));
        }
        return Collections.unmodifiableSet(results);
    }

    /**
     * @return the number of indexed key/value entries
     */
    public int size() {
        return keys.length;
    }

    /**
     * @return the number of distinct character fragments in the index
     */
    public int gramCount() {
        return entriesByGram.size();
    }

    private Collection<T> deduplicatedResults(String token, int[] candidateEntries, boolean verifyCandidates) {
        Set<T> results = new ReferenceOpenHashSet<>();
        for (int entryIndex : candidateEntries) {
            if (!verifyCandidates || keys[entryIndex].contains(token)) {
                results.add(valueAt(entryIndex));
            }
        }
        if (results.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableSet(results);
    }

    @Nullable
    private int[] candidateEntries(String token) {
        int tokenLength = token.length();
        if (tokenLength <= 3) {
            return entriesByGram.get(encodeGram(token, 0, tokenLength));
        }

        int maxGramCount = tokenLength - 2;
        long[] grams = new long[maxGramCount];
        int[][] postings = new int[maxGramCount][];
        int gramCount = 0;
        for (int i = 0; i <= tokenLength - 3; i++) {
            long gram = encodeGram(token, i, 3);
            if (contains(grams, gramCount, gram)) {
                continue;
            }

            int[] entries = entriesByGram.get(gram);
            if (entries == null) {
                return null;
            }
            int insertAt = gramCount;
            while (insertAt > 0 && postings[insertAt - 1].length > entries.length) {
                grams[insertAt] = grams[insertAt - 1];
                postings[insertAt] = postings[insertAt - 1];
                insertAt--;
            }
            grams[insertAt] = gram;
            postings[insertAt] = entries;
            gramCount++;
        }
        if (gramCount == 0) {
            return NO_ENTRIES;
        }
        if (gramCount == 1) {
            return postings[0];
        }

        return intersectPostings(postings, gramCount);
    }

    private T valueAt(int index) {
        return value(values[index]);
    }

    @SuppressWarnings("unchecked")
    private static <T> T value(Object value) {
        return (T) value;
    }

    private final class CandidateResultCollection extends AbstractCollection<T> {
        private final String token;
        private final int[] candidateEntries;
        private final boolean verifyCandidates;
        private volatile int size = -1;

        private CandidateResultCollection(String token, int[] candidateEntries, boolean verifyCandidates) {
            this.token = token;
            this.candidateEntries = candidateEntries;
            this.verifyCandidates = verifyCandidates;
        }

        @Override
        public Iterator<T> iterator() {
            return new Iterator<T>() {
                private int nextCandidateIndex = findNextCandidate(0);

                @Override
                public boolean hasNext() {
                    return nextCandidateIndex >= 0;
                }

                @Override
                public T next() {
                    if (nextCandidateIndex < 0) {
                        throw new NoSuchElementException();
                    }
                    int entryIndex = candidateEntries[nextCandidateIndex];
                    nextCandidateIndex = findNextCandidate(nextCandidateIndex + 1);
                    return valueAt(entryIndex);
                }
            };
        }

        @Override
        public int size() {
            if (size < 0) {
                int count = 0;
                for (int entryIndex : candidateEntries) {
                    if (matches(entryIndex)) {
                        count++;
                    }
                }
                size = count;
            }
            return size;
        }

        private int findNextCandidate(int startIndex) {
            for (int i = startIndex; i < candidateEntries.length; i++) {
                if (matches(candidateEntries[i])) {
                    return i;
                }
            }
            return -1;
        }

        private boolean matches(int entryIndex) {
            return !verifyCandidates || keys[entryIndex].contains(token);
        }
    }

    /**
     * Builder for {@link BakedSubstringIndex}.
     *
     * @param <T> the value type stored in the index
     */
    public static final class Builder<T> {
        private final List<String> keys = new ArrayList<>();
        private final List<T> values = new ArrayList<>();

        private Builder() {
        }

        /**
         * Adds a key/value pair to the index being built.
         * <p>
         * Empty keys are allowed but cannot match any non-empty search token.
         *
         * @param key   the string to search
         * @param value the value to return when {@code key} matches
         * @return this builder
         */
        public Builder<T> put(String key, T value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            keys.add(key);
            values.add(value);
            return this;
        }

        /**
         * Builds an immutable substring index from the current builder contents.
         *
         * @return the baked index
         */
        public BakedSubstringIndex<T> build() {
            String[] keyArray = keys.toArray(new String[0]);
            Object[] valueArray = values.toArray();
            Long2ObjectOpenHashMap<IntArrayList> mutableEntriesByGram = new Long2ObjectOpenHashMap<>();
            Long2IntOpenHashMap lastEntryByGram = new Long2IntOpenHashMap();
            lastEntryByGram.defaultReturnValue(-1);
            Set<T> seenValues = new ReferenceOpenHashSet<>();
            boolean deduplicateResults = false;

            for (int entryIndex = 0; entryIndex < keyArray.length; entryIndex++) {
                String key = keyArray[entryIndex];
                addGrams(mutableEntriesByGram, lastEntryByGram, key, entryIndex);
                if (!seenValues.add(values.get(entryIndex))) {
                    deduplicateResults = true;
                }
            }

            Long2ObjectOpenHashMap<int[]> entriesByGram = new Long2ObjectOpenHashMap<>(mutableEntriesByGram.size());
            for (Long2ObjectMap.Entry<IntArrayList> entry : mutableEntriesByGram.long2ObjectEntrySet()) {
                entriesByGram.put(entry.getLongKey(), entry.getValue().toIntArray());
            }
            return new BakedSubstringIndex<>(keyArray, valueArray, entriesByGram, deduplicateResults);
        }

        private static void addGrams(
                Long2ObjectOpenHashMap<IntArrayList> entriesByGram,
                Long2IntOpenHashMap lastEntryByGram,
                String key,
                int entryIndex
        ) {
            int keyLength = key.length();
            for (int gramLength = 1; gramLength <= 3 && gramLength <= keyLength; gramLength++) {
                for (int i = 0; i <= keyLength - gramLength; i++) {
                    long gram = encodeGram(key, i, gramLength);
                    if (lastEntryByGram.get(gram) == entryIndex) {
                        continue;
                    }
                    IntArrayList entries = entriesByGram.get(gram);
                    if (entries == null) {
                        entries = new IntArrayList();
                        entriesByGram.put(gram, entries);
                    }
                    entries.add(entryIndex);
                    lastEntryByGram.put(gram, entryIndex);
                }
            }
        }
    }

    private static long encodeGram(String string, int offset, int length) {
        long gram = (long) length << 48;
        for (int i = 0; i < length; i++) {
            gram |= (long) string.charAt(offset + i) << ((2 - i) * 16);
        }
        return gram;
    }

    private static boolean contains(long[] values, int length, long value) {
        for (int i = 0; i < length; i++) {
            if (values[i] == value) {
                return true;
            }
        }
        return false;
    }

    private static int[] intersectPostings(int[][] postings, int postingCount) {
        int[] current = postings[0];
        int currentLength = current.length;
        for (int i = 1; i < postingCount; i++) {
            int[] next = postings[i];
            int[] output = new int[Math.min(currentLength, next.length)];
            currentLength = intersect(current, currentLength, next, output);
            if (currentLength == 0) {
                return NO_ENTRIES;
            }
            current = output;
        }
        if (currentLength == current.length) {
            return current;
        }

        int[] output = new int[currentLength];
        System.arraycopy(current, 0, output, 0, currentLength);
        return output;
    }

    private static int intersect(int[] left, int leftLength, int[] right, int[] output) {
        int leftIndex = 0;
        int rightIndex = 0;
        int outputIndex = 0;
        while (leftIndex < leftLength && rightIndex < right.length) {
            int leftValue = left[leftIndex];
            int rightValue = right[rightIndex];
            if (leftValue == rightValue) {
                output[outputIndex++] = leftValue;
                leftIndex++;
                rightIndex++;
            } else if (leftValue < rightValue) {
                leftIndex++;
            } else {
                rightIndex++;
            }
        }
        return outputIndex;
    }

    @Override
    public String toString() {
        return "BakedSubstringIndex{entries=" + keys.length + ", grams=" + entriesByGram.size() + '}';
    }
}

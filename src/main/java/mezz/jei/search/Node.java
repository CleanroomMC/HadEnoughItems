/*
 * Copyright 2012 Alessandro Bahgat Shehata
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package mezz.jei.search;

import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectMaps;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mezz.jei.collect.Char2ObjectSingletonMap;
import mezz.jei.util.Substring;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Represents a node (and edge for optimization purposes) of the generalized suffix tree graph
 *
 * @see GeneralizedSuffixTree
 */
public class Node<T> extends Substring {

    /**
     * The payload array used to store the data (indexes) associated with this node.
     * In this case, it is used to store all property indexes.
     */
    private Collection<T> data;

    /**
     * The set of edges starting from this node
     */
    private Char2ObjectMap<Node<T>> edges;

    /**
     * The suffix link as described in Ukkonen's paper.
     * if str is the string denoted by the path from the root to this, this.suffix
     * is the node denoted by the path that corresponds to str without the first char.
     */
    @Nullable
    private Node<T> suffix;

    Node(Substring string) {
        super(string);
        this.data = Collections.emptyList();
        this.edges = Char2ObjectMaps.emptyMap();
        this.suffix = null;
    }

    /**
     * Gets data from the payload of both this node and its children, the string representation
     * of the path to this node is a substring of the one of the children nodes.
     */
    void getData(Collection<T> collection) {
        if (!data.isEmpty()) {
            collection.addAll(data);
        }
        for (Node<T> e : edges.values()) {
            e.getData(collection);
        }
    }

    /**
     * Adds the given <tt>index</tt> to the set of indexes associated with <tt>this</tt>
     * returns false if this node already contains the ref
     */
    boolean addRef(T index) {
        if (contains(index)) {
            return false;
        }
        addValue(index);
        Node<T> iter = this.suffix;
        while (iter != null) {
            if (!iter.contains(index)) {
                iter.addValue(index);
                iter = iter.suffix;
            } else {
                break;
            }
        }
        return true;
    }

    /**
     * Tests whether a node contains a reference to the given index.
     *
     * @param index the index to look for
     * @return true <tt>this</tt> contains a reference to index
     */
    protected boolean contains(T index) {
        return data.contains(index);
    }

    protected void addEdge(Node<T> e) {
        if (edges.isEmpty()) {
            edges = new Char2ObjectSingletonMap<>(e.charAt(0), e);
        } else if (edges.size() == 1) {
            Char2ObjectMap<Node<T>> newEdges = new Char2ObjectArrayMap<>(2);
            newEdges.putAll(edges);
            newEdges.put(e.charAt(0), e);
            edges = newEdges;
        } else if (edges.size() == 7) {
            Char2ObjectMap<Node<T>> newEdges = new Char2ObjectOpenHashMap<>(edges);
            newEdges.put(e.charAt(0), e);
            edges = newEdges;
        } else {
            edges.put(e.charAt(0), e);
        }
    }

    @Nullable
    Node<T> getEdge(char ch) {
        return edges.get(ch);
    }

    @Nullable
    Node<T> getEdge(Substring substring) {
        if (substring.isEmpty()) {
            return null;
        }
        return edges.get(substring.charAt(0));
    }

    @Nullable
    Node<T> getSuffix() {
        return suffix;
    }

    void setSuffix(Node<T> suffix) {
        this.suffix = suffix;
    }

    // TODO: fixed-lengthed list classes
    protected void addValue(T index) {
        switch (data.size()) {
            case 0:
                data = Collections.singletonList(index);
                break;
            case 1:
                T first = data.iterator().next();
                data = new ArrayList<>(2);
                data.add(first);
                data.add(index);
                break;
            case 8:
                Collection<T> newData = new ReferenceOpenHashSet<>();
                newData.addAll(data);
                newData.add(index);
                data = newData;
                break;
            default:
                data.add(index);
        }
    }

    @Override
    public String toString() {
        return "Node: size:" + (data.size()) + " Edges: " + edges;
    }

    public IntSummaryStatistics nodeSizeStats() {
        return nodeSizes().summaryStatistics();
    }

    private IntStream nodeSizes() {
        return IntStream.concat(IntStream.of(data.size()), edges.values().stream().flatMapToInt(Node::nodeSizes));
    }

    public String nodeEdgeStats() {
        IntSummaryStatistics edgeCounts = nodeEdgeCounts().summaryStatistics();
        IntSummaryStatistics edgeLengths = nodeEdgeLengths().summaryStatistics();
        return "Edge counts: " + edgeCounts + "\nEdge lengths: " + edgeLengths;
    }

    private IntStream nodeEdgeCounts() {
        return IntStream.concat(IntStream.of(edges.size()), edges.values().stream().flatMapToInt(Node::nodeEdgeCounts));
    }

    private IntStream nodeEdgeLengths() {
        return IntStream.concat(edges.values().stream().mapToInt(Node::length), edges.values().stream().flatMapToInt(Node::nodeEdgeLengths));
    }

    public void printTree(PrintWriter out, boolean includeSuffixLinks) {
        out.println("digraph {");
        out.println("\trankdir = LR;");
        out.println("\tordering = out;");
        out.println("\tedge [arrowsize=0.4,fontsize=10]");
        out.println("\t" + nodeId(this) + " [label=\"\",style=filled,fillcolor=lightgrey,shape=circle,width=.1,height=.1];");
        out.println("//------leaves------");
        printLeaves(out);
        out.println("//------internal nodes------");
        printInternalNodes(this, out);
        out.println("//------edges------");
        printEdges(out);
        if (includeSuffixLinks) {
            out.println("//------suffix links------");
            printSLinks(out);
        }
        out.println("}");
    }

    private void printLeaves(PrintWriter out) {
        if (edges.isEmpty()) {
            out.println("\t" + nodeId(this) + " [label=\"" + data + "\",shape=point,style=filled,fillcolor=lightgrey,shape=circle,width=.07,height=.07]");
        } else {
            for (Node<T> edge : edges.values()) {
                edge.printLeaves(out);
            }
        }
    }

    private void printInternalNodes(Node<T> root, PrintWriter out) {
        if (this != root && !edges.isEmpty()) {
            out.println("\t" + nodeId(this) + " [label=\"" + data + "\",style=filled,fillcolor=lightgrey,shape=circle,width=.07,height=.07]");
        }
        for (Node<T> edge : edges.values()) {
            edge.printInternalNodes(root, out);
        }
    }

    private void printEdges(PrintWriter out) {
        for (Node<T> child : edges.values()) {
            out.println("\t" + nodeId(this) + " -> " + nodeId(child) + " [label=\"" + child + "\",weight=10]");
            child.printEdges(out);
        }
    }

    private void printSLinks(PrintWriter out) {
        if (suffix != null) {
            out.println("\t" + nodeId(this) + " -> " + nodeId(suffix) + " [label=\"\",weight=0,style=dotted]");
        }
        for (Node<T> edge : edges.values()) {
            edge.printSLinks(out);
        }
    }

    private static <T> String nodeId(Node<T> node) {
        return "node" + Integer.toHexString(node.hashCode()).toUpperCase();
    }

    /**
     * The root node can have a lot of values added to it because so many suffix links point to it.
     * The values are never read from here though.
     * This class makes sure we don't accumulate a ton of useless values in the root node.
     */
    public static class Root<T> extends Node<T> {

        public Root() {
            super(new Substring(""));
        }

        @Override
        protected boolean contains(T value) {
            return true;
        }

        @Override
        protected void addValue(T index) { }

    }

}

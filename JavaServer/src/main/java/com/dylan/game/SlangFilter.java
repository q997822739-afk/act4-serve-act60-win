package com.dylan.game;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class SlangFilter {

    public static final SlangFilter EMPTY = new SlangFilter(new Node(), 0);

    private static final class Node {

        private final Map<Character, Node> children = new HashMap<>();

        private boolean end;
    }

    private final Node root;
    private final int size;

    private SlangFilter(Node root, int size) {
        this.root = root;
        this.size = size;
    }

    public static SlangFilter of(Collection<String> words) {
        if (words == null || words.isEmpty()) {
            return EMPTY;
        }
        Node root = new Node();
        int size = 0;
        for (String word : words) {
            if (word == null || word.isEmpty()) {
                continue;
            }
            Node node = root;
            for (int i = 0; i < word.length(); i++) {
                node = node.children.computeIfAbsent(word.charAt(i), k -> new Node());
            }
            if (!node.end) {
                node.end = true;
                size++;
            }
        }
        return size == 0 ? EMPTY : new SlangFilter(root, size);
    }

    public int matchAt(String text, int from) {
        if (text == null || from < 0 || from >= text.length()) {
            return 0;
        }
        Node node = root;
        for (int i = from; i < text.length(); i++) {
            node = node.children.get(text.charAt(i));
            if (node == null) {
                return 0;
            }
            if (node.end) {
                return i - from + 1;
            }
        }
        return 0;
    }

    public boolean has(String text) {
        if (text == null || text.isEmpty() || size == 0) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (matchAt(text, i) > 0) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return size;
    }
}

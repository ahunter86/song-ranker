package com.songranker.ui.util;

import java.util.List;

/** Drag-and-drop reorder math, shared by every screen with a draggable list. */
public final class ReorderUtil {

    private ReorderUtil() {
    }

    /**
     * Final index of an item dragged from {@code from} and dropped "before" the row at
     * {@code beforeIndex} (the green insertion bar sits above that row). {@code beforeIndex ==
     * size} means dropped below the last row, i.e. moved to the end.
     */
    public static int finalIndexWhenDroppedBefore(int from, int beforeIndex, int size) {
        int target = Math.max(0, Math.min(beforeIndex, size));
        int index = from < target ? target - 1 : target;
        return Math.max(0, Math.min(index, size - 1));
    }

    /** Moves {@code list[from]} so it ends up directly before the row currently at {@code beforeIndex}. */
    public static <T> void moveBefore(List<T> list, int from, int beforeIndex) {
        int finalIndex = finalIndexWhenDroppedBefore(from, beforeIndex, list.size());
        T item = list.remove(from);
        list.add(finalIndex, item);
    }
}

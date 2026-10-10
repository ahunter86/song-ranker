package com.songranker.ui.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReorderUtilTest {

    private static List<String> abcd() {
        return new ArrayList<>(List.of("A", "B", "C", "D"));
    }

    @Test
    void draggingDownLandsDirectlyAboveTheRowItWasDroppedOn() {
        List<String> list = abcd();
        ReorderUtil.moveBefore(list, 0, 2); // A dropped on C: the green bar was above C
        assertEquals(List.of("B", "A", "C", "D"), list);
    }

    @Test
    void draggingUpLandsDirectlyAboveTheRowItWasDroppedOn() {
        List<String> list = abcd();
        ReorderUtil.moveBefore(list, 3, 1); // D dropped on B
        assertEquals(List.of("A", "D", "B", "C"), list);
    }

    @Test
    void droppingBelowTheLastRowMovesToTheEnd() {
        List<String> list = abcd();
        ReorderUtil.moveBefore(list, 0, 4);
        assertEquals(List.of("B", "C", "D", "A"), list);
    }

    @Test
    void droppingOnItselfOrTheRowRightAfterItChangesNothing() {
        List<String> list = abcd();
        ReorderUtil.moveBefore(list, 1, 1);
        ReorderUtil.moveBefore(list, 1, 2);
        assertEquals(abcd(), list);
    }

    @Test
    void finalIndexIsClampedIntoTheList() {
        assertEquals(3, ReorderUtil.finalIndexWhenDroppedBefore(0, 99, 4));
        assertEquals(0, ReorderUtil.finalIndexWhenDroppedBefore(2, -5, 4));
    }
}

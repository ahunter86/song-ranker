package com.songranker.ui.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ShareCardTitlesTest {

    @Test
    void wholeDiscographyIsTitledByTheArtist() {
        assertArrayEquals(new String[]{"Phoebe Bridgers", "Discography ranking"},
                ShareCard.titles("Phoebe Bridgers", "Entire discography"));
    }

    @Test
    void albumIsTitledByTheAlbumWithTheArtistUnderneath() {
        assertArrayEquals(new String[]{"Punisher", "by Phoebe Bridgers"},
                ShareCard.titles("Phoebe Bridgers", "Punisher"));
    }

    @Test
    void playlistIsTitledByItsName() {
        assertArrayEquals(new String[]{"Road Trip", "Playlist ranking"}, ShareCard.titles("Road Trip", "Playlist"));
    }

    @Test
    void missingNamesStillProduceSomethingSensible() {
        assertEquals("My Ranking", ShareCard.titles(null, null)[0]);
        assertArrayEquals(new String[]{"Artist", "My ranking"}, ShareCard.titles("Artist", null));
    }

    @Test
    void thereAreSeveralColorSchemesToCycleThrough() {
        assertNotNull(ShareCard.PALETTES.get(0).name());
        assertEquals(4, ShareCard.PALETTES.size());
    }
}

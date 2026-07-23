package com.macindex.macindex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FavouriteActivityTest {

    @Test
    public void favouriteSnapshotMatchesTheExistingStoredFormat() {
        final String userFavourites = "││{Desktop}│[Macintosh LC III+]│[eMac]";

        assertTrue(FavouriteActivity.isFavourite("Macintosh LC III+", userFavourites));
        assertTrue(FavouriteActivity.isFavourite("eMac", userFavourites));
        assertFalse(FavouriteActivity.isFavourite("Macintosh LC III", userFavourites));
    }
}

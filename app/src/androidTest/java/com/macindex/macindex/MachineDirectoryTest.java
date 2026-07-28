package com.macindex.macindex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class MachineDirectoryTest {

    private static SQLiteDatabase database;

    @BeforeClass
    public static void initDirectory() {
        final Context applicationContext = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        MainActivity.validateOperation(applicationContext);
        database = SQLiteDatabase.openDatabase(
                applicationContext.getDatabasePath("specs.db").getPath(), null,
                SQLiteDatabase.OPEN_READONLY);
    }

    @AfterClass
    public static void closeDirectory() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void indexedMachineNameSearchMatchesDatabase() {
        assertSearchMatchesDatabase("sname", "Macintosh", false);
    }

    @Test
    public void indexedExactNameSearchMatchesDatabase() {
        assertSearchMatchesDatabase("name", "Macintosh LC III+", true);
    }

    @Test
    public void indexedExactModelSearchMatchesDatabase() {
        assertSearchMatchesDatabase("smodel", "M1640", true);
    }

    @Test
    public void indexedCategorySearchMatchesDatabase() {
        assertSearchMatchesDatabase("stype", "macbook_pro", false);
    }

    @Test
    public void indexedYearSearchMatchesDatabase() {
        assertSearchMatchesDatabase("syear", "2007", false);
    }

    @Test
    public void indexedProcessorSearchMatchesDatabase() {
        assertSearchMatchesDatabase("sprocessor", "68030", false);
    }

    @Test
    public void indexedIdentifierSearchMatchesDatabase() {
        assertSearchMatchesDatabase("sident", "MacBookPro1,1", true);
    }

    @Test
    public void indexedGestaltSearchMatchesDatabase() {
        assertSearchMatchesDatabase("sgestalt", "23", true);
    }

    @Test
    public void indexedOrderNumberSearchMatchesDatabase() {
        assertSearchMatchesDatabase("sorder", "M0421LL/A", true);
    }

    @Test
    public void indexedEMCSearchMatchesDatabase() {
        assertSearchMatchesDatabase("semc", "2104", true);
    }

    @Test
    public void generatedMainCacheMatchesDirectory() {
        final String[] manufacturers = {"all", "apple68k", "appleppc", "appleintel", "applearm"};
        final String[] filters = {"names", "processors", "years"};
        for (String thisManufacturer : manufacturers) {
            for (String thisFilter : filters) {
                final String[][] filterString = MainActivity.getMachineHelper()
                        .getFilterString(thisFilter);
                final int[][] cachedPositions = MainActivity.getMachineHelper()
                        .getMainPositions(thisFilter, thisManufacturer);
                assertEquals(filterString[1].length, cachedPositions.length);
                for (int i = 0; i < filterString[1].length; i++) {
                    assertArrayEquals(MainActivity.getMachineHelper().searchHelper(
                                    filterString[0][0], filterString[1][i],
                                    thisManufacturer, false, true),
                            cachedPositions[i]);
                }
            }
        }
    }

    private void assertSearchMatchesDatabase(final String columnName, final String searchInput,
                                             final boolean isExactMatch) {
        final List<String> expectedNames = new ArrayList<>();
        try (Cursor tablesCursor = database.query("sqlite_master", new String[]{"name"},
                "type = ? AND name NOT LIKE ? AND name NOT IN (?, ?, ?)",
                new String[]{"table", "android_%", "machine_directory", "main_filter",
                        "main_cache"},
                null, null, null)) {
            while (tablesCursor.moveToNext()) {
                final String tableName = tablesCursor.getString(0);
                try (Cursor resultCursor = database.query(tableName,
                        new String[]{"name", columnName}, columnName + " LIKE ?",
                        new String[]{"%" + searchInput + "%"}, null, null, null)) {
                    while (resultCursor.moveToNext()) {
                        final String rawValue = resultCursor.getString(1);
                        if (!isExactMatch || isExactMatch(rawValue, searchInput)) {
                            expectedNames.add(resultCursor.getString(0));
                        }
                    }
                }
            }
        }

        final List<String> indexedNames = new ArrayList<>();
        final int[] indexedResults = MainActivity.getMachineHelper().searchHelper(
                columnName, searchInput, "all", isExactMatch, false);
        for (int machineID : indexedResults) {
            indexedNames.add(MainActivity.getMachineHelper().getName(machineID));
        }
        Collections.sort(expectedNames);
        Collections.sort(indexedNames);
        assertEquals(expectedNames, indexedNames);
    }

    private boolean isExactMatch(final String rawValue, final String searchInput) {
        if (rawValue == null) {
            return false;
        }
        for (String thisValue : rawValue.split("~")) {
            if (thisValue.equalsIgnoreCase(searchInput)) {
                return true;
            }
        }
        return false;
    }
}

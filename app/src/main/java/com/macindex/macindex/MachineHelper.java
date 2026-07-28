package com.macindex.macindex;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/*
 * MacIndex MachineHelper.
 * Helps with ID-based flexible database query.
 * First built May 12, 2020.
 *
 * Category name and description was removed since Ver. 4.0
 * Category start and end was removed since Ver. 4.0
 * Based on searching since Ver. 4.0
 * Total configuration removed since Ver. 4.5
 * Config count was removed since Ver. 4.5
 * convertToDatabaseCategoryID was removed since Ver. 4.5
 * Find by Config was removed since Ver. 4.5
 * Category Individual Cursor was removed since Ver. 4.5
 * Changed Cursor behavior since Ver. 4.5
 */
class MachineHelper {

    /*
     * Updating categories
     * (1) Update the following array by order.
     * (2) Update the MH manufacturer method.
     * (3) Update the MH filter method.
     * (4) Update String resources.
     * (5) Update the MainActivity.
     * (6) Update the SearchActivity.
     * (7) Make change to the database.
     * (8) Update the following information.
     *
     * Updating filters
     * (1) Update the MH filter method.
     * (2) Update String resources.
     * (3) Update the MainActivity.
     * (4) Update the SearchActivity.
     * (5) Update the following information.
     *
     * Updating columns
     * (1) Update MH to adapt the new column.
     * (2) Update String resources.
     * (3) Update SpecActivity to get the data.
     * (4) Add a new column to every table.
     */

    private static final String[] CATEGORIES_LIST = {"compact_mac", "mac_ii", "mac_lc", "mac_quadra",
            "mac_performa_68k", "mac_centris", "mac_server_68k", "powerbook_68k", "powerbook_duo_68k",
            "power_mac_classic", "mac_performa_ppc", "mac_server_ppc_classic", "powerbook_ppc_classic",
            "powerbook_duo_ppc", "power_mac", "imac_ppc", "emac", "mac_mini_ppc", "mac_server_ppc",
            "xserve_ppc", "powerbook_ppc", "ibook", "mac_pro_intel", "imac_intel", "imac_pro_intel",
            "mac_mini_intel", "xserve_intel", "macbook_pro_intel", "macbook_intel", "macbook_air_intel",
            "mac_pro_arm", "imac_arm", "mac_mini_arm", "macbook_pro_arm", "macbook_air_arm",
            "macbook_arm", "mac_studio"};

    /*
     * machine_directory, main_filter and main_cache are generated from the tables above.
     * Run generateMachineIndexes after updating categories, filters, or database contents.
     */

    /*
     * getSound
     * Available Parameters: 0 Macintosh 128k, mac128, no death sound
     *                       1 Macintosh II, macii, macii_death
     *                       2 Macintosh LC, maclc, maclc_death
     *                       3 Macintosh Quadra w/o AV, 68k PowerBook, quadra, maclc_death
     *                       4 Macintosh Quadra w/ AV, quadraav, quadraav_death
     *                       5 First gen Power Macintosh, powermac6100, powermac6100_death
     *                       6 NuBus Power Macintosh, powermac5000, powermac5000_death
     *                       7 PCI Power Macintosh, powermac, powermac_death
     *                       8 New World Macintosh, newmac, no death sound
     *                       9 TAM, tam, powermac_death
     *                       PB Old World PowerPC PowerBook, powermac(s), maclc_death(s)
     *                       T2 Big Sur startup sound, bigsur, no death sound
     *                       N no startup sound, no death sound
     *
     * getCategoryRange
     * Available Manufacturer(Group) Strings: all, apple68k, appleppc, appleintel, applearm
     * Available Manufacturer(Group) Resources: R.string.menu_group0, R.string.menu_group1,
     *                                          R.string.menu_group2, R.string.menu_group3,
     *                                          R.string.menu_group4
     *
     * getFilterString
     * Available Filter Strings: names, processors, years
     * Available Filter Resources: R.string.view1, R.string.view2, R.string.view3
     */

    private final SQLiteDatabase database;

    /* Directory index. */
    private final int[] machineCategoryIndex;

    private final int[] machineDatabaseIndex;

    private final String[] machineNameIndex;

    private final String[] machineSearchNameIndex;

    private final String[] machineYearIndex;

    private final String[] machineTypeIndex;

    private final String[] machineProcessorIndex;

    private final String[] machineModelIndex;

    private final String[] machineIdentifierIndex;

    private final String[] machineGestaltIndex;

    private final String[] machineOrderIndex;

    private final String[] machineEMCIndex;

    private final int[][] machineProcessorFormatIndex;

    private final int[][] machineGraphicsFormatIndex;

    private final String[][][] mainFilters;

    private final int[][] mainSectionPositions;

    private final String[][] mainSectionNames;

    private final int[][][][] mainPositions;

    /* starts from 0, actual total -1. */
    private int totalMachine = 0;

    // Stop flooding the Logcat!
    private boolean stopQuery = false;

    MachineHelper(final SQLiteDatabase thisDatabase) {
        database = thisDatabase;

        try (Cursor directoryCursor = database.query("machine_directory",
                new String[]{"machine_id", "category_id", "database_id", "name", "sname",
                        "syear", "stype", "sprocessor", "smodel", "sident", "sgestalt",
                        "sorder", "semc", "processor_format", "graphics_format"},
                null, null, null, null, "machine_id")) {
            totalMachine = directoryCursor.getCount();
            if (totalMachine == 0) {
                throw new IllegalStateException("Machine directory is empty");
            }
            final int[] categoryIndividualCount = new int[CATEGORIES_LIST.length];
            machineCategoryIndex = new int[totalMachine];
            machineDatabaseIndex = new int[totalMachine];
            machineNameIndex = new String[totalMachine];
            machineSearchNameIndex = new String[totalMachine];
            machineYearIndex = new String[totalMachine];
            machineTypeIndex = new String[totalMachine];
            machineProcessorIndex = new String[totalMachine];
            machineModelIndex = new String[totalMachine];
            machineIdentifierIndex = new String[totalMachine];
            machineGestaltIndex = new String[totalMachine];
            machineOrderIndex = new String[totalMachine];
            machineEMCIndex = new String[totalMachine];
            machineProcessorFormatIndex = new int[totalMachine][];
            machineGraphicsFormatIndex = new int[totalMachine][];
            int expectedMachineID = 0;
            while (directoryCursor.moveToNext()) {
                final int machineID = directoryCursor.getInt(
                        directoryCursor.getColumnIndexOrThrow("machine_id"));
                final int categoryID = directoryCursor.getInt(
                        directoryCursor.getColumnIndexOrThrow("category_id"));
                final int databaseID = directoryCursor.getInt(
                        directoryCursor.getColumnIndexOrThrow("database_id"));
                if (machineID != expectedMachineID
                        || categoryID < 0 || categoryID >= CATEGORIES_LIST.length
                        || databaseID != categoryIndividualCount[categoryID]) {
                    throw new IllegalStateException("Illegal machine directory position "
                            + machineID + "/" + categoryID + "/" + databaseID);
                }
                categoryIndividualCount[categoryID]++;
                machineCategoryIndex[machineID] = categoryID;
                machineDatabaseIndex[machineID] = databaseID;
                machineNameIndex[machineID] = directoryCursor.getString(
                        directoryCursor.getColumnIndexOrThrow("name"));
                machineSearchNameIndex[machineID] = directoryCursor.getString(
                        directoryCursor.getColumnIndexOrThrow("sname"));
                machineYearIndex[machineID] = directoryCursor.getString(
                        directoryCursor.getColumnIndexOrThrow("syear"));
                machineTypeIndex[machineID] = directoryCursor.getString(
                        directoryCursor.getColumnIndexOrThrow("stype"));
                machineProcessorIndex[machineID] = directoryCursor.getString(
                        directoryCursor.getColumnIndexOrThrow("sprocessor"));
                machineModelIndex[machineID] = directoryCursor.getString(
                        directoryCursor.getColumnIndexOrThrow("smodel"));
                machineIdentifierIndex[machineID] = directoryCursor.getString(
                        directoryCursor.getColumnIndexOrThrow("sident"));
                machineGestaltIndex[machineID] = directoryCursor.getString(
                        directoryCursor.getColumnIndexOrThrow("sgestalt"));
                machineOrderIndex[machineID] = directoryCursor.getString(
                        directoryCursor.getColumnIndexOrThrow("sorder"));
                machineEMCIndex[machineID] = directoryCursor.getString(
                        directoryCursor.getColumnIndexOrThrow("semc"));
                machineProcessorFormatIndex[machineID] = parseFormatRanges(
                        directoryCursor.getString(
                                directoryCursor.getColumnIndexOrThrow("processor_format")));
                machineGraphicsFormatIndex[machineID] = parseFormatRanges(
                        directoryCursor.getString(
                                directoryCursor.getColumnIndexOrThrow("graphics_format")));
                expectedMachineID++;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load the machine directory", e);
        }

        mainFilters = new String[3][][];
        mainSectionPositions = new int[3][];
        mainSectionNames = new String[3][];
        try (Cursor filterCursor = database.query("main_filter",
                new String[]{"filter", "column_name", "keywords", "labels", "sections"},
                null, null, null, null, null)) {
            int filterCount = 0;
            while (filterCursor.moveToNext()) {
                final String thisFilter = filterCursor.getString(
                        filterCursor.getColumnIndexOrThrow("filter"));
                final int filterID = translateFilterID(thisFilter);
                if (mainFilters[filterID] != null) {
                    throw new IllegalStateException("Duplicate main filter " + thisFilter);
                }
                final String[] keywords = filterCursor.getString(
                        filterCursor.getColumnIndexOrThrow("keywords")).split(",", -1);
                final String[] labels = filterCursor.getString(
                        filterCursor.getColumnIndexOrThrow("labels")).split("\n", -1);
                if (keywords.length != labels.length) {
                    throw new IllegalStateException("Illegal main filter " + thisFilter);
                }
                mainFilters[filterID] = new String[][]{
                        {filterCursor.getString(
                                filterCursor.getColumnIndexOrThrow("column_name"))},
                        keywords, labels};
                final String rawSections = filterCursor.getString(
                        filterCursor.getColumnIndexOrThrow("sections"));
                if (rawSections.isEmpty()) {
                    mainSectionPositions[filterID] = new int[0];
                    mainSectionNames[filterID] = new String[0];
                } else {
                    final String[] sections = rawSections.split(";");
                    mainSectionPositions[filterID] = new int[sections.length];
                    mainSectionNames[filterID] = new String[sections.length];
                    int previousPosition = -1;
                    for (int i = 0; i < sections.length; i++) {
                        final String[] thisSection = sections[i].split(":");
                        if (thisSection.length != 2) {
                            throw new IllegalStateException(
                                    "Illegal main filter section " + thisFilter);
                        }
                        final int thisPosition = Integer.parseInt(thisSection[0]);
                        if (thisPosition <= previousPosition || thisPosition >= keywords.length) {
                            throw new IllegalStateException(
                                    "Illegal main filter section position " + thisFilter);
                        }
                        mainSectionPositions[filterID][i] = thisPosition;
                        mainSectionNames[filterID][i] = thisSection[1];
                        previousPosition = thisPosition;
                    }
                }
                filterCount++;
            }
            if (filterCount != 3) {
                throw new IllegalStateException("Illegal main filter count " + filterCount);
            }
            for (String[][] mainFilter : mainFilters) {
                if (mainFilter == null) {
                    throw new IllegalStateException("Incomplete main filters");
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load the main filters", e);
        }

        mainPositions = new int[5][3][][];
        try (Cursor cacheCursor = database.query("main_cache",
                new String[]{"manufacturer", "filter", "positions"},
                null, null, null, null, null)) {
            int cacheCount = 0;
            while (cacheCursor.moveToNext()) {
                final String thisManufacturer = cacheCursor.getString(
                        cacheCursor.getColumnIndexOrThrow("manufacturer"));
                final String thisFilter = cacheCursor.getString(
                        cacheCursor.getColumnIndexOrThrow("filter"));
                final int manufacturerID = translateManufacturerID(thisManufacturer);
                final int filterID = translateFilterID(thisFilter);
                if (mainPositions[manufacturerID][filterID] != null) {
                    throw new IllegalStateException("Duplicate main cache "
                            + thisManufacturer + "/" + thisFilter);
                }

                final String[] rawCategories = cacheCursor.getString(
                        cacheCursor.getColumnIndexOrThrow("positions")).split(";", -1);
                if (rawCategories.length != getFilterString(thisFilter)[1].length) {
                    throw new IllegalStateException("Illegal main cache category count "
                            + thisManufacturer + "/" + thisFilter);
                }
                final int[][] thisPositions = new int[rawCategories.length][];
                for (int i = 0; i < rawCategories.length; i++) {
                    if (rawCategories[i].isEmpty()) {
                        thisPositions[i] = new int[0];
                        continue;
                    }
                    final String[] rawMachineIDs = rawCategories[i].split(",");
                    final boolean[] matchedMachines = new boolean[totalMachine];
                    thisPositions[i] = new int[rawMachineIDs.length];
                    for (int j = 0; j < rawMachineIDs.length; j++) {
                        final int machineID = Integer.parseInt(rawMachineIDs[j]);
                        if (machineID < 0 || machineID >= totalMachine
                                || matchedMachines[machineID]) {
                            throw new IllegalStateException("Illegal cached machine ID "
                                    + machineID + " in " + thisManufacturer + "/" + thisFilter);
                        }
                        matchedMachines[machineID] = true;
                        thisPositions[i][j] = machineID;
                    }
                }
                mainPositions[manufacturerID][filterID] = thisPositions;
                cacheCount++;
            }
            if (cacheCount != 15) {
                throw new IllegalStateException("Illegal main cache count " + cacheCount);
            }
            for (int[][][] manufacturerPositions : mainPositions) {
                for (int[][] filterPositions : manufacturerPositions) {
                    if (filterPositions == null) {
                        throw new IllegalStateException("Incomplete main cache");
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load the main cache", e);
        }
        Log.w("MachineHelper", "Initialized with " + totalMachine + " machines.");
    }

    public void setStopQuery() {
        Log.e("MachineHelper", "Stopping further query.");
        stopQuery = true;
    }

    private boolean isQueryCancelled() {
        return stopQuery || Thread.currentThread().isInterrupted();
    }

    // Get total machines. For usage of random access.
    public int getMachineCount() {
        return totalMachine;
    }

    // Get generated positions for the MainActivity.
    public int[][] getMainPositions(final String thisFilter, final String thisManufacturer) {
        return mainPositions[translateManufacturerID(thisManufacturer)][translateFilterID(thisFilter)];
    }

    // Get category range for fixed navigation
    public int[] getCategoryRangeIDs(final int thisMachine) {
        validateMachineID(thisMachine);
        final String thisCategory = machineTypeIndex[thisMachine];
        final String[] categoryIDs = getFilterString("names")[1];
        final int[][] categoryPositions = getMainPositions("names", "all");
        for (int i = 0; i < categoryIDs.length; i++) {
            if (categoryIDs[i].equals(thisCategory)) {
                return categoryPositions[i];
            }
        }
        throw new IllegalStateException("Unable to find category " + thisCategory);
    }


    // Get specific position of a machine ID.
    private int[] getPosition(final int thisMachine) {
        validateMachineID(thisMachine);
        // Category ID / Remainder
        return new int[]{machineCategoryIndex[thisMachine], machineDatabaseIndex[thisMachine]};
    }

    private void validateMachineID(final int thisMachine) {
        if (thisMachine < 0 || thisMachine >= totalMachine) {
            throw new IllegalArgumentException("Machine ID is out of range: " + thisMachine);
        }
    }

    private int[] parseFormatRanges(final String thisFormat) {
        if (thisFormat == null || thisFormat.isEmpty()) {
            return new int[0];
        }
        final String[] rawRanges = thisFormat.split(";");
        final int[] formatRanges = new int[rawRanges.length * 2];
        int previousEnd = 0;
        for (int i = 0; i < rawRanges.length; i++) {
            final String[] thisRange = rawRanges[i].split(":");
            if (thisRange.length != 2) {
                throw new IllegalStateException("Illegal model format " + thisFormat);
            }
            final int rangeStart = Integer.parseInt(thisRange[0]);
            final int rangeEnd = Integer.parseInt(thisRange[1]);
            if (rangeStart < previousEnd || rangeEnd <= rangeStart) {
                throw new IllegalStateException("Illegal model range " + thisFormat);
            }
            formatRanges[i * 2] = rangeStart;
            formatRanges[i * 2 + 1] = rangeEnd;
            previousEnd = rangeEnd;
        }
        return formatRanges;
    }

    // Convert Internal Database Category ID to MH Category ID
    private int convertToMHCategoryID(final String toConvert) {
        // Array out bound bug fix
        int toReturn = 0;
        for (String thisDBCategoryID : CATEGORIES_LIST) {
            if (toConvert.equals(thisDBCategoryID)) {
                break;
            }
            toReturn++;
        }
        return toReturn;
    }

    public String getName(final int thisMachine) {
        validateMachineID(thisMachine);
        return checkApplicability(machineNameIndex[thisMachine]);
    }

    public int[] getProcessorModelRanges(final int thisMachine) {
        validateMachineID(thisMachine);
        return machineProcessorFormatIndex[thisMachine];
    }

    public int[] getGraphicsModelRanges(final int thisMachine) {
        validateMachineID(thisMachine);
        return machineGraphicsFormatIndex[thisMachine];
    }

    public boolean isClassicMachine(final int thisMachine) {
        validateMachineID(thisMachine);
        return machineIdentifierIndex[thisMachine] == null;
    }

    public String[] getSpecs(final int thisMachine) {
        int[] position = getPosition(thisMachine);
        final String[] columns = {"year", "model", "ident", "gestalt", "\"order\"", "emc",
                "processor", "graphics", "display", "ram", "rom", "software", "storage",
                "features", "expansion", "design", "support"};
        Cursor tempCursor = database.query(CATEGORIES_LIST[position[0]],
                columns, "id = " + position[1], null, null, null, null);
        tempCursor.moveToFirst();
        final String[] tempResult = new String[columns.length];
        for (int i = 0; i < columns.length; i++) {
            tempResult[i] = checkApplicability(tempCursor.getString(i));
        }
        tempCursor.close();
        return tempResult;
    }

    public String getSYear(final int thisMachine) {
        validateMachineID(thisMachine);
        return checkApplicability(machineYearIndex[thisMachine]);
    }

    // NullSafe
    private static String checkApplicability(final String thisSpec) {
        if (thisSpec == null) {
            return MainActivity.getRes().getString(R.string.not_applicable);
        } else {
            return thisSpec;
        }
    }

    private boolean isDirectoryColumn(final String thisColumn) {
        switch (thisColumn) {
            case "name":
            case "sname":
            case "syear":
            case "stype":
            case "sprocessor":
            case "smodel":
            case "sident":
            case "sgestalt":
            case "sorder":
            case "semc":
                return true;
            default:
                return false;
        }
    }

    private String getDirectoryValue(final int thisMachine, final String thisColumn) {
        switch (thisColumn) {
            case "name":
                return machineNameIndex[thisMachine];
            case "sname":
                return machineSearchNameIndex[thisMachine];
            case "syear":
                return machineYearIndex[thisMachine];
            case "stype":
                return machineTypeIndex[thisMachine];
            case "sprocessor":
                return machineProcessorIndex[thisMachine];
            case "smodel":
                return machineModelIndex[thisMachine];
            case "sident":
                return machineIdentifierIndex[thisMachine];
            case "sgestalt":
                return machineGestaltIndex[thisMachine];
            case "sorder":
                return machineOrderIndex[thisMachine];
            case "semc":
                return machineEMCIndex[thisMachine];
            default:
                throw new IllegalArgumentException("Column is not indexed: " + thisColumn);
        }
    }

    // Integrated with SoundHelper
    public int[] getSound(final int thisMachine, final Context thisContext) {
        int[] position = getPosition(thisMachine);
        Cursor tempCursor = database.query(CATEGORIES_LIST[position[0]],
                new String[]{"sound"}, "id = " + position[1], null, null, null,
                null);
        tempCursor.moveToFirst();
        String thisSound = tempCursor.getString(tempCursor.getColumnIndexOrThrow("sound"));
        tempCursor.close();
        int[] sound = {0, 0};
        // NullSafe
        if (thisSound == null) {
            return sound;
        }
        DebugHelper.log("MachineHelperGetSound", "Get parameter " + thisSound);
        switch (thisSound) {
            case "0":
                sound[0] = R.raw.mac128;
                break;
            case "1":
                sound[0] = R.raw.macii;
                break;
            case "2":
                sound[0] = R.raw.maclc;
                break;
            case "3":
                sound[0] = R.raw.quadra;
                break;
            case "4":
                sound[0] = R.raw.quadraav;
                break;
            case "5":
                sound[0] = R.raw.powermac6100;
                break;
            case "6":
                sound[0] = R.raw.powermac5000;
                break;
            case "7":
            case "PB":
                sound[0] = R.raw.powermac;
                break;
            case "8":
                sound[0] = R.raw.newmac;
                break;
            case "9":
                sound[0] = R.raw.tam;
                break;
            case "T2":
                sound[0] = R.raw.bigsur;
                break;
            default:
                ExceptionHelper.handleException(thisContext, null,
                        "MachineHelperGetSound", "Illegal parameter " + thisSound);
        }
        switch (thisSound) {
            case "1":
                sound[1] = R.raw.macii_death;
                break;
            case "2":
            case "3":
            case "PB":
                sound[1] = R.raw.maclc_death;
                break;
            case "4":
                sound[1] = R.raw.quadraav_death;
                break;
            case "5":
                sound[1] = R.raw.powermac6100_death;
                break;
            case "6":
                sound[1] = R.raw.powermac5000_death;
                break;
            case "7":
            case "9":
                sound[1] = R.raw.powermac_death;
                break;
            default:
                Log.w("MachineHelperGetDthSnd", "No death sound for parameter " + thisSound);
        }
        return sound;
    }

    public Bitmap getPicture(final int thisMachine) {
        if (thisMachine < 0 || thisMachine >= totalMachine) {
            throw new IllegalArgumentException("Machine ID is out of range: " + thisMachine);
        }
        for (int candidate = thisMachine; candidate >= 0; candidate--) {
            final int[] position = getPosition(candidate);
            final byte[] blob;
            try (Cursor cursor = database.query(CATEGORIES_LIST[position[0]],
                    new String[]{"pic"}, "id = ?", new String[]{String.valueOf(position[1])},
                    null, null, null)) {
                if (!cursor.moveToFirst()) {
                    throw new IllegalStateException("Missing database row for machine " + candidate);
                }
                blob = cursor.getBlob(cursor.getColumnIndexOrThrow("pic"));
            }
            if (blob != null) {
                final Bitmap picture = BitmapFactory.decodeByteArray(blob, 0, blob.length);
                if (picture == null) {
                    throw new IllegalStateException("Unable to decode image for machine " + candidate);
                }
                return picture;
            }
        }
        return null;
    }

    // Should return "N" if EveryMac link is not available.
    public String getConfig(final int thisMachine) {
        int[] position = getPosition(thisMachine);
        Cursor tempCursor = database.query(CATEGORIES_LIST[position[0]],
                new String[]{"links"}, "id = " + position[1], null, null, null,
                null);
        tempCursor.moveToFirst();
        String tempResult = tempCursor.getString(tempCursor.getColumnIndexOrThrow("links"));
        tempCursor.close();
        // NullSafe
        if (tempResult == null) {
            return "null";
        } else {
            return tempResult;
        }
    }

    // Refer to SpecsActivity for a documentation.
    public int getProcessorTypeImage(final int thisMachine, final Context thisContext) {
        validateMachineID(thisMachine);
        final String thisProcessorImage = machineProcessorIndex[thisMachine];
        DebugHelper.log("MHGetProcessorImageType", "Get ID " + thisProcessorImage);
        // NullSafe
        if (thisProcessorImage == null) {
            return 0;
        }
        String[] thisImages = thisProcessorImage.split("~");
        switch (thisImages[0]) {
            // Duo dock exception
            case "680X0":
            case "68000":
            case "68020":
            case "68030":
            case "68040":
                return R.drawable.motorola;
            case "601":
            case "603":
            case "604":
            case "g3":
            case "g4":
            case "g5":
                return R.drawable.powerpc;
            case "netburst":
            case "p6":
            case "core":
            case "penryn":
            case "nehalem":
            case "westmere":
            case "snb":
            case "ivb":
            case "haswell":
            case "broadwell":
            case "skylake":
            case "kabylake":
            case "coffeelake":
            case "amberlake":
            case "cascadelake":
            case "cometlake":
            case "icelake":
            case "tigerlake":
                return R.drawable.intel;
            case "A12Z":
            case "a18":
            case "m1":
            case "m2":
            case "m3":
            case "m4":
            case "m5":
                return R.drawable.applelogo;
            default:
                ExceptionHelper.handleException(thisContext, null,
                        "MHGetProcessorImageType", "Illegal parameter " + thisProcessorImage);
        }
        return 0;
    }

    public int[][] getProcessorImage(final int thisMachine, final Context thisContext) {
        int[] position = getPosition(thisMachine);
        Cursor tempCursor = database.query(CATEGORIES_LIST[position[0]],
                new String[]{"processorid"}, "id = " + position[1], null, null, null,
                null);
        tempCursor.moveToFirst();
        String thisProcessorImage = tempCursor.getString(tempCursor.getColumnIndexOrThrow("processorid"));
        tempCursor.close();
        DebugHelper.log("MHGetProcessorImage", "Get ID " + thisProcessorImage);
        // NullSafe
        if (thisProcessorImage == null) {
            return new int[][] {{0}};
        }
        String[] thisImages = thisProcessorImage.split(",");
        int[][] toReturn = new int[thisImages.length][];
        for (int i = 0; i < thisImages.length; i++) {
            switch (thisImages[i]) {
                case "740":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.ppc740;
                    break;
                case "750":
                    toReturn[i] = new int[2];
                    toReturn[i][0] = R.drawable.mpc750;
                    toReturn[i][1] = R.drawable.ppc750l;
                    break;
                case "750cx":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.ppc750cx;
                    break;
                case "750cxe":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.ppc750cxe;
                    break;
                case "755":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.mpc755;
                    break;
                case "750fx":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.ppc750fx;
                    break;
                case "7400":
                    toReturn[i] = new int[2];
                    toReturn[i][0] = R.drawable.mpc7400;
                    toReturn[i][1] = R.drawable.ppc7400;
                    break;
                case "7410":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.mpc7410;
                    break;
                case "7440":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.mpc7440;
                    break;
                case "7445":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.mpc7445;
                    break;
                case "7450":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.mpc7450;
                    break;
                case "7455":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.mpc7455;
                    break;
                case "7447":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.mpc7447a;
                    break;
                case "970":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.ppc970;
                    break;
                case "970fx":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.ppc970fx;
                    break;
                case "970mp":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.ppc970mp;
                    break;
                case "p4ht":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.intelp4ht;
                    break;
                case "coresolo":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.coresolo;
                    break;
                case "coreduo":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.intelcoreduo;
                    break;
                case "core2duo":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.core2duo;
                    break;
                case "core2ex":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.core2ex;
                    break;
                case "corei5":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei5;
                    break;
                case "corei7":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei7;
                    break;
                case "corei3_1":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei3_1;
                    break;
                case "corei5_1":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei5_1;
                    break;
                case "corei7_1":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei7_1;
                    break;
                case "corei3_2":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei3_2;
                    break;
                case "corei5_2":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei5_2;
                    break;
                case "corei7_2":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei7_2;
                    break;
                case "corei5_4":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei5_4;
                    break;
                case "corei7_4":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei7_4;
                    break;
                case "corei5_5":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei5_5;
                    break;
                case "corei7_5":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei7_5;
                    break;
                case "corei5_6":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei5_6;
                    break;
                case "corei7_6":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei7_6;
                    break;
                case "corei5_7":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei5_7;
                    break;
                case "corei7_7":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei7_7;
                    break;
                case "corei3_8":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei3_8;
                    break;
                case "corei5_8":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei5_8;
                    break;
                case "corei7_8":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei7_8;
                    break;
                case "corei9_8":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei9_8;
                    break;
                case "corei5_9":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei5_9;
                    break;
                case "corei7_9":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei7_9;
                    break;
                case "corei9_9":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei9_9;
                    break;
                case "corei3_10":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei3_10;
                    break;
                case "corei5_10":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei5_10;
                    break;
                case "corei7_10":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei7_10;
                    break;
                case "corei9_10":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corei9_10;
                    break;
                case "corem":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corem;
                    break;
                case "corem3_6":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corem3_6;
                    break;
                case "corem5_6":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corem5_6;
                    break;
                case "corem7_6":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corem7_6;
                    break;
                case "corem3_7":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.corem3_7;
                    break;
                case "xeon_a":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.xeon_a;
                    break;
                case "xeon_b":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.xeon_b;
                    break;
                case "xeon_1":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.xeon_1;
                    break;
                case "xeon_2":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.xeon_2;
                    break;
                case "xeon_6":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.xeon_6;
                    break;
                case "t1":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.applet1;
                    break;
                case "t2":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.applet2;
                    break;
                case "A12Z":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.applea12z;
                    break;
                case "m1":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.applem1;
                    break;
                case "m1pro":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.applem1pro;
                    break;
                case "m1max":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.applem1max;
                    break;
                case "m1ultra":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.applem1u;
                    break;
                default:
                    ExceptionHelper.handleException(thisContext, null,
                            "MHGetProcessorImage", "Illegal parameter " + thisProcessorImage);
                    toReturn[i] = new int[1];
                    toReturn[i][0] = 0;
                    break;
            }
        }
        return toReturn;
    }

    public int[][] getGraphicsImage(final int thisMachine, final Context thisContext) {
        int[] position = getPosition(thisMachine);
        Cursor tempCursor = database.query(CATEGORIES_LIST[position[0]],
                new String[]{"graphicsid"}, "id = " + position[1], null, null, null,
                null);
        tempCursor.moveToFirst();
        String thisGraphicsImage = tempCursor.getString(tempCursor.getColumnIndexOrThrow("graphicsid"));
        tempCursor.close();
        DebugHelper.log("MHGetGraphicsImage", "Get ID " + thisGraphicsImage);
        // NullSafe
        if (thisGraphicsImage == null) {
            return new int[][] {{0}};
        }
        String[] thisImages = thisGraphicsImage.split(",");
        int[][] toReturn = new int[thisImages.length][];
        for (int i = 0; i < thisImages.length; i++) {
            switch (thisImages[i]) {
                case "ati":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.ati;
                    break;
                case "atiradeon2000":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.atiradeon2000;
                    break;
                case "atiradeon2004":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.atiradeon2004;
                    break;
                case "atiradeon2007":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.atiradeon2007;
                    break;
                case "amdradeon":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.amdradeon;
                    break;
                case "amdradeon2013":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.amdradeon2013;
                    break;
                case "amdfirepro":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.amdfirepro;
                    break;
                case "amdradeon2016":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.amdradeon2016;
                    break;
                case "amdradeonvega":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.amdradeonvega;
                    break;
                case "nvgeforce2mx":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.nvgeforce2mx;
                    break;
                case "nvgeforce3":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.nvgeforce3;
                    break;
                case "nvgeforce4":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.nvgeforce4;
                    break;
                case "nvgeforcefx":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.nvgeforcefx;
                    break;
                case "nvgeforce6":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.nvgeforce6;
                    break;
                case "nvgeforce7":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.nvgeforce7;
                    break;
                case "nvgeforce2008":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.nvgeforce2008;
                    break;
                case "nvgeforcegt2012":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.nvgeforcegt2012;
                    break;
                case "nvgeforcegtx2012":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.nvgeforcegtx2012;
                    break;
                case "nvquadro":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.nvquadro;
                    break;
                case "nvquadro2008":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.nvquadro2008;
                    break;
                case "intelhd":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.intelhd;
                    break;
                case "inteliris":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.inteliris;
                    break;
                case "inteliris2020":
                    toReturn[i] = new int[1];
                    toReturn[i][0] = R.drawable.inteliris2020;
                    break;
                default:
                    ExceptionHelper.handleException(thisContext, null,
                            "MHGetGraphicsImage", "Illegal parameter " + thisGraphicsImage);
                    toReturn[i] = new int[1];
                    toReturn[i][0] = 0;
                    break;
            }
        }
        return toReturn;
    }

    private int translateManufacturerID(final String thisManufacturer) {
        switch (thisManufacturer) {
            case "all":
                return 0;
            case "apple68k":
                return 1;
            case "appleppc":
                return 2;
            case "appleintel":
                return 3;
            case "applearm":
                return 4;
            default:
                throw new IllegalArgumentException("Illegal manufacturer " + thisManufacturer);
        }
    }

    private int translateFilterID(final String thisFilter) {
        switch (thisFilter) {
            case "names":
                return 0;
            case "processors":
                return 1;
            case "years":
                return 2;
            default:
                throw new IllegalArgumentException("Illegal filter " + thisFilter);
        }
    }

    // Get category range by manufacturer. Should be updated accordingly.
    // This provides table names for query, when adding new tables, should be updated accordingly.
    private String[] getCategoryRange(final String thisManufacturer) {
        DebugHelper.log("MHRange", "Get parameter " + thisManufacturer);
        final String[] apple68k = {"compact_mac", "mac_ii", "mac_lc", "mac_quadra",
                "mac_performa_68k", "mac_centris", "mac_server_68k", "powerbook_68k", "powerbook_duo_68k"};
        final String[] appleppc = {"power_mac_classic", "mac_performa_ppc", "mac_server_ppc_classic",
                "powerbook_ppc_classic", "powerbook_duo_ppc", "power_mac", "imac_ppc", "emac",
                "mac_mini_ppc", "mac_server_ppc", "xserve_ppc", "powerbook_ppc", "ibook"};
        final String[] appleintel = {"mac_pro_intel", "imac_intel", "imac_pro_intel",
                "mac_mini_intel", "xserve_intel", "macbook_pro_intel", "macbook_intel", "macbook_air_intel"};
        final String[] applearm = {"mac_pro_arm", "imac_arm", "mac_mini_arm", "macbook_pro_arm",
                "macbook_air_arm", "macbook_arm", "mac_studio"};
        switch (thisManufacturer) {
            case "all":
                return CATEGORIES_LIST;
            case "apple68k":
                return apple68k;
            case "appleppc":
                return appleppc;
            case "appleintel":
                return appleintel;
            case "applearm":
                return applearm;
            default:
                Log.e("MHRange", "Invalid parameter");
                return CATEGORIES_LIST;
        }
    }

    // Get generated filter string[type(Search column/Search keywords/Display string), ID].
    public String[][] getFilterString(final String thisFilter) {
        DebugHelper.log("MHGetFilter", "Get parameters " + thisFilter);
        return mainFilters[translateFilterID(thisFilter)];
    }

    public int[] getFilterSectionPositions(final String thisFilter) {
        return mainSectionPositions[translateFilterID(thisFilter)];
    }

    public String[] getFilterSectionNames(final String thisFilter) {
        return mainSectionNames[translateFilterID(thisFilter)];
    }

    // For search use. Return machine IDs. Adapted with category range.
    public int[] searchHelper(final String columnName, final String searchInput, final String thisManufacturer,
                              final boolean isExactMatch, final boolean sortResults) {
        try {
            if (!isDirectoryColumn(columnName)) {
                throw new IllegalArgumentException("Column is not indexed: " + columnName);
            }

            final String[] thisCategoryRange = getCategoryRange(thisManufacturer);
            final boolean[] includedCategories = new boolean[CATEGORIES_LIST.length];
            for (String thisCategory : thisCategoryRange) {
                includedCategories[convertToMHCategoryID(thisCategory)] = true;
            }
            final List<Integer> rawPositions = new ArrayList<>();
            final String normalizedSearchInput = searchInput.toLowerCase(Locale.ROOT);

            // Search the directory index.
            for (int machineID = 0; machineID < totalMachine; machineID++) {
                // Terminate immediately.
                if (isQueryCancelled()) {
                    throw new IllegalAccessException();
                }
                final int categoryID = machineCategoryIndex[machineID];
                if (!includedCategories[categoryID]) {
                    continue;
                }
                final String directoryValue = getDirectoryValue(machineID, columnName);
                if (directoryValue != null
                        && directoryValue.toLowerCase(Locale.ROOT).contains(normalizedSearchInput)) {
                    rawPositions.add(machineID);
                }
            }
            int[] finalPositions = new int[rawPositions.size()];
            for (int i = 0; i < rawPositions.size(); i++) {
                finalPositions[i] = rawPositions.get(i);
            }
            DebugHelper.log("MHSearchHelper", "Raw Matched: " + finalPositions.length + " result(s).");

            // Verify Exact Match if required.
            if (isExactMatch) {
                final List<Integer> verifiedPositions = new ArrayList<>();
                for (int machineToVerify : finalPositions) {
                    final String directoryValue = getDirectoryValue(machineToVerify, columnName);
                    if (directoryValue == null) {
                        continue;
                    }
                    String[] rawUndefinedQuery = directoryValue.split("~");
                    for (String resultToVerify : rawUndefinedQuery) {
                        if (resultToVerify.equalsIgnoreCase(searchInput)) {
                            verifiedPositions.add(machineToVerify);
                            break;
                        }
                    }
                }
                // Go over the list.
                finalPositions = new int[verifiedPositions.size()];
                for (int i = 0; i < verifiedPositions.size(); i++) {
                    finalPositions[i] = verifiedPositions.get(i);
                }
            }
            DebugHelper.log("MHSearchHelper", "Exact Match is " + isExactMatch + ".");
            DebugHelper.log("MHSearchHelper", "Exact Matched: " + finalPositions.length + " result(s).");

            // Sort if required.
            if (sortResults && finalPositions.length > 1) {
                // Sort by introduction date.
                finalPositions = directSortByYear(finalPositions);
            }
            DebugHelper.log("MHSearchHelper", "Sorting is " + sortResults + ".");
            DebugHelper.log("MHSearchHelper", "Returning " + finalPositions.length + " result(s).");
            return finalPositions;
        } catch (Exception e) {
            Log.e("MHSearchHelper", "Exception Occurred, returning empty array");
            e.printStackTrace();
            return new int[0];
        }
    }

    // Get year parameter for sorting. Returns an integer in YYYYMM format.
    private int getYearForSorting(final int thisMachine) {
        try {
            String[] rawYear = getSYear(thisMachine).split(", ");
            // Terminate immediately.
            if (isQueryCancelled()) {
                throw new IllegalAccessException();
            }
            String[] targetYearSplited = rawYear[0].split("\\.");
            if (targetYearSplited.length != 2) {
                Log.e("getYearForSorting", "Error, Machine Name " + getName(thisMachine)
                        + ", Raw Year " + getSYear(thisMachine));
                throw new IllegalArgumentException();
            }
            int targetYearSplitedA = Integer.parseInt(targetYearSplited[0]);
            int targetYearSplitedB = Integer.parseInt(targetYearSplited[1]);
            if (targetYearSplitedB < 1 || targetYearSplitedB > 12) {
                throw new IllegalArgumentException();
            }
            return targetYearSplitedA * 100 + targetYearSplitedB;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    // Sorting used by ver. 4.9
    public int[] directSortByYear(final int[] input) {
        try {
            DebugHelper.log("MHDirectSort", "Starting Direct Sorting.");
            final int[] originalInput = input.clone();
            final long[] sortValues = new long[input.length];
            for (int i = 0; i < input.length; i++) {
                // Terminate immediately.
                if (isQueryCancelled()) {
                    throw new IllegalAccessException();
                }
                // Keep the original position in the low bits for stable sorting.
                sortValues[i] = ((long) getYearForSorting(input[i]) << 32)
                        | (i & 0xffffffffL);
            }
            Arrays.sort(sortValues);
            for (int i = 0; i < input.length; i++) {
                input[i] = originalInput[(int) sortValues[i]];
            }
            return input;
        } catch (Exception e) {
            e.printStackTrace();
            return input;
        }
    }

    public int[] checkDuplicate(final int[] input) {
        try {
            if (input.length == 0) {
                Log.w("MHCheckDuplicate", "Input is empty.");
                return input;
            }
            DebugHelper.log("MHCheckDuplicate", "Input is " + Arrays.toString(input));
            final LinkedHashSet<Integer> uniqueInput = new LinkedHashSet<>();
            for (int entry : input) {
                uniqueInput.add(entry);
            }
            int[] toReturn = new int[uniqueInput.size()];
            int toReturnIndex = 0;
            for (int entry : uniqueInput) {
                toReturn[toReturnIndex] = entry;
                toReturnIndex++;
            }
            DebugHelper.log("MHCheckDuplicate", "Output is " + Arrays.toString(toReturn));
            return toReturn;
        } catch (Exception e) {
            e.printStackTrace();
            return input;
        }
    }
}

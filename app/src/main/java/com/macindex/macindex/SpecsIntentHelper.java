package com.macindex.macindex;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;

import java.util.Arrays;
import java.util.List;

class SpecsIntentHelper {

    public static TextView[] initCategory(final LinearLayout currentLayout, final int[] machineIDs,
                                          final boolean isVisible, final Context thisContext) {
        try {
            TextView[] machineLoaded = new TextView[machineIDs.length];
            for (int i = 0; i < machineIDs.length; i++) {
                final int thisMachineID = machineIDs[i];
                final View mainChunk = ((LayoutInflater) thisContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                        .inflate(R.layout.chunk_main, currentLayout, false);
                final TextView machineName = mainChunk.findViewById(R.id.machineName);
                final TextView machineYear = mainChunk.findViewById(R.id.machineYear);
                final LinearLayout mainChunkToClick = mainChunk.findViewById(R.id.main_chunk_clickable);

                // Find information necessary for interface.
                final String thisName = MainActivity.getMachineHelper().getName(thisMachineID);
                final String thisYear = MainActivity.getMachineHelper().getSYear(thisMachineID);

                machineName.setText(thisName);
                machineName.setTag(MainActivity.getMachineHelper().getUID(thisMachineID));
                machineYear.setText(thisYear);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    machineName.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                } else {
                    TextViewCompat.setAutoSizeTextTypeWithDefaults(machineName, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                }

                mainChunkToClick.setOnClickListener(unused -> openMachine(
                        machineIDs, thisMachineID, thisContext));

                if (!isVisible) {
                    mainChunk.setVisibility(View.GONE);
                }

                currentLayout.addView(mainChunk);
                machineLoaded[i] = machineName;
            }
            return machineLoaded;
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e,
                    "initCategory", "Category initialization failed.");
            return null;
        }
    }

    public static void openMachine(final int[] machineIDs, final int thisMachineID,
                                   final Context thisContext) {
        if (PrefsHelper.getBooleanPrefs("isOpenEveryMac", thisContext)) {
            LinkLoadingHelper.loadLinks(MainActivity.getMachineHelper().getName(thisMachineID),
                    MainActivity.getMachineHelper().getConfig(thisMachineID), thisContext);
        } else {
            SpecsIntentHelper.sendIntent(machineIDs, thisMachineID, thisContext);
        }
    }

    public static void sendIntent(final int[] thisCategory, final int thisMachineID,
                                  final Context parentContext) {
        final Intent intent = new Intent(parentContext, SpecsActivity.class);
        final MachineHelper machineHelper = MainActivity.getMachineHelper();
        intent.putExtra("machineUID", machineHelper.getUID(thisMachineID));

        // Is fixed navigation?
        final int[] navigationIDs;
        if (PrefsHelper.getBooleanPrefs("isFixedNav", parentContext)) {
            navigationIDs = machineHelper.getCategoryRangeIDs(thisMachineID);
            DebugHelper.log("sendIntent", "Fixed Navigation, Category IDs "
                    + Arrays.toString(navigationIDs) + ", thisMachineID " + thisMachineID);
        } else {
            navigationIDs = thisCategory;
            DebugHelper.log("sendIntent", "Normal Navigation, Category IDs " + Arrays.toString(thisCategory)
                    + ", thisMachineID " + thisMachineID);
        }
        final String[] navigationUIDs = new String[navigationIDs.length];
        for (int i = 0; i < navigationIDs.length; i++) {
            navigationUIDs[i] = machineHelper.getUID(navigationIDs[i]);
        }
        intent.putExtra("navigationUIDs", navigationUIDs);
        parentContext.startActivity(intent);
    }

    public static void refreshFavourites(final TextView[][] textViewGroup, final Context thisContext) {
        // NullSafe
        if (textViewGroup != null) {
            final List<UserFavouriteHelper.Folder> userFavourites =
                    UserFavouriteHelper.read(thisContext);
            for (TextView[] thisViewGroup : textViewGroup) {
                // NullSafe
                if (thisViewGroup != null) {
                    for (TextView thisView : thisViewGroup) {
                        refreshFavourite(thisView, userFavourites);
                    }
                }
            }
        }
    }

    public static void refreshFavourite(final TextView thisView,
                                        final List<UserFavouriteHelper.Folder> userFavourites) {
        final Object machineUID = thisView.getTag();
        if (machineUID instanceof String && UserFavouriteHelper.contains(
                (String) machineUID, userFavourites)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                thisView.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
            } else {
                TextViewCompat.setAutoSizeTextTypeWithDefaults(
                        thisView, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
            }
            thisView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    0, 0, R.drawable.ic_baseline_star_24, 0);
        } else {
            thisView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                thisView.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
            } else {
                TextViewCompat.setAutoSizeTextTypeWithDefaults(
                        thisView, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE);
            }

            // Reset the text size
            thisView.setTextSize(18);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                thisView.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
            } else {
                TextViewCompat.setAutoSizeTextTypeWithDefaults(
                        thisView, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
            }
        }
    }
}

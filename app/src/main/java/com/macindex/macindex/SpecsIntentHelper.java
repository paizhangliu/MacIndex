package com.macindex.macindex;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;

import java.util.Arrays;

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
            SpecsIntentHelper.sendIntent(machineIDs, thisMachineID, thisContext, false);
        }
    }

    public static void sendIntent(final int[] thisCategory, final int thisMachineID,
                                  final Context parentContext, final boolean isRandom) {
        final Intent intent = new Intent(parentContext, SpecsActivity.class);
        intent.putExtra("machineID", thisMachineID);

        ProgressDialog waitDialog = new ProgressDialog(parentContext);
        // Is fixed navigation?
        if (PrefsHelper.getBooleanPrefs("isFixedNav", parentContext)) {
            waitDialog.setMessage(parentContext.getString(R.string.loading_fixed_nav));
            waitDialog.setCancelable(false);
            waitDialog.show();
            new Thread() {
                @Override
                public void run() {
                    final int[] newCategory = MainActivity.getMachineHelper()
                            .getCategoryRangeIDs(thisMachineID);
                    ((Activity) parentContext).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            final Activity parentActivity = (Activity) parentContext;
                            if (parentActivity.isFinishing()
                                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                                    && parentActivity.isDestroyed())) {
                                return;
                            }
                            try {
                                waitDialog.dismiss();
                                Log.i("sendIntent", "Fixed Navigation, Category IDs " + Arrays.toString(newCategory)
                                        + ", thisMachineID " + thisMachineID);
                                intent.putExtra("thisCategory", newCategory);
                                intent.putExtra("isRandom", isRandom);
                                parentContext.startActivity(intent);
                            } catch (final Exception e) {
                                ExceptionHelper.handleException(parentContext, e, null, null);
                            }
                        }
                    });
                }
            }.start();
        } else {
            Log.i("sendIntent", "Normal Navigation, Category IDs " + Arrays.toString(thisCategory)
                    + ", thisMachineID " + thisMachineID);
            intent.putExtra("thisCategory", thisCategory);
            intent.putExtra("isRandom", isRandom);
            parentContext.startActivity(intent);
        }
    }

    public static void refreshFavourites(final TextView[][] textViewGroup, final Context thisContext) {
        // NullSafe
        if (textViewGroup != null) {
            final String userFavourites = PrefsHelper.getStringPrefs("userFavourites", thisContext);
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
                                        final String userFavourites) {
        if (FavouriteActivity.isFavourite(thisView.getText().toString(), userFavourites)) {
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

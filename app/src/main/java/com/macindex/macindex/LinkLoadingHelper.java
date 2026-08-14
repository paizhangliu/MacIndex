package com.macindex.macindex;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;

class LinkLoadingHelper {

    public static void loadLinks(final String[] machineNames, final String[] machineLinks,
                                 final Context thisContext) {
        try {
            if (machineNames.length != 2 || machineNames.length != machineLinks.length) {
                throw new IllegalArgumentException();
            }
            final String[] leftLinkGroup = getLinkGroup(machineLinks[0]);
            final String[] rightLinkGroup = getLinkGroup(machineLinks[1]);
            if (leftLinkGroup.length == 0 && rightLinkGroup.length == 0) {
                Toast.makeText(thisContext,
                        MainActivity.getRes().getString(R.string.link_not_available), Toast.LENGTH_LONG).show();
                return;
            }

            final AlertDialog.Builder linkDialog = new AlertDialog.Builder(thisContext);
            final View linkChunk = ((LayoutInflater) linkDialog.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                    .inflate(R.layout.chunk_compare_links, null);
            ((TextView) linkChunk.findViewById(R.id.leftMachineName)).setText(machineNames[0]);
            ((TextView) linkChunk.findViewById(R.id.rightMachineName)).setText(machineNames[1]);
            final RadioGroup leftOptions = linkChunk.findViewById(R.id.leftOption);
            final RadioGroup rightOptions = linkChunk.findViewById(R.id.rightOption);
            initLinkOptions(leftOptions, leftLinkGroup, leftLinkGroup.length != 0, thisContext);
            initLinkOptions(rightOptions, rightLinkGroup, leftLinkGroup.length == 0, thisContext);

            final boolean[] isChanging = {false};
            leftOptions.setOnCheckedChangeListener((group, checkedID) -> {
                if (checkedID != -1 && !isChanging[0]) {
                    isChanging[0] = true;
                    rightOptions.clearCheck();
                    isChanging[0] = false;
                }
            });
            rightOptions.setOnCheckedChangeListener((group, checkedID) -> {
                if (checkedID != -1 && !isChanging[0]) {
                    isChanging[0] = true;
                    leftOptions.clearCheck();
                    isChanging[0] = false;
                }
            });

            linkDialog.setView(linkChunk);
            linkDialog.setPositiveButton(MainActivity.getRes().getString(R.string.link_confirm),
                    (dialog, which) -> {
                        try {
                            if (leftOptions.getCheckedRadioButtonId() != -1) {
                                startLink(leftLinkGroup[getCheckedIndex(leftOptions)], thisContext);
                            } else if (rightOptions.getCheckedRadioButtonId() != -1) {
                                startLink(rightLinkGroup[getCheckedIndex(rightOptions)], thisContext);
                            }
                        } catch (Exception e) {
                            ExceptionHelper.handleException(thisContext, e, null, null);
                        }
                    });
            linkDialog.setNegativeButton(MainActivity.getRes().getString(R.string.link_cancel),
                    (dialog, which) -> {
                        // Cancelled.
                    });
            linkDialog.show();
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e,
                    "loadLinks", "Link selection failed.");
        }
    }

    public static void loadLinks(final String thisName, final String thisLinks, final Context thisContext) {
        try {
            if (thisLinks.equals("null")) {
                throw new IllegalArgumentException();
            }
            if (thisLinks.equals("N")) {
                Toast.makeText(thisContext,
                        MainActivity.getRes().getString(R.string.link_not_available), Toast.LENGTH_LONG).show();
                return;
            }
            final String[] linkGroup = getLinkGroup(thisLinks);

            if (linkGroup.length == 1) {
                // Only one option, launch EveryMac directly.
                startLink(linkGroup[0], thisContext);
            } else {
                final AlertDialog.Builder linkDialog = new AlertDialog.Builder(thisContext);
                linkDialog.setTitle(thisName);
                linkDialog.setMessage(MainActivity.getRes().getString(R.string.link_message));
                // Setup each option in dialog.
                final View linkChunk = ((LayoutInflater) thisContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.chunk_links, null);
                final RadioGroup linkOptions = linkChunk.findViewById(R.id.option);
                initLinkOptions(linkOptions, linkGroup, true, thisContext);
                linkDialog.setView(linkChunk);

                // When user tapped confirm or cancel...
                linkDialog.setPositiveButton(MainActivity.getRes().getString(R.string.link_confirm),
                        (dialog, which) -> {
                            try {
                                startLink(linkGroup[getCheckedIndex(linkOptions)], thisContext);
                            } catch (Exception e) {
                                ExceptionHelper.handleException(thisContext, e, null, null);
                            }
                        });
                linkDialog.setNegativeButton(MainActivity.getRes().getString(R.string.link_cancel),
                        (dialog, which) -> {
                            // Cancelled.
                        });
                linkDialog.show();
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e,
                    "loadLinks", "Link loading failed for name " + thisName + " and string " + thisLinks);
        }
    }

    private static String[] getLinkGroup(final String thisLinks) {
        if (thisLinks == null || thisLinks.equals("null") || thisLinks.equals("N")) {
            return new String[0];
        }
        final String[] linkGroup = thisLinks.split("html;");
        // Fix ; and , split bug.
        for (int i = 0; i < linkGroup.length - 1; i++) {
            linkGroup[i] = linkGroup[i] + "html";
        }
        return linkGroup;
    }

    private static void initLinkOptions(final RadioGroup linkOptions, final String[] linkGroup,
                                        final boolean isChecked, final Context thisContext) {
        if (linkGroup.length == 0) {
            final RadioButton linkOption = new RadioButton(thisContext);
            linkOption.setText(R.string.link_not_available);
            linkOption.setEnabled(false);
            linkOptions.addView(linkOption);
            return;
        }
        for (int i = 0; i < linkGroup.length; i++) {
            final RadioButton linkOption = new RadioButton(thisContext);
            linkOption.setText(linkGroup[i].split(",http")[0]);
            linkOption.setId(View.generateViewId());
            linkOption.setTag(i);
            if (i == 0 && isChecked) {
                linkOption.setChecked(true);
            }
            linkOptions.addView(linkOption);
        }
    }

    private static int getCheckedIndex(final RadioGroup linkOptions) {
        final RadioButton checkedOption = linkOptions.findViewById(
                linkOptions.getCheckedRadioButtonId());
        return (int) checkedOption.getTag();
    }

    private static void startLink(final String thisLink, final Context thisContext) {
        startEveryMac(thisLink.split(",http")[0],
                "http" + thisLink.split(",http")[1], thisContext);
    }

    public static boolean startBrowser(final String url, final Context thisContext) {
        final Uri uri;
        try {
            if (url == null || url.isEmpty()) {
                throw new IllegalArgumentException("Link is empty");
            }
            uri = Uri.parse(url);
            if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("Illegal link: " + url);
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(thisContext, e,
                    "startBrowserCustomTabs", "Invalid link " + url);
            return false;
        }
        try {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            builder.setToolbarColor(ContextCompat.getColor(thisContext, R.color.colorPrimary));
            CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.launchUrl(thisContext, uri);
            return true;
        } catch (Exception e) {
            Log.e("startBrowserCustomTabs", "Failed to open " + url, e);
            ExceptionHelper.showMessageDialog(thisContext, R.string.link_open_failed_title,
                    R.string.link_open_failed_message);
            return false;
        }
    }

    public static void startEveryMac(final String thisName, final String url, final Context thisContext) {
        if (startBrowser(url, thisContext)) {
            Toast.makeText(thisContext,
                    MainActivity.getRes().getString(R.string.link_opening, thisName),
                    Toast.LENGTH_LONG).show();
        }
    }
}

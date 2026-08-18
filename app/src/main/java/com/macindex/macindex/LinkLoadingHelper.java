package com.macindex.macindex;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;

import com.macindex.macindex.catalog.ExternalLink;
import com.macindex.macindex.catalog.Machine;

import java.util.List;

class LinkLoadingHelper {

    private LinkLoadingHelper() {
    }

    static void loadLinks(@NonNull final Machine leftMachine,
                          @NonNull final Machine rightMachine,
                          @NonNull final Context context) {
        final List<ExternalLink> leftLinks = leftMachine.links();
        final List<ExternalLink> rightLinks = rightMachine.links();
        if (leftLinks.isEmpty() && rightLinks.isEmpty()) {
            Toast.makeText(context, R.string.link_not_available, Toast.LENGTH_LONG).show();
            return;
        }

        final AlertDialog.Builder linkDialog = new AlertDialog.Builder(context);
        final LayoutInflater inflater = (LayoutInflater) linkDialog.getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) {
            throw new IllegalStateException("LayoutInflater is unavailable");
        }
        final View linkChunk = inflater.inflate(R.layout.chunk_compare_links, null);
            ((TextView) linkChunk.findViewById(R.id.leftMachineName))
                    .setText(leftMachine.name());
            ((TextView) linkChunk.findViewById(R.id.rightMachineName))
                    .setText(rightMachine.name());
            final RadioGroup leftOptions = linkChunk.findViewById(R.id.leftOption);
            final RadioGroup rightOptions = linkChunk.findViewById(R.id.rightOption);
            initLinkOptions(leftOptions, leftLinks, !leftLinks.isEmpty(), context);
            initLinkOptions(rightOptions, rightLinks, leftLinks.isEmpty(), context);

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
        linkDialog.setPositiveButton(R.string.link_confirm, (dialog, which) -> {
            if (leftOptions.getCheckedRadioButtonId() != -1) {
                startLink(leftLinks.get(getCheckedIndex(leftOptions)), context);
            } else if (rightOptions.getCheckedRadioButtonId() != -1) {
                startLink(rightLinks.get(getCheckedIndex(rightOptions)), context);
            }
        });
        linkDialog.setNegativeButton(R.string.link_cancel, (dialog, which) -> {
            // Cancelled.
        });
        linkDialog.show();
    }

    static void loadLinks(@NonNull final Machine machine,
                          @NonNull final Context context) {
        final List<ExternalLink> links = machine.links();
        if (links.isEmpty()) {
            Toast.makeText(context, R.string.link_not_available, Toast.LENGTH_LONG).show();
            return;
        }
        if (links.size() == 1) {
            startLink(links.get(0), context);
            return;
        }

        final AlertDialog.Builder linkDialog = new AlertDialog.Builder(context);
        linkDialog.setTitle(machine.name());
        linkDialog.setMessage(R.string.link_message);
        final LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (inflater == null) {
            throw new IllegalStateException("LayoutInflater is unavailable");
        }
        final View linkChunk = inflater.inflate(R.layout.chunk_links, null);
        final RadioGroup linkOptions = linkChunk.findViewById(R.id.option);
        initLinkOptions(linkOptions, links, true, context);
        linkDialog.setView(linkChunk);
        linkDialog.setPositiveButton(R.string.link_confirm, (dialog, which) ->
                startLink(links.get(getCheckedIndex(linkOptions)), context));
        linkDialog.setNegativeButton(R.string.link_cancel, (dialog, which) -> {
            // Cancelled.
        });
        linkDialog.show();
    }

    private static void initLinkOptions(@NonNull final RadioGroup linkOptions,
                                        @NonNull final List<ExternalLink> links,
                                        final boolean isChecked,
                                        @NonNull final Context context) {
        if (links.isEmpty()) {
            final RadioButton linkOption = new RadioButton(context);
            linkOption.setText(R.string.link_not_available);
            linkOption.setEnabled(false);
            linkOptions.addView(linkOption);
            return;
        }
        for (int index = 0; index < links.size(); index++) {
            final RadioButton linkOption = new RadioButton(context);
            linkOption.setText(links.get(index).label());
            linkOption.setId(View.generateViewId());
            linkOption.setTag(index);
            if (index == 0 && isChecked) {
                linkOption.setChecked(true);
            }
            linkOptions.addView(linkOption);
        }
    }

    private static int getCheckedIndex(@NonNull final RadioGroup linkOptions) {
        final RadioButton checkedOption = linkOptions.findViewById(
                linkOptions.getCheckedRadioButtonId());
        if (checkedOption == null || !(checkedOption.getTag() instanceof Integer)) {
            throw new IllegalStateException("No external link is selected");
        }
        return (int) checkedOption.getTag();
    }

    private static void startLink(@NonNull final ExternalLink link,
                                  @NonNull final Context context) {
        startNamedLink(link.label(), link.url(), context);
    }

    public static boolean startBrowser(final String url, final Context context) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("Link is empty");
        }
        final Uri uri = Uri.parse(url);
        if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalArgumentException("Illegal link: " + url);
        }
        try {
            final CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            builder.setToolbarColor(ContextCompat.getColor(context, R.color.colorPrimary));
            builder.build().launchUrl(context, uri);
            return true;
        } catch (ActivityNotFoundException | SecurityException exception) {
            Log.w("Browser", "Failed to open " + url, exception);
            ExceptionHelper.showMessageDialog(context, R.string.link_open_failed_title,
                    R.string.link_open_failed_message);
            return false;
        }
    }

    private static void startNamedLink(final String name, final String url,
                                       final Context context) {
        if (startBrowser(url, context)) {
            Toast.makeText(context, context.getString(R.string.link_opening, name),
                    Toast.LENGTH_LONG).show();
        }
    }
}

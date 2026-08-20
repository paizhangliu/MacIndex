package com.macindex.macindex;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;

import com.macindex.macindex.catalog.Machine;
import com.macindex.macindex.catalog.SearchHit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Owns the one machine-row layout used by browsing, bookmarks, and search. */
final class MachineRowBinder {

    @FunctionalInterface
    interface SelectionListener {
        void onSelected(Machine machine);
    }

    private static final int NAME_MIN_SIZE_SP = 12;
    private static final int NAME_MAX_SIZE_SP = 18;

    private MachineRowBinder() {
    }

    static View inflate(final LayoutInflater inflater, final ViewGroup parent) {
        final View row = inflater.inflate(R.layout.chunk_machine_row, parent, false);
        final Holder holder = new Holder(
                row,
                row.findViewById(R.id.machineRowName),
                row.findViewById(R.id.machineRowSecondary));
        row.setTag(holder);
        return row;
    }

    static boolean canBind(final View row) {
        return row != null && row.getTag() instanceof Holder;
    }

    static void bindCatalogMachine(final View row,
                                   final Machine machine,
                                   final boolean favourite,
                                   final SelectionListener selectionListener) {
        bind(row, machine, machine.name(), oneLine(machine.introductionDisplayText()), favourite,
                1, selectionListener);
    }

    static void bindSearchHit(final View row,
                              final SearchHit hit,
                              final boolean favourite,
                              final SelectionListener selectionListener) {
        bind(row, hit.machine(), hit.machine().name(), searchExplanation(row.getContext(), hit),
                favourite, Integer.MAX_VALUE, selectionListener);
    }

    static void refreshFavourite(final View row, final Set<String> favouriteUids) {
        final Holder holder = requireHolder(row);
        if (holder.machine == null) {
            throw new IllegalStateException("Cannot refresh an unbound machine row");
        }
        holder.favourite = favouriteUids.contains(holder.machine.uid());
        renderFavourite(holder);
        renderAccessibility(holder);
    }

    private static void bind(final View row,
                             final Machine machine,
                             final CharSequence title,
                             final CharSequence secondary,
                             final boolean favourite,
                             final int secondaryMaxLines,
                             final SelectionListener selectionListener) {
        final Holder holder = requireHolder(row);
        holder.machine = machine;
        holder.secondaryText = secondary;
        holder.favourite = favourite;

        holder.name.setTypeface(holder.boldNameTypeface);
        holder.name.setText(title);
        holder.secondary.setMaxLines(secondaryMaxLines);
        holder.secondary.setText(secondary);
        holder.secondary.setVisibility(secondary.length() == 0 ? View.GONE : View.VISIBLE);
        renderFavourite(holder);
        renderAccessibility(holder);
        holder.root.setOnClickListener(unused -> selectionListener.onSelected(machine));
    }

    private static void renderFavourite(final Holder holder) {
        holder.name.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0, 0, holder.favourite ? R.drawable.ic_baseline_star_24 : 0, 0);
        // Recalculate after the star changes the width available to the single-line name.
        configureNameAutoSize(holder.name);
    }

    private static void renderAccessibility(final Holder holder) {
        holder.root.setContentDescription(holder.root.getContext().getString(
                holder.favourite
                        ? R.string.machine_row_accessibility_favourite
                        : R.string.machine_row_accessibility,
                holder.machine.name(), holder.secondaryText.toString()).trim());
    }

    private static void configureNameAutoSize(final TextView name) {
        TextViewCompat.setAutoSizeTextTypeWithDefaults(
                name, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, NAME_MAX_SIZE_SP);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                name,
                NAME_MIN_SIZE_SP,
                NAME_MAX_SIZE_SP,
                1,
                TypedValue.COMPLEX_UNIT_SP);
    }

    private static CharSequence searchExplanation(final Context context,
                                                   final SearchHit hit) {
        final List<SearchHit.Evidence> explanations = new ArrayList<>();
        for (SearchHit.Evidence evidence : hit.evidence()) {
            if (containsExplanation(explanations, evidence)) {
                continue;
            }
            explanations.add(evidence);
        }
        if (explanations.isEmpty()) {
            return "";
        }
        final SpannableStringBuilder result = new SpannableStringBuilder();
        for (SearchHit.Evidence evidence : explanations) {
            if (result.length() > 0) {
                result.append('\n');
            }
            appendExplanation(context, hit, evidence, result);
        }
        return result;
    }

    private static void appendExplanation(final Context context,
                                          final SearchHit hit,
                                          final SearchHit.Evidence evidence,
                                          final SpannableStringBuilder result) {
        final int label;
        if (evidence.field() == SearchHit.Field.NAME) {
            label = isCanonicalTitleEvidence(hit, evidence)
                    ? R.string.search_field_name : R.string.search_field_alias;
        } else {
            label = fieldLabel(evidence.field());
        }
        result.append(context.getString(
                R.string.search_result_field_prefix,
                context.getString(label)));
        final int valueStart = result.length();
        result.append(evidence.matchedValue());
        applyEvidenceRanges(result, valueStart, hit.evidence(),
                evidence.field(), evidence.matchedValue());
    }

    private static boolean isCanonicalTitleEvidence(final SearchHit hit,
                                                    final SearchHit.Evidence evidence) {
        return evidence.field() == SearchHit.Field.NAME
                && hit.machine().name().equals(evidence.matchedValue());
    }

    private static boolean containsExplanation(
            final List<SearchHit.Evidence> explanations,
            final SearchHit.Evidence candidate) {
        for (SearchHit.Evidence explanation : explanations) {
            if (explanation.field() == candidate.field()
                    && explanation.matchedValue().equals(candidate.matchedValue())) {
                return true;
            }
        }
        return false;
    }

    private static void applyEvidenceRanges(
            final SpannableStringBuilder target,
            final int valueStart,
            final List<SearchHit.Evidence> evidenceList,
            final SearchHit.Field field,
            final String matchedValue) {
        final List<SearchRange> ranges = new ArrayList<>();
        for (SearchHit.Evidence evidence : evidenceList) {
            if (evidence.field() == field && matchedValue.equals(evidence.matchedValue())) {
                ranges.add(new SearchRange(
                        evidence.matchStartInclusive(), evidence.matchEndExclusive()));
            }
        }
        Collections.sort(ranges, new Comparator<SearchRange>() {
            @Override
            public int compare(final SearchRange left, final SearchRange right) {
                return Integer.compare(left.start, right.start);
            }
        });
        int mergedStart = -1;
        int mergedEnd = -1;
        for (SearchRange range : ranges) {
            if (mergedStart < 0) {
                mergedStart = range.start;
                mergedEnd = range.end;
            } else if (range.start <= mergedEnd) {
                mergedEnd = Math.max(mergedEnd, range.end);
            } else {
                applyBold(target, valueStart + mergedStart, valueStart + mergedEnd);
                mergedStart = range.start;
                mergedEnd = range.end;
            }
        }
        if (mergedStart >= 0) {
            applyBold(target, valueStart + mergedStart, valueStart + mergedEnd);
        }
    }

    private static void applyBold(final SpannableStringBuilder target,
                                  final int start,
                                  final int end) {
        target.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    static int fieldLabel(final SearchHit.Field field) {
        switch (field) {
            case NAME:
                return R.string.search_field_name;
            case CODENAME:
                return R.string.search_field_codename;
            case MODEL_NUMBER:
                return R.string.search_field_model_number;
            case MODEL_IDENTIFIER:
                return R.string.search_field_model_identifier;
            case GESTALT_ID:
                return R.string.search_field_gestalt_id;
            case PART_NUMBER:
                return R.string.search_field_part_number;
            case EMC_NUMBER:
                return R.string.search_field_emc_number;
            case PROCESSOR:
                return R.string.search_field_processor;
            case INTRODUCTION:
                return R.string.search_field_introduction;
            default:
                throw new IllegalStateException("Unknown search field " + field);
        }
    }

    private static String oneLine(final String value) {
        return value.replace("\r\n", "\n").replace("\n", ", ");
    }

    private static Holder requireHolder(final View row) {
        if (!canBind(row)) {
            throw new IllegalArgumentException("View is not a machine row");
        }
        return (Holder) row.getTag();
    }

    private static final class Holder {
        private final View root;
        private final TextView name;
        private final TextView secondary;
        private final Typeface boldNameTypeface;
        private Machine machine;
        private CharSequence secondaryText = "";
        private boolean favourite;

        private Holder(final View sourceRoot,
                       final TextView sourceName,
                       final TextView sourceSecondary) {
            root = sourceRoot;
            name = sourceName;
            secondary = sourceSecondary;
            boldNameTypeface = Typeface.create(sourceName.getTypeface(), Typeface.BOLD);
        }
    }

    private static final class SearchRange {
        private final int start;
        private final int end;

        private SearchRange(final int sourceStart, final int sourceEnd) {
            start = sourceStart;
            end = sourceEnd;
        }
    }
}

package com.macindex.macindex;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.macindex.macindex.catalog.Machine;
import com.macindex.macindex.catalog.SearchHit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Search-only rows which keep the reason for every catalog match visible. */
final class SearchResultAdapter extends BaseAdapter {

    private final LayoutInflater layoutInflater;
    private final List<SearchHit> hits;
    private final MachineRowBinder.SelectionListener selectionListener;
    private Set<String> favouriteUids;

    SearchResultAdapter(final List<SearchHit> sourceHits,
                        final Set<String> sourceFavouriteUids,
                        final Context sourceContext,
                        final MachineRowBinder.SelectionListener sourceSelectionListener) {
        layoutInflater = LayoutInflater.from(sourceContext);
        favouriteUids = sourceFavouriteUids;
        selectionListener = sourceSelectionListener;
        hits = sourceHits;
    }

    List<Machine> navigationMachines() {
        final List<Machine> machines = new ArrayList<>(hits.size());
        for (SearchHit hit : hits) {
            machines.add(hit.machine());
        }
        return Collections.unmodifiableList(machines);
    }

    void setFavouriteUids(final Set<String> sourceFavouriteUids) {
        favouriteUids = sourceFavouriteUids;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return hits.size();
    }

    @Override
    public Object getItem(final int position) {
        return hits.get(position);
    }

    @Override
    public long getItemId(final int position) {
        return Long.parseLong(hits.get(position).machine().uid().substring(2));
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        return resultView(hits.get(position), convertView, parent);
    }

    private View resultView(final SearchHit hit, final View convertView,
                            final ViewGroup parent) {
        final View resultView = MachineRowBinder.canBind(convertView)
                ? convertView
                : MachineRowBinder.inflate(layoutInflater, parent);
        final Machine machine = hit.machine();
        final boolean favourite = favouriteUids.contains(machine.uid());
        MachineRowBinder.bindSearchHit(
                resultView, hit, favourite, selectionListener::onSelected);
        return resultView;
    }
}

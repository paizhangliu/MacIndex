package com.macindex.macindex;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

/* List adapter for machine search results. */
class MachineListAdapter extends BaseAdapter {

    private final Context thisContext;

    private final LayoutInflater layoutInflater;

    private final int[] machineIDs;

    private String userFavourites = "";

    MachineListAdapter(final int[] thisMachineIDs, final Context parentContext) {
        machineIDs = thisMachineIDs;
        thisContext = parentContext;
        layoutInflater = (LayoutInflater) parentContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        refreshFavourites(false);
    }

    public void refreshFavourites() {
        refreshFavourites(true);
    }

    private void refreshFavourites(final boolean notifyChange) {
        userFavourites = PrefsHelper.getStringPrefs("userFavourites", thisContext);
        if (notifyChange) {
            notifyDataSetChanged();
        }
    }

    @Override
    public int getCount() {
        return machineIDs.length;
    }

    @Override
    public Object getItem(final int position) {
        return machineIDs[position];
    }

    @Override
    public long getItemId(final int position) {
        return machineIDs[position];
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        final View machineView;
        final MachineHolder machineHolder;
        if (convertView == null) {
            machineView = layoutInflater.inflate(R.layout.chunk_main, parent, false);
            machineHolder = new MachineHolder();
            machineHolder.machineName = machineView.findViewById(R.id.machineName);
            machineHolder.machineYear = machineView.findViewById(R.id.machineYear);
            machineHolder.machineLayout = machineView.findViewById(R.id.main_chunk_clickable);
            machineView.setTag(machineHolder);
        } else {
            machineView = convertView;
            machineHolder = (MachineHolder) convertView.getTag();
        }

        final int machineID = machineIDs[position];
        final String machineName = MainActivity.getMachineHelper().getName(machineID);
        machineHolder.machineName.setText(machineName);
        machineHolder.machineYear.setText(MainActivity.getMachineHelper().getSYear(machineID));
        SpecsIntentHelper.refreshFavourite(machineHolder.machineName, userFavourites);

        machineHolder.machineLayout.setOnClickListener(unused -> SpecsIntentHelper.openMachine(
                machineIDs, machineID, thisContext));
        return machineView;
    }

    private static class MachineHolder {
        private TextView machineName;
        private TextView machineYear;
        private LinearLayout machineLayout;
    }
}

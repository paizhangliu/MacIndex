package com.macindex.macindex;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import com.macindex.macindex.catalog.IntroductionDate;
import com.macindex.macindex.catalog.Machine;
import com.macindex.macindex.catalog.MachineCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** UID-only rendering and navigation shared by the comments and favourites screens. */
final class UserLibraryViewAdapter {

    private UserLibraryViewAdapter() {
    }

    static List<Machine> resolveMachines(final MachineCatalog catalog,
                                         final List<String> machineUids,
                                         final boolean sortByIntroduction) {
        final List<Machine> machines = new ArrayList<>(machineUids.size());
        for (String uid : machineUids) {
            machines.add(catalog.requireByUid(uid));
        }
        if (sortByIntroduction && machines.size() > 1) {
            Collections.sort(machines, (left, right) -> earliestIntroduction(left)
                    .compareTo(earliestIntroduction(right)));
        }
        return Collections.unmodifiableList(machines);
    }

    static void addFavouriteMachineRows(final LinearLayout parent,
                                        final List<Machine> navigationMachines,
                                        final MachineCatalog catalog,
                                        final boolean fixedNavigation,
                                        final Context context) {
        final LayoutInflater inflater = LayoutInflater.from(context);
        for (Machine machine : navigationMachines) {
            final View row = MachineRowBinder.inflate(inflater, parent);
            MachineRowBinder.bindCatalogMachine(
                    row,
                    machine,
                    true,
                    unused -> openMachine(context, catalog, fixedNavigation,
                            navigationMachines, machine));
            row.setVisibility(View.GONE);
            parent.addView(row);
        }
    }

    static void openMachine(final Context context,
                            final MachineCatalog catalog,
                            final boolean fixedNavigation,
                            final List<Machine> currentNavigation,
                            final Machine selected) {
        final List<Machine> navigation;
        if (fixedNavigation) {
            navigation = catalog.sequenceForProductType(selected.productTypeKey());
        } else {
            navigation = currentNavigation;
        }
        context.startActivity(NavigationContract.machineSpecsIntent(
                context,
                NavigationContract.MachineRequest.create(selected, navigation, false)));
    }

    private static IntroductionDate earliestIntroduction(final Machine machine) {
        return Collections.min(machine.introductions());
    }
}

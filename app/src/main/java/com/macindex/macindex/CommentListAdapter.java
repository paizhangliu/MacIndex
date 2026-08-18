package com.macindex.macindex;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;

import com.macindex.macindex.catalog.Machine;
import com.macindex.macindex.catalog.MachineCatalog;
import com.macindex.macindex.userstate.UserComment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* List adapter for the original comment chunk. */
class CommentListAdapter extends BaseAdapter {

    private final Context context;
    private final LayoutInflater layoutInflater;
    private final MachineCatalog catalog;
    private final boolean fixedNavigation;
    private final List<Machine> navigationMachines;
    private final List<Row> rows;

    CommentListAdapter(final List<UserComment> comments,
                       final MachineCatalog machineCatalog,
                       final boolean sortByIntroduction,
                       final boolean useFixedNavigation,
                       final Context parentContext) {
        context = parentContext;
        layoutInflater = LayoutInflater.from(parentContext);
        catalog = machineCatalog;
        fixedNavigation = useFixedNavigation;

        final List<String> machineUids = new ArrayList<>(comments.size());
        final Map<String, String> commentsByUid = new HashMap<>();
        for (UserComment comment : comments) {
            machineUids.add(comment.getMachineUid());
            commentsByUid.put(comment.getMachineUid(), comment.getText());
        }
        navigationMachines = UserLibraryViewAdapter.resolveMachines(
                catalog, machineUids, sortByIntroduction);
        rows = new ArrayList<>(navigationMachines.size());
        for (Machine machine : navigationMachines) {
            final String comment = commentsByUid.get(machine.uid());
            if (comment == null) {
                throw new IllegalArgumentException("Missing comment for machine " + machine.uid());
            }
            rows.add(new Row(machine, comment));
        }
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Object getItem(final int position) {
        return rows.get(position).machine.uid();
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        final View commentView;
        final CommentHolder holder;
        if (convertView == null) {
            commentView = layoutInflater.inflate(R.layout.chunk_comments, parent, false);
            holder = new CommentHolder();
            holder.machineName = commentView.findViewById(R.id.machineName);
            holder.machineComment = commentView.findViewById(R.id.machineComment);
            holder.commentLayout = commentView.findViewById(R.id.comment_chunk);
            commentView.setTag(holder);
        } else {
            commentView = convertView;
            holder = (CommentHolder) convertView.getTag();
        }

        final Row row = rows.get(position);
        holder.machineName.setText(row.machine.name());
        holder.machineComment.setText(row.comment);
        TextViewCompat.setAutoSizeTextTypeWithDefaults(
                holder.machineName, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);

        holder.commentLayout.setOnClickListener(unused -> UserLibraryViewAdapter.openMachine(
                context, catalog, fixedNavigation, navigationMachines, row.machine));
        holder.commentLayout.setOnLongClickListener(unused -> {
            ExceptionHelper.copyText(context, "userComment", holder.machineComment.getText(),
                    R.string.copy_information_success);
            return true;
        });
        return commentView;
    }

    private static final class Row {
        private final Machine machine;
        private final String comment;

        private Row(final Machine machine, final String comment) {
            this.machine = machine;
            this.comment = comment;
        }
    }

    private static class CommentHolder {
        private TextView machineName;
        private TextView machineComment;
        private LinearLayout commentLayout;
    }
}

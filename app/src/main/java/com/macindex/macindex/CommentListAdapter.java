package com.macindex.macindex;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.widget.TextViewCompat;

/* List adapter for the original comment chunk. */
class CommentListAdapter extends BaseAdapter {

    private final Context thisContext;

    private final LayoutInflater layoutInflater;

    private final int[] machineIDs;

    private final String[] machineNames;

    private final String[] machineComments;

    CommentListAdapter(final int[] thisMachineIDs, final String[] thisCommentsStrings,
                       final Context parentContext) {
        machineIDs = thisMachineIDs;
        thisContext = parentContext;
        layoutInflater = (LayoutInflater) parentContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        machineNames = new String[machineIDs.length];
        machineComments = new String[machineIDs.length];
        for (int i = 0; i < machineIDs.length; i++) {
            machineNames[i] = MainActivity.getMachineHelper().getName(machineIDs[i]);
            machineComments[i] = "";
            for (String thisString : thisCommentsStrings) {
                final String[] commentParts = thisString.split("│", 2);
                if (commentParts.length == 2 && commentParts[0].equals(machineNames[i])) {
                    machineComments[i] = commentParts[1];
                    break;
                }
            }
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
        final View commentView;
        final CommentHolder commentHolder;
        if (convertView == null) {
            commentView = layoutInflater.inflate(R.layout.chunk_comments, parent, false);
            commentHolder = new CommentHolder();
            commentHolder.machineName = commentView.findViewById(R.id.machineName);
            commentHolder.machineComment = commentView.findViewById(R.id.machineComment);
            commentHolder.commentLayout = commentView.findViewById(R.id.comment_chunk);
            commentView.setTag(commentHolder);
        } else {
            commentView = convertView;
            commentHolder = (CommentHolder) convertView.getTag();
        }

        commentHolder.machineName.setText(machineNames[position]);
        commentHolder.machineComment.setText(machineComments[position]);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            commentHolder.machineName.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        } else {
            TextViewCompat.setAutoSizeTextTypeWithDefaults(commentHolder.machineName,
                    TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        }

        commentHolder.commentLayout.setOnClickListener(unused -> SpecsIntentHelper.sendIntent(
                machineIDs, machineIDs[position], thisContext));
        commentHolder.commentLayout.setOnLongClickListener(unused -> {
            ClipboardManager clipboard = (ClipboardManager) thisContext.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("userComment", commentHolder.machineComment.getText());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(thisContext, MainActivity.getRes().getString(R.string.copy_information_success),
                    Toast.LENGTH_LONG).show();
            return true;
        });
        return commentView;
    }

    private static class CommentHolder {
        private TextView machineName;
        private TextView machineComment;
        private LinearLayout commentLayout;
    }
}

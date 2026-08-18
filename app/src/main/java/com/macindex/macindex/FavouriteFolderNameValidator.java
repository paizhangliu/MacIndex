package com.macindex.macindex;

import android.widget.EditText;

import com.macindex.macindex.userstate.FavouriteFolder;
import com.macindex.macindex.userstate.UserStateLimits;

import java.util.List;

final class FavouriteFolderNameValidator {

    private FavouriteFolderNameValidator() { }

    static boolean validate(final EditText input,
                            final String name,
                            final List<FavouriteFolder> folders) {
        if (name.isEmpty()) {
            input.setError(input.getContext().getString(R.string.favourites_error_empty));
            return false;
        }
        if (name.length() > UserStateLimits.MAX_FOLDER_NAME_LENGTH || name.contains("\n")) {
            input.setError(input.getContext().getString(R.string.favourites_error_length,
                    UserStateLimits.MAX_FOLDER_NAME_LENGTH));
            return false;
        }
        for (FavouriteFolder folder : folders) {
            if (folder.getName().equals(name)) {
                input.setError(input.getContext().getString(R.string.favourites_error_conflict));
                return false;
            }
        }
        return true;
    }
}

package com.macindex.macindex;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.TextViewCompat;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class SpecsActivity extends AppCompatActivity {

    private int machineID = -1;

    private String[] navigationUIDs = {};

    private int machineIDPosition = -1;

    private SpecsHelper specsHelper = null;

    private String thisName = null;

    private String thisUID = null;

    private String thisType = null;

    private String thisProcessor = null;

    private String thisMaxram = null;

    private String thisYear = null;

    private String thisModel = null;

    private String thisId = null;

    private String thisGraphics = null;

    private String thisDisplay = null;

    private String thisFeatures = null;

    private String thisExpansion = null;

    private String thisStorage = null;

    private String thisOrder = null;

    private String thisGestalt = null;

    private String thisEmc = null;

    private String thisSoftware = null;

    private String thisDesign = null;

    private String thisSupport = null;

    private String thisComment = null;

    private MenuItem compareItem = null;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_specs);
        WindowInsetsHelper.apply(this);
        specsHelper = new SpecsHelper(this);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (!MainActivity.validateOperation(this)) {
            return;
        }

        try {
            final Intent intent = getIntent();
            navigationUIDs = intent.getStringArrayExtra("navigationUIDs");
            thisUID = intent.getStringExtra("machineUID");

            if (navigationUIDs == null || thisUID == null) {
                throw new IllegalArgumentException();
            }
            machineID = MainActivity.getMachineHelper().getMachineID(thisUID);

            // Find the current position.
            for (int i = 0; i < navigationUIDs.length; i++) {
                if (navigationUIDs[i].equals(thisUID)) {
                    machineIDPosition = i;
                    break;
                }
            }

            if (machineIDPosition == -1) {
                throw new IllegalArgumentException();
            }

            ViewGroup mainView = findViewById(R.id.mainView);
            LayoutTransition layoutTransition = mainView.getLayoutTransition();
            layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
            initialize();
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, null, null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_specs, menu);
        compareItem = menu.findItem(R.id.addCompareItem);
        initCompareCheckBox();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final int itemID = item.getItemId();
        if (itemID == R.id.shareItem) {
            copySpecification();
        } else if (itemID == R.id.shareLinkItem) {
            generateShareLink();
        } else if (itemID == R.id.addFavouriteItem) {
            selectFolder();
        } else if (itemID == R.id.addCompareItem) {
            addToCompare();
        } else if (itemID == R.id.commentItem) {
            initCommentDialog();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        release();
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        release();
        super.onStop();
    }

    @Override
    protected void onRestart() {
        // Restart Sound System.
        super.onRestart();
        initImage();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void initialize() {
        DebugHelper.log("SpecsInitialize", "Machine ID " + machineID);
        if (PrefsHelper.getBooleanPrefs("isUseNavButtons", this) && navigationUIDs.length > 1) {
            initButtons();
        }
        getSpecs();
        initSpecs();
        initImage();
        initLinks();
        initComment();
    }

    private void release() {
        if (specsHelper != null) {
            specsHelper.release();
        }
    }

    private void getSpecs() {
        final MachineHelper helper = MainActivity.getMachineHelper();
        final String[] thisSpecs = helper.getSpecs(machineID);
        thisName = helper.getName(machineID);
        thisUID = helper.getUID(machineID);
        thisYear = thisSpecs[0];
        thisModel = thisSpecs[1];
        thisId = thisSpecs[2];
        thisGestalt = thisSpecs[3];
        thisOrder = thisSpecs[4];
        thisEmc = thisSpecs[5];
        thisProcessor = thisSpecs[6];
        thisGraphics = thisSpecs[7];
        thisDisplay = thisSpecs[8];
        thisMaxram = thisSpecs[9];
        thisType = thisSpecs[10];
        thisSoftware = thisSpecs[11];
        thisStorage = thisSpecs[12];
        thisFeatures = thisSpecs[13];
        thisExpansion = thisSpecs[14];
        thisDesign = thisSpecs[15];
        thisSupport = thisSpecs[16];
    }

    private void initSpecs() {
        try {
            final TextView type = findViewById(R.id.typeText);
            final TextView processor = findViewById(R.id.processorText);
            final TextView maxram = findViewById(R.id.maxramText);
            final TextView year = findViewById(R.id.yearText);
            final TextView model = findViewById(R.id.modelText);
            final TextView id = findViewById(R.id.idText);
            final TextView graphics = findViewById(R.id.graphicsText);
            final TextView display = findViewById(R.id.displayText);
            final TextView features = findViewById(R.id.featuresText);
            final TextView expansion = findViewById(R.id.expansionText);
            final TextView storage = findViewById(R.id.storageText);
            final TextView order = findViewById(R.id.orderText);
            final TextView gestalt = findViewById(R.id.gestaltText);
            final TextView emc = findViewById(R.id.emcText);
            final TextView software = findViewById(R.id.softwareText);
            final TextView design = findViewById(R.id.designText);
            final TextView support = findViewById(R.id.supportText);

            this.setTitle(thisName);
            reloadName();

            final boolean isClassic = MainActivity.getMachineHelper()
                    .isClassicMachine(machineID);
            findViewById(R.id.idLayout).setVisibility(isClassic ? View.GONE : View.VISIBLE);
            findViewById(R.id.idDivider).setVisibility(isClassic ? View.GONE : View.VISIBLE);
            findViewById(R.id.gestaltLayout).setVisibility(isClassic ? View.VISIBLE : View.GONE);
            findViewById(R.id.gestaltDivider).setVisibility(
                    isClassic ? View.VISIBLE : View.GONE);
            findViewById(R.id.emcLayout).setVisibility(isClassic ? View.GONE : View.VISIBLE);
            findViewById(R.id.emcDivider).setVisibility(isClassic ? View.GONE : View.VISIBLE);

            type.setText(thisType);
            specsHelper.initCopy(type, thisType, "typeInfo");
            processor.setText(specsHelper.formatModels(thisProcessor,
                    MainActivity.getMachineHelper().getProcessorModelRanges(machineID)));
            specsHelper.initCopy(processor, thisProcessor, "processorInfo");
            maxram.setText(thisMaxram);
            specsHelper.initCopy(maxram, thisMaxram, "maxramInfo");
            year.setText(thisYear);
            specsHelper.initCopy(year, thisYear, "yearInfo");
            model.setText(thisModel);
            specsHelper.initCopy(model, thisModel, "modelInfo");
            id.setText(thisId);
            specsHelper.initCopy(id, thisId, "idInfo");
            graphics.setText(specsHelper.formatModels(thisGraphics,
                    MainActivity.getMachineHelper().getGraphicsModelRanges(machineID)));
            specsHelper.initCopy(graphics, thisGraphics, "graphicsInfo");
            display.setText(thisDisplay);
            specsHelper.initCopy(display, thisDisplay, "displayInfo");
            features.setText(thisFeatures);
            specsHelper.initCopy(features, thisFeatures, "featuresInfo");
            expansion.setText(thisExpansion);
            specsHelper.initCopy(expansion, thisExpansion, "expansionInfo");
            storage.setText(thisStorage);
            specsHelper.initCopy(storage, thisStorage, "storageInfo");
            specsHelper.initPartNumbers(order, thisOrder);
            specsHelper.initCopy(order, thisOrder, "orderInfo");
            gestalt.setText(thisGestalt);
            specsHelper.initCopy(gestalt, thisGestalt, "gestaltInfo");
            emc.setText(thisEmc);
            specsHelper.initCopy(emc, thisEmc, "emcInfo");
            software.setText(thisSoftware);
            specsHelper.initCopy(software, thisSoftware, "softwareInfo");
            design.setText(thisDesign);
            specsHelper.initCopy(design, thisDesign, "designInfo");
            support.setText(thisSupport);
            specsHelper.initCopy(support, thisSupport, "supportInfo");
            specsHelper.setSupportColor(support, thisSupport);

            /*
                Processor Images dynaLoad.

                (1) Try getting type image. Will load if the type image is present.
                (2) Try getting specific image. Will load if specific image(s) is/are present.
                (3) No action. The case is not applicable for both loading process.
            */
            final LinearLayout processorTypeImageLayout = findViewById(R.id.processorTypeImageLayout);
            final ImageView processorTypeImage = findViewById(R.id.processorTypeImage);
            final LinearLayout processorImageLayoutContainer = findViewById(R.id.processorImageLayoutContainer);
            final HorizontalScrollView processorImageScrollView = findViewById(R.id.processorImageScrollView);
            final LinearLayout processorImages = findViewById(R.id.processorImageLayout);
            final int[][] processorImageRes = MainActivity.getMachineHelper().getProcessorImage(machineID, SpecsActivity.this);

            // Default states are all hidden.
            processorTypeImageLayout.setVisibility(View.GONE);
            processorImageLayoutContainer.setVisibility(View.GONE);

            final int processorTypeImageRes = MainActivity.getMachineHelper().getProcessorTypeImage(machineID, SpecsActivity.this);
            if (processorTypeImageRes != 0) {
                // Got type image. Now loading.
                processorTypeImageLayout.setVisibility(View.VISIBLE);
                processorTypeImage.setImageBitmap(BitmapLoadingHelper.decodeSampledBitmapFromResource(getResources(), processorTypeImageRes, 200, 200));
                ThemeHelper.applyProcessorTypeLogo(this, processorTypeImage,
                        processorTypeImageRes);
            }
            if (processorImageRes[0][0] != 0) {
                // Got specific images. Now loading.
                processorImageLayoutContainer.setVisibility(View.VISIBLE);
                // Clear all existing children.
                processorImages.removeAllViews();
                for (int[] processorImageResGroup : processorImageRes) {
                    for (final int thisProcessorImageRes : processorImageResGroup) {
                        @SuppressLint("InflateParams")
                        final View imageChunk = getLayoutInflater().inflate(R.layout.chunk_processor_image, null);
                        final ImageView thisProcessorImage = imageChunk.findViewById(R.id.processorImage);
                        thisProcessorImage.setImageBitmap(BitmapLoadingHelper.decodeSampledBitmapFromResource(getResources(), thisProcessorImageRes, 200, 200));
                        ThemeHelper.applyImageMask(this, thisProcessorImage);
                        processorImages.addView(imageChunk);
                    }
                }
                // Remove the last space.
                ((LinearLayout) processorImages.getChildAt(processorImages.getChildCount() - 1)).removeViewAt(1);
                fitImageLayout(processorImageScrollView, processorImages, R.id.processorImage);
            }

            /*
                Graphics Images dynaLoad.

                (1) Try getting specific image. Will load if specific image(s) is/are present.
                (2) No action. The case is not applicable for the loading process.
            */
            final LinearLayout graphicsImageLayoutContainer = findViewById(R.id.graphicsImageLayoutContainer);
            final HorizontalScrollView graphicsImageScrollView = findViewById(R.id.graphicsImageScrollView);
            final LinearLayout graphicsImages = findViewById(R.id.graphicsImageLayout);
            final int[][] graphicsImageRes = MainActivity.getMachineHelper().getGraphicsImage(machineID, SpecsActivity.this);

            // Default state is hidden.
            graphicsImageLayoutContainer.setVisibility(View.GONE);

            if (graphicsImageRes[0][0] != 0) {
                // Got specific images. Now loading.
                graphicsImageLayoutContainer.setVisibility(View.VISIBLE);
                // Clear all existing children.
                graphicsImages.removeAllViews();
                for (int[] graphicsImageResGroup : graphicsImageRes) {
                    for (final int thisGraphicsImageRes : graphicsImageResGroup) {
                        @SuppressLint("InflateParams")
                        final View imageChunk = getLayoutInflater().inflate(R.layout.chunk_graphics_image, null);
                        final ImageView thisGraphicsImage = imageChunk.findViewById(R.id.graphicsImage);
                        thisGraphicsImage.setImageBitmap(BitmapLoadingHelper.decodeSampledBitmapFromResource(getResources(), thisGraphicsImageRes, 200, 200));
                        ThemeHelper.applyImageMask(this, thisGraphicsImage);
                        graphicsImages.addView(imageChunk);
                    }
                }
                // Remove the last space.
                ((LinearLayout) graphicsImages.getChildAt(graphicsImages.getChildCount() - 1)).removeViewAt(1);
                fitImageLayout(graphicsImageScrollView, graphicsImages, R.id.graphicsImage);
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e,
                    "initSpecs", "Failed, Machine ID " + machineID);
        }
    }

    private void fitImageLayout(final HorizontalScrollView thisScrollView,
                                final LinearLayout thisImageLayout, final int thisImageID) {
        thisScrollView.post(() -> {
            final float density = getResources().getDisplayMetrics().density;
            final ImageView firstImage = thisImageLayout.getChildAt(0).findViewById(thisImageID);
            final int maximumImageHeight = firstImage.getLayoutParams().height;
            final int minimumImageHeight = Math.round(50 * density);
            final int allSpacesWidth = Math.round(5 * density)
                    * (thisImageLayout.getChildCount() - 1);
            float allImagesRatio = 0;

            for (int i = 0; i < thisImageLayout.getChildCount(); i++) {
                final ImageView thisImage = thisImageLayout.getChildAt(i).findViewById(thisImageID);
                if (thisImage.getDrawable() != null
                        && thisImage.getDrawable().getIntrinsicHeight() != 0) {
                    allImagesRatio += (float) thisImage.getDrawable().getIntrinsicWidth()
                            / thisImage.getDrawable().getIntrinsicHeight();
                }
            }

            if (allImagesRatio == 0) {
                return;
            }

            final int availableWidth = thisScrollView.getWidth()
                    - thisScrollView.getPaddingLeft() - thisScrollView.getPaddingRight();
            int imageHeight = Math.round((availableWidth - allSpacesWidth) / allImagesRatio);
            imageHeight = Math.max(minimumImageHeight, Math.min(maximumImageHeight, imageHeight));

            for (int i = 0; i < thisImageLayout.getChildCount(); i++) {
                final ImageView thisImage = thisImageLayout.getChildAt(i).findViewById(thisImageID);
                final ViewGroup.LayoutParams imageParams = thisImage.getLayoutParams();
                imageParams.height = imageHeight;
                thisImage.setLayoutParams(imageParams);
            }

            thisScrollView.scrollTo(0, 0);
        });
    }

    private void reloadName() {
        final TextView name = findViewById(R.id.nameText);
        name.setVisibility(View.INVISIBLE);

        // Reset the auto-sizing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            name.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
        } else {
            TextViewCompat.setAutoSizeTextTypeWithDefaults(name, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE);
        }

        // Reset the Machine Name.
        name.setText(thisName);
        name.setTextSize(20);

        // Check if the star is needed.
        if (UserFavouriteHelper.contains(thisUID, this)) {
            name.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_baseline_star_24, 0);
        } else {
            name.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }

        // Auto-sizing only if the width is insufficient.
        name.post(() -> {
            if (!name.getLayout().getText().toString().equals(thisName)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    name.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                } else {
                    TextViewCompat.setAutoSizeTextTypeWithDefaults(name, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
                }
            }
            name.setVisibility(View.VISIBLE);
        });

        // Set copy
        specsHelper.initCopy(name, thisName, "nameInfo");
    }

    private void initImage() {
        try {
            // Init image
            final ImageView image = findViewById(R.id.pic);
            final Bitmap picture = MainActivity.getMachineHelper().getPicture(machineID,
                    getResources().getDisplayMetrics().widthPixels,
                    Math.round(150 * getResources().getDisplayMetrics().density));
            DebugHelper.log("SpecsAct", "Image exists");
            image.setImageBitmap(picture);
            ThemeHelper.applyMachineImage(this, image);

            final TextView informationLabel = findViewById(R.id.information);
            specsHelper.initSound(machineID, image, informationLabel);

            // Set a long click listener
            image.setOnLongClickListener(v -> {
                Intent viewImageIntent = new Intent(SpecsActivity.this, ViewImageActivity.class);
                viewImageIntent.putExtra("machineUID", thisUID);
                startActivity(viewImageIntent);
                return true;
            });
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e,
                    "initSound", "Failed, Machine Name " + thisName);
        }
    }

    private void initLinks() {
        final ImageView link = findViewById(R.id.everymac);
        specsHelper.initLinks(machineID, thisName, link);
        ThemeHelper.applyInvertedLogo(this, link);
    }

    private void initButtons() {
        try {
            DebugHelper.log("SpecNavButtons", "Loading");
            // Reset the padding
            final LinearLayout basicInfoLayout = findViewById(R.id.basicInfoLayout);
            final float density = getResources().getDisplayMetrics().density;
            basicInfoLayout.setPadding((int) (10 * density), (int) (10 * density), (int) (10 * density), 0);

            final View buttonView = findViewById(R.id.buttonView);
            final Button previous = findViewById(R.id.buttonPrevious);
            final Button next = findViewById(R.id.buttonNext);

            // Reset the listener
            previous.setOnClickListener(null);
            next.setOnClickListener(null);

            // GONE by default, let it show up
            buttonView.setVisibility(View.VISIBLE);

            // Previous button.
            if (machineIDPosition == 0) {
                // First one, disable the prev button
                previous.setEnabled(false);
                previous.setText(getResources().getString(R.string.first_one));
            } else {
                previous.setEnabled(true);
                previous.setText(MainActivity.getMachineHelper().getIdentityName(
                        navigationUIDs[machineIDPosition - 1]));
                previous.setOnClickListener(v -> {
                    previous.setEnabled(false);
                    navPrev();
                });
            }

            // Next button.
            if (machineIDPosition == navigationUIDs.length - 1) {
                // Last one, disable the next button
                next.setEnabled(false);
                next.setText(getResources().getString(R.string.last_one));
            } else {
                next.setEnabled(true);
                next.setText(MainActivity.getMachineHelper().getIdentityName(
                        navigationUIDs[machineIDPosition + 1]));
                next.setOnClickListener(v -> {
                    next.setEnabled(false);
                    navNext();
                });
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e,
                    "SpecsActivity", "Unable to init buttons.");
        }
    }
    /* Gestures were removed since Ver. 4.5b3 */

    /* Comments Functions */
    private void initComment() {
        try {
            final TextView comment = findViewById(R.id.commentText);
            final String savedComment = UserCommentHelper.getComment(thisUID, this);
            thisComment = savedComment == null ? getString(R.string.comment_null) : savedComment;
            comment.setText(thisComment);
            comment.setOnClickListener(view -> {
                initCommentDialog();
            });
            specsHelper.initCopy(comment, thisComment, "userComment");
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "initComment",
                    "Unable to read comments.");
        }
    }

    private void initCommentDialog() {
        final View commentChunk = getLayoutInflater().inflate(R.layout.chunk_edit_comment, null);
        final EditText editComment = commentChunk.findViewById(R.id.editComment);
        final String savedComment = UserCommentHelper.getComment(thisUID, this);
        if (savedComment != null) {
            editComment.setText(savedComment);
        }

        final AlertDialog.Builder commentDialog = new AlertDialog.Builder(this);
        commentDialog.setTitle(R.string.submenu_specs_comment);
        commentDialog.setMessage(R.string.comment_tips);
        commentDialog.setView(commentChunk);
        commentDialog.setPositiveButton(R.string.link_confirm, (dialogInterface, i) -> {
            // To be overwritten...
        });
        commentDialog.setNegativeButton(R.string.link_cancel, (dialogInterface, i) -> {
            // Do nothing
        });

        final AlertDialog commentDialogCreated = commentDialog.create();
        commentDialogCreated.show();
        // Overwrite the positive button
        commentDialogCreated.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                final String inputtedString = editComment.getText().toString().trim();
                if (inputtedString.length() > 500) {
                    Log.w("commentDialog", "Input is too long.");
                    Toast.makeText(this, R.string.comment_length, Toast.LENGTH_LONG).show();
                } else {
                    UserCommentHelper.setComment(thisUID, inputtedString, this);
                    initComment();
                    commentDialogCreated.dismiss();
                }
            } catch (Exception e) {
                ExceptionHelper.handleException(this, e, "commentDialog",
                        "Unable to save comment.");
            }
        });
    }

    /* Favourites Functions */
    // Call this when trying to add to favourites.
    private void selectFolder() {
        try {
            // Check if totally empty.
            if (!isEmptyString()) {
                final View selectChunk = this.getLayoutInflater().inflate(R.layout.chunk_favourites_select, null);
                final LinearLayout selectLayout = selectChunk.findViewById(R.id.selectLayout);
                final List<UserFavouriteHelper.Folder> folders = UserFavouriteHelper.read(this);
                final boolean[] currentSelections = new boolean[folders.size()];
                for (int i = 0; i < folders.size(); i++) {
                    final boolean isExistsAtHere = folders.get(i).machineUIDs.contains(thisUID);
                    CheckBox thisCheckBox = new CheckBox(this);
                    thisCheckBox.setText(folders.get(i).name);
                    thisCheckBox.setChecked(isExistsAtHere);
                    currentSelections[i] = isExistsAtHere;
                    int finalI = i;
                    thisCheckBox.setOnCheckedChangeListener((compoundButton, b) ->
                            currentSelections[finalI] = thisCheckBox.isChecked());
                    selectLayout.addView(thisCheckBox);
                }

                // Create the dialog.
                final AlertDialog.Builder deleteDialog = new AlertDialog.Builder(this);
                deleteDialog.setTitle(R.string.submenu_specs_favourite);
                deleteDialog.setMessage(R.string.favourites_tips);
                deleteDialog.setView(selectChunk);
                deleteDialog.setPositiveButton(R.string.link_confirm, (dialog, which) -> {
                    try {
                        UserFavouriteHelper.setMembership(
                                thisUID, currentSelections, this);
                        reloadName();
                    } catch (Exception e) {
                        ExceptionHelper.handleException(this, e, "selectFolder",
                                "Unable to save favourites.");
                    }
                });
                deleteDialog.setNegativeButton(R.string.link_cancel, ((dialog, which) -> {
                    // Cancelled, do nothing
                }));
                deleteDialog.show();
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "deleteFolder",
                    "Unable to read favourites.");
        }
    }

    // Modified from the original one from the FavouriteActivity
    private boolean isEmptyString() {
        if (UserFavouriteHelper.read(this).isEmpty()) {
            createFolder();
            return true;
        } else {
            return false;
        }
    }

    // Modified from the original one from the FavouriteActivity
    private void createFolder() {
        final View newFolderChunk = getLayoutInflater().inflate(R.layout.chunk_favourites_new, null);
        final EditText folderName = newFolderChunk.findViewById(R.id.folderName);
        final AlertDialog.Builder newFolderDialog = new AlertDialog.Builder(this);
        newFolderDialog.setTitle(R.string.submenu_favourite_add);
        newFolderDialog.setMessage(R.string.favourites_new_folder);
        newFolderDialog.setView(newFolderChunk);
        newFolderDialog.setPositiveButton(R.string.link_confirm, (dialogInterface, i) -> {
            // To be overwritten...
        });
        newFolderDialog.setNegativeButton(R.string.link_cancel, (dialogInterface, i) -> {
            // Do nothing
        });

        final AlertDialog newFolderDialogCreated = newFolderDialog.create();
        newFolderDialogCreated.show();
        // Overwrite the positive button
        newFolderDialogCreated.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                final String inputtedName = folderName.getText().toString().trim();
                // Check if the input is legal
                if (FavouriteActivity.validateFolderName(inputtedName, new String[0], this)) {
                    final List<UserFavouriteHelper.Folder> folders =
                            UserFavouriteHelper.read(this);
                    folders.add(0, new UserFavouriteHelper.Folder(
                            inputtedName, new ArrayList<>()));
                    UserFavouriteHelper.write(folders, this);
                    newFolderDialogCreated.dismiss();
                    selectFolder();
                }
            } catch (Exception e) {
                ExceptionHelper.handleException(this, e, "newFolderDialog",
                        "Unable to save favourites.");
            }
        });
    }

    /* Compare Functions */
    private void addToCompare() {
        try {
            CompareActivity.toggleCompare(thisUID, this);
            initCompareCheckBox();
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "addToCompare",
                    "Unable to save comparison list.");
        }
    }

    private void initCompareCheckBox() {
        try {
            final java.util.List<String> compareNames = CompareActivity.getCompareList(this);
            final boolean containsCurrentMachine = compareNames.contains(thisUID);
            compareItem.setChecked(containsCurrentMachine);
            compareItem.setEnabled(compareNames.size() < 10 || containsCurrentMachine);
            if (compareNames.size() == 10) {
                compareItem.setTitle(getString(R.string.submenu_specs_compare) + " " + getString(R.string.compare_limit));
            } else {
                compareItem.setTitle(getString(R.string.submenu_specs_compare) + " (" + compareNames.size() + ")");
            }
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "initCompareCheckBox",
                    "Unable to read comparison list.");
        }
    }

    private void copySpecification() {
        specsHelper.copySpecification(new String[]{thisName},
                new String[][]{getSpecification()});
    }

    private void generateShareLink() {
        specsHelper.generateShareLink(thisUID);
    }

    private String[] getSpecification() {
        return new String[]{thisYear, thisModel, thisId, thisGestalt, thisOrder, thisEmc,
                thisProcessor, thisGraphics, thisDisplay, thisMaxram, thisType, thisSoftware,
                thisStorage, thisFeatures, thisExpansion, thisDesign, thisSupport, thisComment};
    }

    private void navPrev() {
        machineIDPosition--;
        refresh();
    }

    private void navNext() {
        machineIDPosition++;
        refresh();
    }

    private void refresh() {
        thisUID = navigationUIDs[machineIDPosition];
        final Intent newMachine = new Intent(SpecsActivity.this, SpecsActivity.class);
        newMachine.putExtra("machineUID", thisUID);
        newMachine.putExtra("navigationUIDs", navigationUIDs);
        startActivity(newMachine);
        finish();
    }
}

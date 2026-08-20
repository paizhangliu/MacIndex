package com.macindex.macindex;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.Lifecycle;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.macindex.macindex.catalog.Machine;
import com.macindex.macindex.catalog.MachineCatalog;
import com.macindex.macindex.resources.LogoAsset;
import com.macindex.macindex.resources.MachineResourceLoader;
import com.macindex.macindex.userstate.FavouriteFolder;
import com.macindex.macindex.userstate.UserComment;
import com.macindex.macindex.userstate.UserState;
import com.macindex.macindex.userstate.UserStateCommands;
import com.macindex.macindex.userstate.UserStateLimits;
import com.macindex.macindex.userstate.UserStateLifecycleAdapter;

public class SpecsActivity extends AppCompatActivity {

    private static final int IMAGE_BASE_SIZE = 80;

    private static final int IMAGE_MAXIMUM_SIDE = 140;

    private static final float IMAGE_MINIMUM_SCALE = 0.625f;

    private MachineCatalog catalog = null;

    private Machine machine = null;

    private String[] navigationUIDs = {};
    private boolean forceNavigationButtons = false;

    private int navigationPosition = -1;

    private SpecsHelper specsHelper = null;

    private UserStateLifecycleAdapter stateAdapter = null;

    private UserState currentState = null;

    private LifecycleMachineImageLoader imageLoader = null;

    private VolumeWarningSession volumeWarningSession = null;

    private MenuItem compareItem = null;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_specs);
        ContentInsetsHelper.apply(this);
        specsHelper = new SpecsHelper(this);
        imageLoader = new LifecycleMachineImageLoader(this, getAssets());
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        final MacIndexApplication application = (MacIndexApplication) getApplication();
        StartupUiGate.bind(this, (readyCatalog, userState) -> {
                        if (stateAdapter != null) {
                            return;
                        }
                        volumeWarningSession = application.volumeWarningSession();
                        stateAdapter = new UserStateLifecycleAdapter(
                                SpecsActivity.this,
                                userState,
                                state -> {
                                    currentState = state;
                                    if (machine == null) {
                                        initializeFromRequest(readyCatalog);
                                    } else {
                                        renderUserState();
                                    }
                                },
                                error -> ExceptionHelper.showUserStateReadFailure(
                                        SpecsActivity.this, error));
                    });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_specs, menu);
        compareItem = menu.findItem(R.id.addCompareItem);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        final boolean isReady = machine != null;
        for (int index = 0; index < menu.size(); index++) {
            final MenuItem item = menu.getItem(index);
            item.setEnabled(isReady || item.getItemId() == R.id.specsHelpItem);
        }
        if (isReady) {
            initCompareCheckBox();
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final int itemID = item.getItemId();
        if (itemID == R.id.specsHelpItem) {
            showSpecsHelp();
        } else if (itemID == R.id.shareItem) {
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

    private void showSpecsHelp() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.specs_help_title)
                .setMessage(R.string.specs_help_content)
                .setPositiveButton(R.string.help_confirm, null)
                .show();
    }

    @Override
    protected void onStop() {
        release();
        super.onStop();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (machine == null) {
            return;
        }
        initSound();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void initializeFromRequest(@NonNull final MachineCatalog readyCatalog) {
        try {
            final NavigationContract.MachineRequest request =
                    NavigationContract.MachineRequest.from(getIntent());
            catalog = readyCatalog;
            navigationUIDs = request.getNavigationUIDs();
            final String requestedUID = request.getMachineUID();
            forceNavigationButtons = request.shouldForceNavigationButtons();
            machine = catalog.requireByUid(requestedUID);

            for (int index = 0; index < navigationUIDs.length; index++) {
                catalog.requireByUid(navigationUIDs[index]);
                if (navigationUIDs[index].equals(requestedUID)) {
                    navigationPosition = index;
                }
            }
            if (navigationPosition == -1) {
                throw new IllegalArgumentException(
                        "Navigation request does not contain " + requestedUID);
            }
        } catch (IllegalArgumentException staleRequest) {
            Log.w("SpecsNavigation", "Closing a stale machine navigation request.",
                    staleRequest);
            finish();
            return;
        }

        initialize();
        findViewById(R.id.mainView).setVisibility(View.VISIBLE);
        invalidateOptionsMenu();
    }

    private void initialize() {
        if ((forceNavigationButtons
                || currentState.getPreferences().getUseNavigationButtons())
                && navigationUIDs.length > 1) {
            initButtons();
        }
        initSpecs();
        initImage();
        initLinks();
        renderUserState();
    }

    private void release() {
        if (specsHelper != null) {
            specsHelper.release();
        }
    }

    private void initSpecs() {
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
        final TextView codename = findViewById(R.id.codenameText);
        final TextView gestalt = findViewById(R.id.gestaltText);
        final TextView emc = findViewById(R.id.emcText);
        final TextView software = findViewById(R.id.softwareText);
        final TextView design = findViewById(R.id.designText);
        final TextView support = findViewById(R.id.supportText);

        this.setTitle(machine.name());
        reloadName();

        final boolean isClassic = machine.identifiers() == null
                && !"xserve".equals(machine.productTypeKey());
        findViewById(R.id.idLayout).setVisibility(isClassic ? View.GONE : View.VISIBLE);
        findViewById(R.id.idDivider).setVisibility(isClassic ? View.GONE : View.VISIBLE);
        findViewById(R.id.gestaltLayout).setVisibility(isClassic ? View.VISIBLE : View.GONE);
        findViewById(R.id.gestaltDivider).setVisibility(
                isClassic ? View.VISIBLE : View.GONE);
        findViewById(R.id.emcLayout).setVisibility(isClassic ? View.GONE : View.VISIBLE);
        findViewById(R.id.emcDivider).setVisibility(isClassic ? View.GONE : View.VISIBLE);

        type.setText(specsHelper.getDisplayInfo(machine.rom()));
        specsHelper.initCopy(type, machine.rom(), "typeInfo");
        processor.setText(specsHelper.formatModels(machine.processor(),
                machine.processorModelRanges()));
        specsHelper.initCopy(processor, machine.processor(), "processorInfo");
        maxram.setText(specsHelper.getDisplayInfo(machine.ram()));
        specsHelper.initCopy(maxram, machine.ram(), "maxramInfo");
        year.setText(machine.introductionDisplayText());
        specsHelper.initCopy(year, machine.introductionDisplayText(), "yearInfo");
        model.setText(specsHelper.getDisplayInfo(machine.modelNumbers()));
        specsHelper.initCopy(model, machine.modelNumbers(), "modelInfo");
        id.setText(specsHelper.getDisplayInfo(machine.identifiers()));
        specsHelper.initCopy(id, machine.identifiers(), "idInfo");
        graphics.setText(specsHelper.formatModels(machine.graphics(),
                machine.graphicsModelRanges()));
        specsHelper.initCopy(graphics, machine.graphics(), "graphicsInfo");
        display.setText(specsHelper.getDisplayInfo(machine.display()));
        specsHelper.initCopy(display, machine.display(), "displayInfo");
        features.setText(specsHelper.getDisplayInfo(machine.features()));
        specsHelper.initCopy(features, machine.features(), "featuresInfo");
        expansion.setText(specsHelper.getDisplayInfo(machine.expansion()));
        specsHelper.initCopy(expansion, machine.expansion(), "expansionInfo");
        storage.setText(specsHelper.getDisplayInfo(machine.storage()));
        specsHelper.initCopy(storage, machine.storage(), "storageInfo");
        specsHelper.initPartNumbers(order, machine.orderNumbers());
        specsHelper.initCopy(order, machine.orderNumbers(), "orderInfo");
        final String codenameText = machine.codenameDisplayText();
        codename.setText(specsHelper.getDisplayInfo(codenameText));
        specsHelper.initCopy(codename, codenameText, "codenameInfo");
        gestalt.setText(specsHelper.getDisplayInfo(machine.gestaltIds()));
        specsHelper.initCopy(gestalt, machine.gestaltIds(), "gestaltInfo");
        emc.setText(specsHelper.getDisplayInfo(machine.emcNumbers()));
        specsHelper.initCopy(emc, machine.emcNumbers(), "emcInfo");
        software.setText(specsHelper.getDisplayInfo(machine.software()));
        specsHelper.initCopy(software, machine.software(), "softwareInfo");
        design.setText(specsHelper.getDisplayInfo(machine.design()));
        specsHelper.initCopy(design, machine.design(), "designInfo");
        final String supportText = specsHelper.getSupportText(machine.supportStatus());
        support.setText(specsHelper.getDisplayInfo(supportText));
        specsHelper.initCopy(support, supportText, "supportInfo");
        specsHelper.setSupportColor(support, machine.supportStatus());

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
        final LogoAsset[] processorImageAssets =
                MachineResourceLoader.processorLogos(catalog, machine);

        // The type logo is independent from the model-specific logo strip.
        processorTypeImageLayout.setVisibility(View.GONE);

        final LogoAsset processorTypeAsset =
                MachineResourceLoader.processorTypeLogo(catalog, machine);
        if (processorTypeAsset != null) {
            // Got type image. Now loading.
            processorTypeImageLayout.setVisibility(View.VISIBLE);
            ThemeHelper.setLogo(this, processorTypeImage, processorTypeAsset);
        }
        bindLogoStrip(processorImageLayoutContainer, processorImageScrollView,
                processorImages, processorImageAssets);

        /*
            Graphics Images dynaLoad.

            (1) Try getting specific image. Will load if specific image(s) is/are present.
            (2) No action. The case is not applicable for the loading process.
        */
        final LinearLayout graphicsImageLayoutContainer = findViewById(R.id.graphicsImageLayoutContainer);
        final HorizontalScrollView graphicsImageScrollView = findViewById(R.id.graphicsImageScrollView);
        final LinearLayout graphicsImages = findViewById(R.id.graphicsImageLayout);
        final LogoAsset[] graphicsImageAssets =
                MachineResourceLoader.graphicsLogos(catalog, machine);

        bindLogoStrip(graphicsImageLayoutContainer, graphicsImageScrollView,
                graphicsImages, graphicsImageAssets);
    }

    @SuppressLint("InflateParams")
    private void bindLogoStrip(final LinearLayout container,
                               final HorizontalScrollView scrollView,
                               final LinearLayout imageLayout,
                               final LogoAsset[] assets) {
        container.setVisibility(View.GONE);
        imageLayout.removeAllViews();
        if (assets.length == 0) {
            return;
        }

        container.setVisibility(View.VISIBLE);
        for (LogoAsset asset : assets) {
            final View imageChunk = getLayoutInflater().inflate(R.layout.chunk_logo_image, null);
            final ImageView image = imageChunk.findViewById(R.id.logoImage);
            ThemeHelper.setLogo(this, image, asset);
            imageLayout.addView(imageChunk);
        }
        // The chunk supplies spacing between images; the final image needs no trailing gap.
        ((LinearLayout) imageLayout.getChildAt(imageLayout.getChildCount() - 1)).removeViewAt(1);
        fitImageLayout(scrollView, imageLayout);
    }

    private void fitImageLayout(final HorizontalScrollView thisScrollView,
                                final LinearLayout thisImageLayout) {
        thisScrollView.post(() -> {
            final float density = getResources().getDisplayMetrics().density;
            final int allSpacesWidth = Math.round(5 * density)
                    * (thisImageLayout.getChildCount() - 1);
            final float[] imageWidths = new float[thisImageLayout.getChildCount()];
            final float[] imageHeights = new float[thisImageLayout.getChildCount()];
            final float baseSize = IMAGE_BASE_SIZE * density;
            final float maximumSide = IMAGE_MAXIMUM_SIDE * density;
            float allImagesWidth = 0;

            for (int i = 0; i < thisImageLayout.getChildCount(); i++) {
                final ImageView thisImage = thisImageLayout.getChildAt(i)
                        .findViewById(R.id.logoImage);
                if (thisImage.getDrawable() != null
                        && thisImage.getDrawable().getIntrinsicWidth() > 0
                        && thisImage.getDrawable().getIntrinsicHeight() > 0) {
                    final float aspectRatio = (float) thisImage.getDrawable().getIntrinsicWidth()
                            / thisImage.getDrawable().getIntrinsicHeight();
                    float imageWidth = baseSize * (float) Math.sqrt(aspectRatio);
                    float imageHeight = baseSize / (float) Math.sqrt(aspectRatio);
                    final float longestSide = Math.max(imageWidth, imageHeight);
                    if (longestSide > maximumSide) {
                        final float sideScale = maximumSide / longestSide;
                        imageWidth *= sideScale;
                        imageHeight *= sideScale;
                    }
                    imageWidths[i] = imageWidth;
                    imageHeights[i] = imageHeight;
                    allImagesWidth += imageWidth;
                }
            }

            if (allImagesWidth == 0) {
                return;
            }

            final int availableWidth = thisScrollView.getWidth()
                    - thisScrollView.getPaddingLeft() - thisScrollView.getPaddingRight();
            final float availableImageWidth = Math.max(0, availableWidth - allSpacesWidth);
            final float imageScale = Math.max(IMAGE_MINIMUM_SCALE,
                    Math.min(1, availableImageWidth / allImagesWidth));

            for (int i = 0; i < thisImageLayout.getChildCount(); i++) {
                final ImageView thisImage = thisImageLayout.getChildAt(i)
                        .findViewById(R.id.logoImage);
                final ViewGroup.LayoutParams imageParams = thisImage.getLayoutParams();
                imageParams.width = Math.round(imageWidths[i] * imageScale);
                imageParams.height = Math.round(imageHeights[i] * imageScale);
                thisImage.setLayoutParams(imageParams);
            }

            thisScrollView.scrollTo(0, 0);
        });
    }

    private void reloadName() {
        final TextView name = findViewById(R.id.nameText);
        final String machineName = machine.name();
        name.setVisibility(View.INVISIBLE);

        // Reset the auto-sizing
        TextViewCompat.setAutoSizeTextTypeWithDefaults(
                name, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE);

        // Reset the Machine Name.
        name.setText(machineName);
        name.setTextSize(20);

        // Check if the star is needed.
        if (isFavourite()) {
            name.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_baseline_star_24, 0);
        } else {
            name.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }

        // Auto-sizing only if the width is insufficient.
        name.post(() -> {
            if (!name.isAttachedToWindow()) {
                return;
            }
            if (name.getLayout() != null
                    && !name.getLayout().getText().toString().equals(machineName)) {
                TextViewCompat.setAutoSizeTextTypeWithDefaults(
                        name, TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM);
            }
            name.setVisibility(View.VISIBLE);
        });

        // Set copy
        specsHelper.initCopy(name, machineName, "nameInfo");
    }

    private void initImage() {
        // Init image
        final ImageView image = findViewById(R.id.pic);
        imageLoader.load("specs-picture", machine,
                getResources().getDisplayMetrics().widthPixels,
                Math.round(150 * getResources().getDisplayMetrics().density),
                picture -> {
                    ThemeHelper.applyMachineImage(this, image);
                    image.setImageBitmap(picture);
                },
                error -> {
                    Log.w("SpecsImage", "Machine image unavailable for " + machine.uid(), error);
                    Toast.makeText(this, R.string.machine_image_unavailable,
                            Toast.LENGTH_SHORT).show();
                });

        initSound();

        // Set a long click listener
        image.setOnLongClickListener(v -> {
            startActivity(NavigationContract.machineImageIntent(
                    SpecsActivity.this, machine.uid()));
            return true;
        });
    }

    private void initSound() {
        final ImageView image = findViewById(R.id.pic);
        specsHelper.initSound(machine, image,
                currentState.getPreferences().getPlayDeathSound(),
                currentState.getPreferences().getEnableVolumeWarning(),
                volumeWarningSession);
    }

    private void initLinks() {
        final ImageView link = findViewById(R.id.everymac);
        specsHelper.initLinks(machine, link);
        ThemeHelper.applyInvertedLogo(this, link);
    }

    private void initButtons() {
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
        if (navigationPosition == 0) {
            // First one, disable the prev button
            previous.setEnabled(false);
            previous.setText(getResources().getString(R.string.first_one));
        } else {
            previous.setEnabled(true);
            previous.setText(catalog.requireByUid(
                    navigationUIDs[navigationPosition - 1]).name());
            previous.setOnClickListener(v -> {
                previous.setEnabled(false);
                navPrev();
            });
        }

        // Next button.
        if (navigationPosition == navigationUIDs.length - 1) {
            // Last one, disable the next button
            next.setEnabled(false);
            next.setText(getResources().getString(R.string.last_one));
        } else {
            next.setEnabled(true);
            next.setText(catalog.requireByUid(
                    navigationUIDs[navigationPosition + 1]).name());
            next.setOnClickListener(v -> {
                next.setEnabled(false);
                navNext();
            });
        }
    }

    /* Gestures were removed since Ver. 4.5b3 */

    private void renderUserState() {
        if (machine == null || currentState == null) {
            return;
        }
        reloadName();
        initComment();
        initCompareCheckBox();
    }

    /* Comments Functions */
    private void initComment() {
        final TextView comment = findViewById(R.id.commentText);
        final String savedComment = getSavedComment();
        comment.setText(savedComment == null ? getString(R.string.comment_null) : savedComment);
        comment.setOnClickListener(view -> initCommentDialog());
        specsHelper.initCopy(comment, savedComment, "userComment");
    }

    private void initCommentDialog() {
        final View commentChunk = getLayoutInflater().inflate(R.layout.chunk_edit_comment, null);
        final EditText editComment = commentChunk.findViewById(R.id.editComment);
        if (currentState == null || stateAdapter == null) {
            return;
        }
        final String savedComment = getSavedComment();
        if (savedComment != null) {
            editComment.setText(savedComment);
        }

        final AlertDialog.Builder commentDialog = new AlertDialog.Builder(this);
        commentDialog.setTitle(R.string.submenu_specs_comment);
        commentDialog.setMessage(R.string.comment_tips);
        commentDialog.setView(commentChunk);
        commentDialog.setPositiveButton(R.string.link_confirm, null);
        commentDialog.setNegativeButton(R.string.link_cancel, null);

        final AlertDialog commentDialogCreated = commentDialog.create();
        commentDialogCreated.show();
        // Overwrite the positive button
        commentDialogCreated.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            final String inputtedString = editComment.getText().toString().trim();
            if (inputtedString.length() > UserStateLimits.MAX_COMMENT_LENGTH) {
                editComment.setError(getString(R.string.comment_length,
                        UserStateLimits.MAX_COMMENT_LENGTH));
            } else {
                stateAdapter.execute(
                        UserStateCommands.setComment(machine.uid(), inputtedString),
                        ignored -> commentDialogCreated.dismiss(),
                        error -> ExceptionHelper.showUserStateEditFailure(this, error,
                                R.string.submenu_specs_comment,
                                R.string.comment_save_failed));
            }
        });
    }

    /* Favourites Functions */
    // Call this when trying to add to favourites.
    private void selectFolder() {
        selectFolder(null);
    }

    private void selectFolder(final FavouriteFolder newlyCreatedFolder) {
        if (currentState == null || stateAdapter == null) {
            return;
        }
        final List<FavouriteFolder> folders = new ArrayList<>(
                currentState.getLibrary().getFavouriteFolders());
        boolean containsNewFolder = false;
        for (FavouriteFolder folder : folders) {
            if (newlyCreatedFolder != null && folder.getId() == newlyCreatedFolder.getId()) {
                containsNewFolder = true;
                break;
            }
        }
        if (newlyCreatedFolder != null && !containsNewFolder) {
            folders.add(0, newlyCreatedFolder);
        }
        if (folders.isEmpty()) {
            createFolder();
            return;
        }
        final View selectChunk = getLayoutInflater().inflate(
                R.layout.chunk_favourites_select, null);
        final LinearLayout selectLayout = selectChunk.findViewById(R.id.selectLayout);
        final Set<Long> selectedFolderIds = new HashSet<>();
        for (FavouriteFolder folder : folders) {
            final boolean selected = folder.getMachineUids().contains(machine.uid());
            final CheckBox checkBox = new CheckBox(this);
            checkBox.setText(folder.getName());
            checkBox.setChecked(selected);
            if (selected) {
                selectedFolderIds.add(folder.getId());
            }
            checkBox.setOnCheckedChangeListener((unused, checked) -> {
                if (checked) {
                    selectedFolderIds.add(folder.getId());
                } else {
                    selectedFolderIds.remove(folder.getId());
                }
            });
            selectLayout.addView(checkBox);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.submenu_specs_favourite)
                .setMessage(R.string.favourites_tips)
                .setView(selectChunk)
                .setPositiveButton(R.string.link_confirm, (dialog, which) ->
                        stateAdapter.execute(
                                UserStateCommands.setFavouriteMembership(
                                        machine.uid(), selectedFolderIds),
                                ignored -> { },
                                error -> ExceptionHelper.showUserStateEditFailure(this, error,
                                        R.string.submenu_specs_favourite,
                                        R.string.favourite_membership_save_failed)))
                .setNegativeButton(R.string.link_cancel, null)
                .show();
    }

    private void createFolder() {
        if (currentState == null || stateAdapter == null) {
            return;
        }
        final List<FavouriteFolder> folders = currentState.getLibrary().getFavouriteFolders();
        if (folders.size() >= UserStateLimits.MAX_FOLDERS) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.submenu_favourite_add)
                    .setMessage(getString(R.string.favourites_error_limit,
                            UserStateLimits.MAX_FOLDERS))
                    .setPositiveButton(R.string.link_confirm, null)
                    .show();
            return;
        }
        final View newFolderChunk = getLayoutInflater().inflate(R.layout.chunk_favourites_new, null);
        final EditText folderName = newFolderChunk.findViewById(R.id.folderName);
        final AlertDialog.Builder newFolderDialog = new AlertDialog.Builder(this);
        newFolderDialog.setTitle(R.string.submenu_favourite_add);
        newFolderDialog.setMessage(R.string.favourites_new_folder);
        newFolderDialog.setView(newFolderChunk);
        newFolderDialog.setPositiveButton(R.string.link_confirm, null);
        newFolderDialog.setNegativeButton(R.string.link_cancel, null);

        final AlertDialog folderCreationDialog = newFolderDialog.create();
        folderCreationDialog.show();
        // Overwrite the positive button
        folderCreationDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            final String inputtedName = folderName.getText().toString().trim();
            if (FavouriteFolderNameValidator.validate(folderName, inputtedName, folders)) {
                folderCreationDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setEnabled(false);
                stateAdapter.execute(
                        UserStateCommands.createFavouriteFolder(inputtedName),
                        folderId -> {
                            folderCreationDialog.dismiss();
                            if (getLifecycle().getCurrentState()
                                    .isAtLeast(Lifecycle.State.STARTED)) {
                                selectFolder(new FavouriteFolder(folderId, inputtedName,
                                        Collections.emptyList()));
                            }
                        },
                        error -> {
                            folderCreationDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                    .setEnabled(true);
                            ExceptionHelper.showUserStateEditFailure(this, error,
                                    R.string.submenu_favourite_add,
                                    R.string.favourites_save_failed);
                        });
            }
        });
    }

    /* Compare Functions */
    private void addToCompare() {
        if (currentState == null || stateAdapter == null) {
            return;
        }
        final List<String> compareUIDs =
                currentState.getLibrary().getCompare().getMachineUids();
        stateAdapter.execute(
                compareUIDs.contains(machine.uid())
                        ? UserStateCommands.removeCompareMachine(machine.uid())
                        : UserStateCommands.addCompareMachine(machine.uid()),
                ignored -> { },
                error -> ExceptionHelper.showUserStateEditFailure(this, error,
                        R.string.submenu_specs_compare,
                        R.string.compare_list_save_failed));
    }

    private void initCompareCheckBox() {
        if (compareItem == null || currentState == null) {
            return;
        }
        final List<String> compareUIDs =
                currentState.getLibrary().getCompare().getMachineUids();
        final boolean containsCurrentMachine = compareUIDs.contains(machine.uid());
        compareItem.setChecked(containsCurrentMachine);
        compareItem.setEnabled(compareUIDs.size() < UserStateLimits.MAX_COMPARE_MACHINES
                || containsCurrentMachine);
        if (compareUIDs.size() == UserStateLimits.MAX_COMPARE_MACHINES) {
            compareItem.setTitle(getString(R.string.submenu_specs_compare) + " "
                    + getString(R.string.compare_limit));
        } else {
            compareItem.setTitle(getString(R.string.submenu_specs_compare) + " ("
                    + compareUIDs.size() + ")");
        }
    }

    private String getSavedComment() {
        if (currentState == null) {
            return null;
        }
        for (UserComment comment : currentState.getLibrary().getComments()) {
            if (comment.getMachineUid().equals(machine.uid())) {
                return comment.getText();
            }
        }
        return null;
    }

    private boolean isFavourite() {
        if (currentState == null) {
            return false;
        }
        for (FavouriteFolder folder : currentState.getLibrary().getFavouriteFolders()) {
            if (folder.getMachineUids().contains(machine.uid())) {
                return true;
            }
        }
        return false;
    }

    private void copySpecification() {
        specsHelper.copySpecification(new String[]{machine.name()},
                new String[][]{getSpecification()});
    }

    private void generateShareLink() {
        specsHelper.generateShareLink(machine.uid());
    }

    private String[] getSpecification() {
        return specsHelper.specification(machine, getSavedComment());
    }

    private void navPrev() {
        navigationPosition--;
        refresh();
    }

    private void navNext() {
        navigationPosition++;
        refresh();
    }

    private void refresh() {
        final String targetUID = navigationUIDs[navigationPosition];
        startActivity(NavigationContract.machineSpecsIntent(this,
                NavigationContract.MachineRequest.create(
                        targetUID, navigationUIDs, forceNavigationButtons)));
        finish();
    }
}

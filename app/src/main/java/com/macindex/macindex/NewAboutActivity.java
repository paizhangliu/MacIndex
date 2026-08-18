package com.macindex.macindex;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

public class NewAboutActivity extends AppCompatActivity {

    private ManualUpdateViewModel updateViewModel;
    private Button updateButton;
    private AlertDialog updateDialog;
    private UpdateCheckState renderedState;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_about);
        ContentInsetsHelper.apply(this);
        setTitle(R.string.menu_about);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        final String versionString = getString(R.string.version_information_general,
                BuildConfig.VERSION_NAME + (BuildConfig.DEBUG ? "-Debug" : ""));
        ((TextView) findViewById(R.id.versionText)).setText(versionString);
        ThemeHelper.applyDarkAppLogo(this, findViewById(R.id.appLogo));
        ThemeHelper.applyMonochromeLogo(this, findViewById(R.id.cs125Logo));

        OssLicensesMenuActivity.setActivityTitle(getString(R.string.about_opensource));
        findViewById(R.id.openSourceButton).setOnClickListener(v -> openOpenSourceLicenses());
        findViewById(R.id.releaseHistoryButton).setOnClickListener(v ->
                LinkLoadingHelper.startBrowser("https://macindex.paizhang.info/#版本", this));
        findViewById(R.id.githubLogo).setOnClickListener(v ->
                LinkLoadingHelper.startBrowser("https://github.com/paizhangliu/MacIndex", this));
        findViewById(R.id.cs125Logo).setOnClickListener(v ->
                LinkLoadingHelper.startBrowser("https://macindex.paizhang.info/#故事", this));
        findViewById(R.id.paizhangLogo).setOnClickListener(v ->
                LinkLoadingHelper.startBrowser("https://paizhang.info/", this));

        updateButton = findViewById(R.id.updateButton);
        updateViewModel = new ViewModelProvider(this).get(ManualUpdateViewModel.class);
        updateViewModel.getState().observe(this, this::renderUpdateState);
        updateButton.setOnClickListener(v -> updateViewModel.check(BuildConfig.VERSION_NAME));
    }

    private void renderUpdateState(final UpdateCheckState state) {
        renderedState = state;
        updateUpdateButton();
        if (state == null || !state.isTerminal() || updateDialog != null
                || isFinishing() || isDestroyed()) {
            return;
        }
        if (state.getStatus() == UpdateCheckState.Status.FAILED) {
            Log.w("ManualUpdate", "Unable to check for updates.", state.getError());
        }
        updateDialog = UpdateDialogPresenter.show(this, state, false,
                new UpdateDialogPresenter.Listener() {
                    @Override
                    public void onOpen(final UpdateChecker.Information information) {
                        LinkLoadingHelper.startBrowser(information.getReleasePage(),
                                NewAboutActivity.this);
                        acknowledgeUpdate(state);
                    }

                    @Override
                    public void onAcknowledge() {
                        acknowledgeUpdate(state);
                    }

                    @Override
                    public void onRetry() {
                        acknowledgeUpdate(state);
                        updateViewModel.check(BuildConfig.VERSION_NAME);
                    }
                });
    }

    private void openOpenSourceLicenses() {
        try {
            startActivity(new Intent(this, OssLicensesMenuActivity.class));
        } catch (ActivityNotFoundException | SecurityException expectedFailure) {
            Log.w("OpenSourceLicenses", "Unable to open open-source licenses.",
                    expectedFailure);
            ExceptionHelper.showMessageDialog(this, R.string.about_opensource,
                    R.string.open_source_licenses_failed);
        }
    }

    private void acknowledgeUpdate(final UpdateCheckState state) {
        updateDialog = null;
        updateViewModel.acknowledge(state);
    }

    private void updateUpdateButton() {
        if (updateButton == null) {
            return;
        }
        final boolean checking = renderedState != null
                && renderedState.getStatus() == UpdateCheckState.Status.CHECKING;
        updateButton.setText(checking ? R.string.loading_update : R.string.about_update);
        updateButton.setEnabled(!checking);
    }

    @Override
    protected void onDestroy() {
        if (updateDialog != null) {
            updateDialog.setOnCancelListener(null);
            updateDialog.dismiss();
            updateDialog = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

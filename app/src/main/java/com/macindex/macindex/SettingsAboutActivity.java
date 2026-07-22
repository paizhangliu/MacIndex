package com.macindex.macindex;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Calendar;

public class SettingsAboutActivity extends AppCompatActivity {

    private Thread benchmarkThread = null;

    private ProgressDialog waitDialog = null;

    private boolean benchmarkStopped = false;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_about);
        WindowInsetsHelper.apply(this);
        this.setTitle(getResources().getString(R.string.menu_about_settings));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        MainActivity.validateOperation(this);
        initSettings();

        if (savedInstanceState != null) {
            if (!savedInstanceState.getBoolean("benchmarkComplete", true)) {
                interruptBenchmarkDialog();
            }
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        // Check if benchmark is interrupted
        if (benchmarkThread != null && !benchmarkStopped) {
            Log.e("Benchmark", "Terminated due to activity restart.");
            stopBenchmark(true);
            interruptBenchmarkDialog();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Check if benchmark is interrupted
        if (benchmarkThread != null && !benchmarkStopped) {
            Log.e("Benchmark", "Terminated due to activity destruction.");
            stopBenchmark(true);
            outState.putBoolean("benchmarkComplete", false);
        } else {
            outState.putBoolean("benchmarkComplete", true);
        }
    }

    @Override
    protected void onDestroy() {
        if (benchmarkThread != null && !benchmarkStopped) {
            stopBenchmark(true);
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_prefs, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final int itemID = item.getItemId();
        if (itemID == R.id.benchmarkItem) {
            startBenchmark();
        } else if (itemID == R.id.clearPrefsItem) {
            final AlertDialog.Builder defaultsWarningDialog = new AlertDialog.Builder(SettingsAboutActivity.this);
            defaultsWarningDialog.setTitle(R.string.submenu_prefs_clear);
            defaultsWarningDialog.setMessage(R.string.setting_defaults_warning_content);
            defaultsWarningDialog.setPositiveButton(R.string.link_confirm, (dialogInterface, i) -> PrefsHelper.clearPrefs(this));
            defaultsWarningDialog.setNegativeButton(R.string.link_cancel, (dialogInterface, i) -> {
                // Cancelled, nothing to do.
            });
            defaultsWarningDialog.show();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }



    private void initSettings() {
        final SwitchMaterial swSort = findViewById(R.id.switchSort);
        final SwitchMaterial swSortComment = findViewById(R.id.switchSortComment);
        final SwitchMaterial swEveryMac = findViewById(R.id.switchEveryMac);
        final SwitchMaterial swDeathSound = findViewById(R.id.switchDeathSound);
        final SwitchMaterial swNavButtons = findViewById(R.id.switchNavButtons);
        final SwitchMaterial swQuickNav = findViewById(R.id.switchQuickNav);
        final SwitchMaterial swRandomAll = findViewById(R.id.switchRandomAll);
        final SwitchMaterial swSaveMainUsage = findViewById(R.id.switchSaveMainUsage);
        final SwitchMaterial swSaveSearchUsage = findViewById(R.id.switchSaveSearchUsage);
        final SwitchMaterial swSaveCompareUsage = findViewById(R.id.switchSaveCompareUsage);
        final SwitchMaterial swVolWarning = findViewById(R.id.switchVolWarning);
        final SwitchMaterial swOpenDirectly = findViewById(R.id.switchOpenDirectly);

        swSort.setChecked(PrefsHelper.getBooleanPrefs("isSortAgain", this));
        swSortComment.setChecked(PrefsHelper.getBooleanPrefs("isSortComment", this));
        final Boolean everyMacSelection = PrefsHelper.getBooleanPrefs("isOpenEveryMac", this);
        swEveryMac.setChecked(everyMacSelection);
        swDeathSound.setChecked(PrefsHelper.getBooleanPrefs("isPlayDeathSound", this));
        swNavButtons.setChecked(PrefsHelper.getBooleanPrefs("isUseNavButtons", this));
        swQuickNav.setChecked(PrefsHelper.getBooleanPrefs("isFixedNav", this));
        swRandomAll.setChecked(PrefsHelper.getBooleanPrefs("isRandomAll", this));
        swSaveMainUsage.setChecked(PrefsHelper.getBooleanPrefs("isSaveMainUsage", this));
        swSaveSearchUsage.setChecked(PrefsHelper.getBooleanPrefs("isSaveSearchUsage", this));
        swSaveCompareUsage.setChecked(PrefsHelper.getBooleanPrefs("isSaveCompareUsage", this));
        swVolWarning.setChecked(PrefsHelper.getBooleanPrefs("isEnableVolWarning", this));
        swOpenDirectly.setChecked(PrefsHelper.getBooleanPrefs("isOpenDirectly", this));

        swSort.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    PrefsHelper.editPrefs("isSortAgain", isChecked, this);
                    PrefsHelper.editPrefs("isReloadNeeded", true, this);
                });
        swSortComment.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isSortComment", isChecked, this));
        swDeathSound.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isPlayDeathSound", isChecked, this));
        swNavButtons.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isUseNavButtons", isChecked, this));
        swQuickNav.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isFixedNav", isChecked, this));
        swRandomAll.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isRandomAll", isChecked, this));
        swSaveMainUsage.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isSaveMainUsage", isChecked, this));
        swSaveSearchUsage.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isSaveSearchUsage", isChecked, this));
        swSaveCompareUsage.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isSaveCompareUsage", isChecked, this));
        swVolWarning.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isEnableVolWarning", isChecked, this));
        swOpenDirectly.setOnCheckedChangeListener((buttonView, isChecked) -> PrefsHelper.editPrefs("isOpenDirectly", isChecked, this));

        // If EveryMac is checked, disable following settings.
        if (everyMacSelection) {
            swSortComment.setEnabled(false);
            swDeathSound.setEnabled(false);
            swNavButtons.setEnabled(false);
            swQuickNav.setEnabled(false);
            swRandomAll.setEnabled(false);
            swVolWarning.setEnabled(false);
            swSaveCompareUsage.setEnabled(false);
        } else {
            swSortComment.setEnabled(true);
            swDeathSound.setEnabled(true);
            swNavButtons.setEnabled(true);
            swQuickNav.setEnabled(true);
            swRandomAll.setEnabled(true);
            swVolWarning.setEnabled(true);
            swSaveCompareUsage.setEnabled(true);
        }

        swEveryMac.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                final AlertDialog.Builder everyMacWarningDialog = new AlertDialog.Builder(SettingsAboutActivity.this);
                everyMacWarningDialog.setTitle(R.string.setting_everymac);
                everyMacWarningDialog.setMessage(R.string.setting_everymac_warning_content);
                everyMacWarningDialog.setPositiveButton(R.string.link_confirm, (dialogInterface, i) -> {
                    PrefsHelper.editPrefs("isOpenEveryMac", true, this);
                    initSettings();
                });
                everyMacWarningDialog.setNegativeButton(R.string.link_cancel, (dialogInterface, i) -> swEveryMac.setChecked(false));
                everyMacWarningDialog.show();
            } else {
                PrefsHelper.editPrefs("isOpenEveryMac", false, this);
                initSettings();
            }
        });
    }

    private void startBenchmark() {
        final AlertDialog.Builder benchmarkWarningDialog = new AlertDialog.Builder(this);
        benchmarkWarningDialog.setTitle(R.string.submenu_prefs_benchmark);
        benchmarkWarningDialog.setMessage(R.string.benchmark_warning);
        benchmarkWarningDialog.setPositiveButton(R.string.link_confirm, (dialogInterface, i) -> {
            Log.i("Benchmark", "Benchmark started at " + System.currentTimeMillis());
            final long[] benchmarkTimer = {System.currentTimeMillis(), 0};
            waitDialog = new ProgressDialog(this);
            waitDialog.setTitle(R.string.submenu_prefs_benchmark);
            waitDialog.setMessage(getString(R.string.loading_benchmark));
            waitDialog.setCancelable(false);
            waitDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.link_cancel), (dialog, which) -> {
                // To be rewritten
            });
            waitDialog.show();

            // Rewrite negative button
            waitDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> {
                Log.e("Benchmark", "Terminated due to the user.");
                stopBenchmark(true);
            });
            benchmarkStopped = false;
            benchmarkThread = new Thread() {
                @Override
                public void run() {
                    int[] benchTemp = MainActivity.getMachineHelper().searchHelper("sname", "a", "all",
                            false, false);
                    Log.i("Benchmark", "Benchmark Stage 1 ended at " + System.currentTimeMillis());
                    benchmarkTimer[0] = System.currentTimeMillis() - benchmarkTimer[0];
                    benchmarkTimer[1] = System.currentTimeMillis();
                    MainActivity.getMachineHelper().directSortByYear(benchTemp);
                    Log.i("Benchmark", "Benchmark Stage 2 ended at " + System.currentTimeMillis());
                    benchmarkTimer[1] = System.currentTimeMillis() - benchmarkTimer[1];
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                                    && isDestroyed())) {
                                return;
                            }
                            try {
                                if (!benchmarkStopped) {
                                    Log.w("Benchmark", "Terminated normally.");
                                    stopBenchmark(false);
                                    // Compose result message
                                    final String resultInfo = "Generated: " + Calendar.getInstance().getTime() + "\n"
                                            + "MacIndex Version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                                            + "Android Version: " + Build.VERSION.RELEASE + "\n"
                                            + "Hardware Model: " + Build.BRAND + " " + Build.MODEL + "\n"
                                            + "Processor Type: " + Build.SUPPORTED_ABIS[0] + "\n"
                                            + "Database Reading: " + benchmarkTimer[0] + "\n"
                                            + "Enhanced Sorting: " + benchmarkTimer[1] + "\n"
                                            + "Overall Result: " + (benchmarkTimer[0] + benchmarkTimer[1]) + "\n";

                                    // Construct result dialog box
                                    final AlertDialog.Builder resultDialog = new AlertDialog.Builder(SettingsAboutActivity.this);
                                    resultDialog.setTitle(R.string.submenu_prefs_benchmark);
                                    resultDialog.setMessage(R.string.benchmark_result);
                                    resultDialog.setCancelable(false);
                                    resultDialog.setPositiveButton(R.string.link_confirm, (dialogInterface, i) -> {
                                        // Do nothing
                                    });
                                    resultDialog.setNeutralButton(R.string.error_copy_button, (dialogInterface, i) -> {
                                        // To be overwritten
                                    });

                                    final View infoChunk = getLayoutInflater().inflate(R.layout.chunk_exception_dialog, null);
                                    final TextView resultInfoBox = infoChunk.findViewById(R.id.exceptionInfo);

                                    resultInfoBox.setText(resultInfo);
                                    resultDialog.setView(infoChunk);

                                    final AlertDialog exceptionDialogCreated = resultDialog.create();
                                    exceptionDialogCreated.show();

                                    // Override the negative button
                                    exceptionDialogCreated.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                                        ClipboardManager clipboard = (ClipboardManager) SettingsAboutActivity.this.getSystemService(Context.CLIPBOARD_SERVICE);
                                        ClipData clip = ClipData.newPlainText("ExceptionInfo", resultInfo);
                                        clipboard.setPrimaryClip(clip);
                                        Toast.makeText(SettingsAboutActivity.this,
                                                MainActivity.getRes().getString(R.string.error_copy_information), Toast.LENGTH_LONG).show();

                                    });

                                    // Low performance warning dialog
                                    if ((benchmarkTimer[0] + benchmarkTimer[1]) >= 100000
                                            && PrefsHelper.getBooleanPrefs("isSortAgain", SettingsAboutActivity.this)) {
                                        final AlertDialog.Builder performanceWarningDialog = new AlertDialog.Builder(SettingsAboutActivity.this);
                                        performanceWarningDialog.setTitle(R.string.submenu_prefs_benchmark);
                                        performanceWarningDialog.setMessage(R.string.benchmark_advice);
                                        performanceWarningDialog.setPositiveButton(R.string.link_confirm, (dialogInterface, i) -> {
                                            PrefsHelper.editPrefs("isSortAgain", false, SettingsAboutActivity.this);
                                            initSettings();
                                        });
                                        performanceWarningDialog.setNegativeButton(R.string.link_cancel, (dialogInterface, i) -> {
                                            // Cancelled, nothing to do.
                                        });
                                        performanceWarningDialog.show();
                                    }

                                    // High performance warning dialog
                                    if ((benchmarkTimer[0] + benchmarkTimer[1]) < 50000
                                            && !PrefsHelper.getBooleanPrefs("isSortAgain", SettingsAboutActivity.this)) {
                                        final AlertDialog.Builder performanceWarningDialog = new AlertDialog.Builder(SettingsAboutActivity.this);
                                        performanceWarningDialog.setTitle(R.string.submenu_prefs_benchmark);
                                        performanceWarningDialog.setMessage(R.string.benchmark_high);
                                        performanceWarningDialog.setPositiveButton(R.string.link_confirm, (dialogInterface, i) -> {
                                            PrefsHelper.editPrefs("isSortAgain", true, SettingsAboutActivity.this);
                                            initSettings();
                                        });
                                        performanceWarningDialog.setNegativeButton(R.string.link_cancel, (dialogInterface, i) -> {
                                            // Cancelled, nothing to do.
                                        });
                                        performanceWarningDialog.show();
                                    }
                                } else {
                                    Log.w("Benchmark", "Terminated abnormally.");
                                }
                            } catch (final Exception e) {
                                ExceptionHelper.handleException(SettingsAboutActivity.this, e, null, null);
                            }
                        }
                    });
                }
            };
            benchmarkThread.start();
        });
        benchmarkWarningDialog.setNegativeButton(R.string.link_cancel, (dialogInterface, i) -> {
            // Cancelled, nothing to do.
        });
        benchmarkWarningDialog.show();
    }

    private void stopBenchmark(final boolean isReloadRequired) {
        Log.w("Benchmark", "Stopping.");
        benchmarkStopped = true;
        final Thread threadToStop = benchmarkThread;
        if (threadToStop != null && threadToStop != Thread.currentThread()) {
            threadToStop.interrupt();
        }
        if (waitDialog != null && waitDialog.isShowing()) {
            waitDialog.dismiss();
        }
        waitDialog = null;
        benchmarkThread = null;
    }

    private void interruptBenchmarkDialog() {
        final AlertDialog.Builder interruptDialog = new AlertDialog.Builder(this);
        interruptDialog.setTitle(R.string.submenu_prefs_benchmark);
        interruptDialog.setMessage(R.string.benchmark_error);
        interruptDialog.setPositiveButton(R.string.link_confirm, (dialogInterface, i) -> {
            // Confirmed
        });
        interruptDialog.show();
    }
}

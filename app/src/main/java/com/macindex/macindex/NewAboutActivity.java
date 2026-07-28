package com.macindex.macindex;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

import android.widget.TextView;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

import java.text.DateFormat;
import java.util.Date;

public class NewAboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_about);
        WindowInsetsHelper.apply(this);

        this.setTitle(getResources().getString(R.string.menu_about));

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (!MainActivity.validateOperation(this)) {
            return;
        }

        try {
            // Get build time information
            DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
            Date buildDate = new Date();
            buildDate.setTime(BuildConfig.TIMESTAMP);

            String versionString = getString(R.string.version_information_general,
                    BuildConfig.VERSION_NAME + (BuildConfig.DEBUG ? "-Debug" : ""))
                    + " · " + dateFormat.format(buildDate);
            ((TextView) findViewById(R.id.versionText)).setText(versionString);

            OssLicensesMenuActivity.setActivityTitle(getString(R.string.about_opensource));
            findViewById(R.id.openSourceButton).setOnClickListener(v ->
                    startActivity(new Intent(NewAboutActivity.this, OssLicensesMenuActivity.class)));
            findViewById(R.id.updateButton).setOnClickListener(v ->
                    UpdateHelper.checkManually(this));
            findViewById(R.id.releaseHistoryButton).setOnClickListener(v -> {
                LinkLoadingHelper.startBrowser("https://macindex.paizhang.info/#版本", this);
            });
            findViewById(R.id.githubLogo).setOnClickListener(v -> {
                LinkLoadingHelper.startBrowser("https://github.com/paizhangliu/MacIndex", this);
            });
            findViewById(R.id.cs125Logo).setOnClickListener(v -> {
                LinkLoadingHelper.startBrowser("https://macindex.paizhang.info/#故事", this);
            });
            findViewById(R.id.paizhangLogo).setOnClickListener(v -> {
                LinkLoadingHelper.startBrowser("https://paizhang.info/", this);
            });
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "NewAboutActivity", "Failed to fetch information.");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

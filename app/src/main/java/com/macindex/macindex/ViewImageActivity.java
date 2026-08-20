package com.macindex.macindex;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.macindex.macindex.catalog.Machine;
import com.macindex.macindex.catalog.MachineCatalog;

/** Displays the full machine image resolved from a stable catalog UID. */
public class ViewImageActivity extends AppCompatActivity {

    private LifecycleMachineImageLoader imageLoader;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_image);
        ContentInsetsHelper.apply(this);
        imageLoader = new LifecycleMachineImageLoader(this, getAssets());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        StartupUiGate.bind(this,
                (catalog, userState) -> initializeFromRequest(catalog));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void initializeFromRequest(@NonNull final MachineCatalog catalog) {
        final String machineUID = getIntent().getStringExtra(
                NavigationContract.EXTRA_MACHINE_UID);
        if (machineUID == null) {
            throw new IllegalArgumentException("Missing machine image UID");
        }
        init(catalog, catalog.requireByUid(machineUID));
    }

    private void init(@NonNull final MachineCatalog catalog,
                      @NonNull final Machine machine) {
        setTitle(machine.name());
        final ImageView image = findViewById(R.id.pic);
        imageLoader.load("full-picture", machine,
                getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels,
                picture -> {
                    image.setImageBitmap(picture);
                    ThemeHelper.applyMachineImage(this, image);
                },
                error -> {
                    Log.e("MachineImage", "Unable to load image for " + machine.uid(), error);
                    ExceptionHelper.showToast(this, R.string.machine_image_unavailable);
                    finish();
                });
    }

}

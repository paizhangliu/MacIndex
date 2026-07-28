package com.macindex.macindex;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;

/**
 * MacIndex View Image Activity
 * Jul. 6, 2021
 */
public class ViewImageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_image);
        WindowInsetsHelper.apply(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (!MainActivity.validateOperation(this)) {
            return;
        }

        try {
            final Intent intent = getIntent();
            final int machineID = intent.getIntExtra("machineID", -1);
            if (machineID == -1) {
                throw new IllegalArgumentException();
            }
            init(machineID);
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, "ViewImageActivity", "Illegal Machine ID.");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void init(final int machineID) {
        try {
            setTitle(MainActivity.getMachineHelper().getName(machineID));
            final ImageView image = findViewById(R.id.pic);
            final Bitmap picture = MainActivity.getMachineHelper().getPicture(machineID);
            DebugHelper.log("SpecsAct", "Image exists");
            image.setImageBitmap(picture);
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, null, null);
        }
    }
}

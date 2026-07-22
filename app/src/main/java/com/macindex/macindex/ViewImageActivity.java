package com.macindex.macindex;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import java.io.File;

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

        MainActivity.validateOperation(this);

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
            final File imageFile = MainActivity.getMachineHelper().getPicture(machineID, ViewImageActivity.this);
            if (imageFile.exists()) {
                Log.i("SpecsAct", "Image exists");
                image.setImageBitmap(BitmapFactory.decodeFile(imageFile.getPath()));
            }
            imageFile.delete();
        } catch (Exception e) {
            ExceptionHelper.handleException(this, e, null, null);
        }
    }
}

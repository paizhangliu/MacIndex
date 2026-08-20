package com.macindex.macindex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.MediaPlayer;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.macindex.macindex.catalog.Machine;
import com.macindex.macindex.catalog.MachineCatalog;
import com.macindex.macindex.resources.LogoAsset;
import com.macindex.macindex.resources.MachineResourceLoader;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Verifies with Android's real decoders that packaged media can be used by the app. */
@RunWith(AndroidJUnit4.class)
public final class PackagedCatalogInstrumentationTest {

    @Test
    public void packagedCatalogAndResourcesAreClosed() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final MachineCatalog catalog = StartupTestCatalog.get(context);
        assertFalse(catalog.machines().isEmpty());

        final Map<String, Machine> pictureOwners = new LinkedHashMap<>();
        final Set<String> logoAssets = new HashSet<>();
        final Set<String> soundAssets = new HashSet<>();
        for (Machine machine : catalog.machines()) {
            if (!pictureOwners.containsKey(machine.pictureAssetKey())) {
                pictureOwners.put(machine.pictureAssetKey(), machine);
            }
            final LogoAsset processorTypeLogo =
                    MachineResourceLoader.processorTypeLogo(catalog, machine);
            if (processorTypeLogo != null) {
                logoAssets.add(processorTypeLogo.assetPath());
            }
            addResources(logoAssets, MachineResourceLoader.processorLogos(catalog, machine));
            addResources(logoAssets, MachineResourceLoader.graphicsLogos(catalog, machine));
            final String startupSound = MachineResourceLoader.startupSoundAsset(machine);
            if (startupSound != null) {
                soundAssets.add(startupSound);
            }
            final String deathSound = MachineResourceLoader.deathSoundAsset(machine);
            if (deathSound != null) {
                soundAssets.add(deathSound);
            }
        }

        for (Machine owner : pictureOwners.values()) {
            final Bitmap picture = BitmapLoadingHelper.decodeSampledBitmapFromAsset(
                    context.getAssets(), MachineResourceLoader.pictureAsset(owner), 32, 32);
            assertTrue("Undecodable picture " + owner.uid(), picture != null);
            assertTrue(picture.getWidth() > 0 && picture.getHeight() > 0);
            picture.recycle();
        }
        for (String asset : logoAssets) {
            final Bitmap logo = BitmapLoadingHelper.decodeSampledBitmapFromAsset(
                    context.getAssets(), asset, 200, 200);
            assertTrue("Undecodable logo " + asset, logo != null);
            assertTrue(logo.getWidth() > 0 && logo.getHeight() > 0);
            logo.recycle();
        }
        for (String asset : soundAssets) {
            final MediaPlayer player = new MediaPlayer();
            try (AssetFileDescriptor descriptor = context.getAssets().openFd(asset)) {
                player.setDataSource(descriptor.getFileDescriptor(),
                        descriptor.getStartOffset(), descriptor.getLength());
                player.prepare();
            } finally {
                player.release();
            }
        }
    }

    private static void addResources(final Set<String> resources,
                                     final LogoAsset[] mappedResources) {
        for (LogoAsset resource : mappedResources) {
            resources.add(resource.assetPath());
        }
    }
}

package com.macindex.macindex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaPlayer;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.macindex.macindex.catalog.CatalogLoader;
import com.macindex.macindex.catalog.Machine;
import com.macindex.macindex.catalog.MachineCatalog;
import com.macindex.macindex.resources.LogoAsset;
import com.macindex.macindex.resources.MachineAssetLoader;
import com.macindex.macindex.resources.MachineResourceRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Device-level proof that the packaged catalog and its resources form a closed set. */
@RunWith(AndroidJUnit4.class)
public final class PackagedCatalogInstrumentationTest {

    @Test
    public void packagedCatalogAndResourcesAreClosed() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final Set<String> rootAssets = new HashSet<>(
                Arrays.asList(context.getAssets().list("")));
        assertTrue(rootAssets.contains(CatalogLoader.ASSET_NAME));
        assertFalse(rootAssets.contains("specs.db"));

        final MachineCatalog catalog = CatalogLoader.load(context.getAssets());
        assertFalse(catalog.machines().isEmpty());

        final Set<String> expectedPictures = new HashSet<>();
        final Map<String, Machine> pictureOwners = new LinkedHashMap<>();
        final Set<Integer> drawableResources = new HashSet<>();
        final Set<Integer> soundResources = new HashSet<>();
        for (Machine machine : catalog.machines()) {
            assertEquals(machine.uid(), machine, catalog.findByUid(machine.uid()));
            expectedPictures.add(machine.pictureAssetKey() + ".webp");
            if (!pictureOwners.containsKey(machine.pictureAssetKey())) {
                pictureOwners.put(machine.pictureAssetKey(), machine);
            }
            final LogoAsset processorTypeLogo =
                    MachineResourceRegistry.processorTypeLogo(machine);
            assertEquals(machine.processorFamilyKeys().isEmpty(), processorTypeLogo == null);
            if (processorTypeLogo != null) {
                drawableResources.add(processorTypeLogo.drawableRes());
            }
            addResources(drawableResources, MachineResourceRegistry.processorLogos(machine));
            addResources(drawableResources, MachineResourceRegistry.graphicsLogos(machine));
            for (int sound : MachineResourceRegistry.soundResources(machine)) {
                if (sound != 0) {
                    soundResources.add(sound);
                }
            }
        }
        final Set<String> packagedPictures = new HashSet<>(
                Arrays.asList(context.getAssets().list("machines")));
        assertEquals(expectedPictures, packagedPictures);

        for (Machine owner : pictureOwners.values()) {
            final Bitmap picture = MachineAssetLoader.loadPicture(
                    context.getAssets(), owner, 32, 32);
            assertTrue(picture.getWidth() > 0 && picture.getHeight() > 0);
            picture.recycle();
        }
        for (int drawable : drawableResources) {
            final Bitmap logo = BitmapLoadingHelper.decodeSampledBitmapFromResource(
                    context.getResources(), drawable, 200, 200);
            assertNotNull("Undecodable drawable " + drawable, logo);
            assertTrue(logo.getWidth() > 0 && logo.getHeight() > 0);
            assertTrue("Oversized decoded drawable " + drawable,
                    logo.getAllocationByteCount() <= 2 * 1024 * 1024);
            logo.recycle();
        }
        for (int sound : soundResources) {
            final MediaPlayer player = MediaPlayer.create(context, sound);
            assertNotNull("Undecodable sound " + sound, player);
            player.release();
        }

        assertFalse(rootAssets.contains("old_machine_names.json"));
        assertEquals("MI000001", catalog.resolveLegacyName("  Macintosh 128K  ").uid());
        assertNull(catalog.resolveLegacyName("Macintosh"));
    }

    private static void addResources(final Set<Integer> resources,
                                     final LogoAsset[] mappedResources) {
        for (LogoAsset resource : mappedResources) {
            assertTrue(resource.drawableRes() != 0);
            resources.add(resource.drawableRes());
        }
    }
}

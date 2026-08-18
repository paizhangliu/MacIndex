package com.macindex.macindex.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.macindex.macindex.catalog.proto.CatalogExternalLink;
import com.macindex.macindex.catalog.proto.CatalogIdentity;
import com.macindex.macindex.catalog.proto.CatalogMachine;
import com.macindex.macindex.catalog.proto.CatalogPayload;
import com.macindex.macindex.catalog.proto.CatalogTextRange;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Verifies that loading preserves every field in the generated catalog payload. */
public final class MachineDomainParityTest {

    @Test
    public void loadedModelPreservesEveryCatalogField() throws Exception {
        final byte[] bytes = Files.readAllBytes(requiredPath("macindex.catalog.path"));
        final CatalogPayload payload = CatalogPayload.parseFrom(bytes);
        final MachineCatalog catalog = CatalogLoader.load(bytes);

        assertEquals(payload.getMachinesCount(), catalog.machines().size());
        for (int index = 0; index < payload.getMachinesCount(); index++) {
            assertMachineEquals(payload.getMachines(index), catalog.machines().get(index));
        }
    }

    private static void assertMachineEquals(final CatalogMachine source,
                                            final Machine machine) {
        assertEquals(source.getUid(), machine.uid());
        assertEquals(source.getManufacturerKey(), machine.manufacturerKey());
        assertEquals(source.getProductTypeKey(), machine.productTypeKey());
        assertEquals(source.getPictureAssetKey(), machine.pictureAssetKey());
        assertEquals(source.getNames(0).getValue(), machine.name());

        assertEquals(source.getIntroductionsCount(), machine.introductions().size());
        for (int index = 0; index < source.getIntroductionsCount(); index++) {
            assertEquals(source.getIntroductions(index).getYear(),
                    machine.introductions().get(index).year());
            assertEquals(introductionDisplay(source.getIntroductions(index)),
                    machine.introductions().get(index).displayText());
        }

        assertEquals(identityDisplayText(source.getModelNumbersList()),
                machine.modelNumbers());
        assertEquals(identityDisplayText(source.getIdentifiersList()),
                machine.identifiers());
        assertEquals(identityDisplayText(source.getGestaltIdsList()),
                machine.gestaltIds());
        assertEquals(partNumberDisplayText(source.getOrderNumbersList()),
                machine.orderNumbers());
        assertEquals(identityDisplayText(source.getCodenamesList()),
                machine.codenameDisplayText());
        assertEquals(identityDisplayText(source.getEmcNumbersList()),
                machine.emcNumbers());

        assertEquals(nullable(source.hasProcessor(), source.getProcessor()), machine.processor());
        assertEquals(source.getProcessorFamilyKeysList(), machine.processorFamilyKeys());
        assertEquals(source.getProcessorLogoKeysList(), machine.processorLogoKeys());
        assertRanges(source.getProcessorModelRangesList(), machine.processorModelRanges());
        assertEquals(nullable(source.hasGraphics(), source.getGraphics()), machine.graphics());
        assertEquals(source.getGraphicsLogoKeysList(), machine.graphicsLogoKeys());
        assertRanges(source.getGraphicsModelRangesList(), machine.graphicsModelRanges());

        assertEquals(nullable(source.hasDisplay(), source.getDisplay()), machine.display());
        assertEquals(nullable(source.hasRam(), source.getRam()), machine.ram());
        assertEquals(nullable(source.hasRom(), source.getRom()), machine.rom());
        assertEquals(nullable(source.hasSoftware(), source.getSoftware()), machine.software());
        assertEquals(nullable(source.hasStorage(), source.getStorage()), machine.storage());
        assertEquals(nullable(source.hasFeatures(), source.getFeatures()), machine.features());
        assertEquals(nullable(source.hasExpansion(), source.getExpansion()), machine.expansion());
        assertEquals(source.getDesign(), machine.design());
        assertEquals(stripPrefix(source.getSupportStatus().name(), "CATALOG_SUPPORT_STATUS_"),
                machine.supportStatus().name());
        assertEquals(stripPrefix(source.getSoundProfile().name(), "CATALOG_SOUND_PROFILE_"),
                machine.soundProfile().name());

        final List<ExternalLink> links = new ArrayList<>();
        for (CatalogExternalLink link : source.getLinksList()) {
            links.add(new ExternalLink(link.getLabel(), link.getUrl()));
        }
        assertEquals(links, machine.links());
    }

    private static String identityDisplayText(final List<CatalogIdentity> values) {
        if (values.isEmpty()) {
            return null;
        }
        final List<String> displayValues = new ArrayList<>();
        for (CatalogIdentity value : values) {
            displayValues.add(identityDisplay(value));
        }
        return String.join("\n", displayValues);
    }

    private static String identityDisplay(final CatalogIdentity value) {
        return value.hasQualifier()
                ? value.getValue() + " (" + value.getQualifier() + ")"
                : value.getValue();
    }

    private static String partNumberDisplayText(final List<CatalogIdentity> values) {
        if (values.isEmpty()) {
            return null;
        }
        final List<String> displayValues = new ArrayList<>();
        for (CatalogIdentity value : values) {
            final String revisions = value.getRevisionsList().stream()
                    .map(revision -> value.getValue() + "*/" + revision)
                    .collect(java.util.stream.Collectors.joining(", "));
            displayValues.add(value.hasQualifier()
                    ? revisions + " (" + value.getQualifier() + ")" : revisions);
        }
        return String.join("\n", displayValues);
    }

    private static String introductionDisplay(
            final com.macindex.macindex.catalog.proto.CatalogIntroduction value) {
        final String date = value.getYear() + "." + value.getMonth();
        return value.hasQualifier() ? date + " (" + value.getQualifier() + ")" : date;
    }

    private static void assertRanges(final List<CatalogTextRange> source,
                                     final List<TextRange> ranges) {
        assertEquals(source.size(), ranges.size());
        for (int index = 0; index < source.size(); index++) {
            assertEquals(source.get(index).getStartInclusive(), ranges.get(index).startInclusive());
            assertEquals(source.get(index).getEndExclusive(), ranges.get(index).endExclusive());
        }
    }

    private static String nullable(final boolean present, final String value) {
        return present ? value : null;
    }

    private static String stripPrefix(final String value, final String prefix) {
        assertTrue("Missing expected enum prefix in " + value, value.startsWith(prefix));
        return value.substring(prefix.length());
    }

    private static Path requiredPath(final String property) {
        final String value = System.getProperty(property);
        assertNotNull("Missing test property " + property, value);
        return Path.of(value);
    }
}

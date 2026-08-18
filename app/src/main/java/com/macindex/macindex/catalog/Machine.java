package com.macindex.macindex.catalog;

import androidx.annotation.Nullable;

import com.macindex.macindex.catalog.proto.CatalogExternalLink;
import com.macindex.macindex.catalog.proto.CatalogIdentity;
import com.macindex.macindex.catalog.proto.CatalogIntroduction;
import com.macindex.macindex.catalog.proto.CatalogMachine;
import com.macindex.macindex.catalog.proto.CatalogSoundProfile;
import com.macindex.macindex.catalog.proto.CatalogSupportStatus;
import com.macindex.macindex.catalog.proto.CatalogTextRange;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Immutable runtime representation of one catalog machine. */
public final class Machine {

    private final CatalogMachine source;
    private final List<IntroductionDate> introductions;
    private final int introductionSortKey;
    private final List<TextRange> processorModelRanges;
    private final List<TextRange> graphicsModelRanges;
    private final List<ExternalLink> links;

    private final List<SearchValue> searchValues;

    Machine(final CatalogMachine source) {
        this.source = source;
        introductions = convertIntroductions(source.getIntroductionsList());
        introductionSortKey = earliestIntroduction(introductions);

        processorModelRanges = convertRanges(source.getProcessorModelRangesList());
        graphicsModelRanges = convertRanges(source.getGraphicsModelRangesList());
        links = convertLinks(source.getLinksList());

        if (source.getNamesCount() == 0) {
            throw new CatalogFormatException("Missing machine name for " + source.getUid());
        }
        final List<SearchValue> values = new ArrayList<>();
        appendSearchValues(values, source.getNamesList(), SearchHit.Field.NAME);
        appendSearchValues(values, source.getCodenamesList(), SearchHit.Field.CODENAME);
        appendSearchValues(values, source.getModelNumbersList(),
                SearchHit.Field.MODEL_NUMBER);
        appendSearchValues(values, source.getIdentifiersList(),
                SearchHit.Field.MODEL_IDENTIFIER);
        appendSearchValues(values, source.getGestaltIdsList(),
                SearchHit.Field.GESTALT_ID);
        appendSearchValues(values, source.getOrderNumbersList(),
                SearchHit.Field.PART_NUMBER);
        appendSearchValues(values, source.getEmcNumbersList(),
                SearchHit.Field.EMC_NUMBER);
        searchValues = Collections.unmodifiableList(values);
    }

    private static List<IntroductionDate> convertIntroductions(
            final List<CatalogIntroduction> source) {
        final List<IntroductionDate> values = new ArrayList<>();
        for (CatalogIntroduction raw : source) {
            values.add(new IntroductionDate(
                    raw.getYear(), raw.getMonth(),
                    raw.hasQualifier() ? raw.getQualifier() : null));
        }
        return Collections.unmodifiableList(values);
    }

    private static int earliestIntroduction(final List<IntroductionDate> values) {
        int earliest = Integer.MAX_VALUE;
        for (IntroductionDate introduction : values) {
            earliest = Math.min(earliest, introduction.sortKey());
        }
        return earliest;
    }

    private static List<TextRange> convertRanges(final List<CatalogTextRange> source) {
        final List<TextRange> values = new ArrayList<>();
        for (CatalogTextRange raw : source) {
            values.add(new TextRange(raw.getStartInclusive(), raw.getEndExclusive()));
        }
        return Collections.unmodifiableList(values);
    }

    private static List<ExternalLink> convertLinks(final List<CatalogExternalLink> source) {
        final List<ExternalLink> values = new ArrayList<>();
        for (CatalogExternalLink raw : source) {
            values.add(new ExternalLink(raw.getLabel(), raw.getUrl()));
        }
        return Collections.unmodifiableList(values);
    }

    private static SupportStatus convertSupportStatus(final CatalogSupportStatus raw) {
        switch (raw) {
            case CATALOG_SUPPORT_STATUS_SUPPORTED:
                return SupportStatus.SUPPORTED;
            case CATALOG_SUPPORT_STATUS_VINTAGE:
                return SupportStatus.VINTAGE;
            case CATALOG_SUPPORT_STATUS_OBSOLETE:
                return SupportStatus.OBSOLETE;
            case CATALOG_SUPPORT_STATUS_NOT_APPLICABLE:
                return SupportStatus.NOT_APPLICABLE;
            default:
                throw new CatalogFormatException("Missing support status");
        }
    }

    private static SoundProfile convertSoundProfile(final CatalogSoundProfile raw) {
        switch (raw) {
            case CATALOG_SOUND_PROFILE_UNSPECIFIED:
                return SoundProfile.UNSPECIFIED;
            case CATALOG_SOUND_PROFILE_MACINTOSH_128K:
                return SoundProfile.MACINTOSH_128K;
            case CATALOG_SOUND_PROFILE_MACINTOSH_II:
                return SoundProfile.MACINTOSH_II;
            case CATALOG_SOUND_PROFILE_MACINTOSH_LC:
                return SoundProfile.MACINTOSH_LC;
            case CATALOG_SOUND_PROFILE_QUADRA:
                return SoundProfile.QUADRA;
            case CATALOG_SOUND_PROFILE_QUADRA_AV:
                return SoundProfile.QUADRA_AV;
            case CATALOG_SOUND_PROFILE_POWER_MAC_6100:
                return SoundProfile.POWER_MAC_6100;
            case CATALOG_SOUND_PROFILE_POWER_MAC_5000:
                return SoundProfile.POWER_MAC_5000;
            case CATALOG_SOUND_PROFILE_POWER_MAC:
                return SoundProfile.POWER_MAC;
            case CATALOG_SOUND_PROFILE_NEW_WORLD:
                return SoundProfile.NEW_WORLD;
            case CATALOG_SOUND_PROFILE_TWENTIETH_ANNIVERSARY:
                return SoundProfile.TWENTIETH_ANNIVERSARY;
            case CATALOG_SOUND_PROFILE_POWERBOOK:
                return SoundProfile.POWERBOOK;
            case CATALOG_SOUND_PROFILE_T2:
                return SoundProfile.T2;
            case CATALOG_SOUND_PROFILE_NONE:
                return SoundProfile.NONE;
            default:
                throw new CatalogFormatException("Unknown sound profile");
        }
    }

    private static void appendSearchValues(
            final List<SearchValue> destination,
            final List<CatalogIdentity> source,
            final SearchHit.Field field) {
        for (CatalogIdentity entry : source) {
            destination.add(new SearchValue(
                    entry.getValue(),
                    entry.hasQualifier() ? entry.getQualifier() : null,
                    entry.getRevisionsList(),
                    field,
                    field == SearchHit.Field.NAME && destination.isEmpty()));
        }
    }

    static String normalize(final String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim().toLowerCase(Locale.ROOT);
    }

    List<SearchValue> searchValues() {
        return searchValues;
    }

    int introductionSortKey() {
        return introductionSortKey;
    }

    public String uid() {
        return source.getUid();
    }

    public String manufacturerKey() {
        return source.getManufacturerKey();
    }

    public String productTypeKey() {
        return source.getProductTypeKey();
    }

    public String pictureAssetKey() {
        return source.getPictureAssetKey();
    }

    public String name() {
        return searchValues.get(0).displayValue();
    }

    public List<IntroductionDate> introductions() {
        return introductions;
    }

    public String introductionDisplayText() {
        final StringBuilder result = new StringBuilder();
        for (IntroductionDate introduction : introductions) {
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(introduction.displayText());
        }
        return result.toString();
    }

    @Nullable
    public String modelNumbers() {
        return displayText(SearchHit.Field.MODEL_NUMBER);
    }

    @Nullable
    public String identifiers() {
        return displayText(SearchHit.Field.MODEL_IDENTIFIER);
    }

    @Nullable
    public String gestaltIds() {
        return displayText(SearchHit.Field.GESTALT_ID);
    }

    @Nullable
    public String orderNumbers() {
        return displayText(SearchHit.Field.PART_NUMBER);
    }

    @Nullable
    public String codenameDisplayText() {
        return displayText(SearchHit.Field.CODENAME);
    }

    @Nullable
    public String emcNumbers() {
        return displayText(SearchHit.Field.EMC_NUMBER);
    }

    @Nullable
    public String processor() {
        return source.hasProcessor() ? source.getProcessor() : null;
    }

    public List<String> processorFamilyKeys() {
        return source.getProcessorFamilyKeysList();
    }

    public List<String> processorLogoKeys() {
        return source.getProcessorLogoKeysList();
    }

    public List<TextRange> processorModelRanges() {
        return processorModelRanges;
    }

    @Nullable
    public String graphics() {
        return source.hasGraphics() ? source.getGraphics() : null;
    }

    public List<String> graphicsLogoKeys() {
        return source.getGraphicsLogoKeysList();
    }

    public List<TextRange> graphicsModelRanges() {
        return graphicsModelRanges;
    }

    @Nullable
    public String display() {
        return source.hasDisplay() ? source.getDisplay() : null;
    }

    @Nullable
    public String ram() {
        return source.hasRam() ? source.getRam() : null;
    }

    @Nullable
    public String rom() {
        return source.hasRom() ? source.getRom() : null;
    }

    @Nullable
    public String software() {
        return source.hasSoftware() ? source.getSoftware() : null;
    }

    @Nullable
    public String storage() {
        return source.hasStorage() ? source.getStorage() : null;
    }

    @Nullable
    public String features() {
        return source.hasFeatures() ? source.getFeatures() : null;
    }

    @Nullable
    public String expansion() {
        return source.hasExpansion() ? source.getExpansion() : null;
    }

    public String design() {
        return source.getDesign();
    }

    public SupportStatus supportStatus() {
        return convertSupportStatus(source.getSupportStatus());
    }

    public SoundProfile soundProfile() {
        return convertSoundProfile(source.getSoundProfile());
    }

    public List<ExternalLink> links() {
        return links;
    }

    @Nullable
    private String displayText(final SearchHit.Field field) {
        StringBuilder result = null;
        for (SearchValue value : searchValues) {
            if (value.field() != field) {
                continue;
            }
            if (result == null) {
                result = new StringBuilder();
            } else {
                result.append('\n');
            }
            result.append(value.displayValue());
        }
        return result == null ? null : result.toString();
    }
}

package com.macindex.macindex.catalog;

import java.util.Objects;

/** A labelled external specification link. */
public final class ExternalLink {

    private final String label;
    private final String url;

    ExternalLink(final String label, final String url) {
        this.label = label;
        this.url = url;
    }

    public String label() {
        return label;
    }

    public String url() {
        return url;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExternalLink)) {
            return false;
        }
        final ExternalLink that = (ExternalLink) other;
        return label.equals(that.label) && url.equals(that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, url);
    }
}

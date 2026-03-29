package com.media.cache;

/**
 * Media type enum for cache helper
 * Java 7 compatible - no enum methods, no lambdas
 */
public final class MediaType {

    public static final MediaType IMAGE = new MediaType("IMAGE", 0);
    public static final MediaType VIDEO = new MediaType("VIDEO", 1);

    private final String name;
    private final int ordinal;

    private MediaType(String name, int ordinal) {
        this.name = name;
        this.ordinal = ordinal;
    }

    public String getName() {
        return name;
    }

    public int getOrdinal() {
        return ordinal;
    }

    @Override
    public String toString() {
        return name;
    }

    public static MediaType fromOrdinal(int ordinal) {
        switch (ordinal) {
            case 0:
                return IMAGE;
            case 1:
                return VIDEO;
            default:
                return IMAGE;
        }
    }
}

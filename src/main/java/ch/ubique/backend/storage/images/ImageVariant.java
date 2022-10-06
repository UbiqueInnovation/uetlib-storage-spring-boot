package ch.ubique.backend.storage.images;

public enum ImageVariant {
    ORIGINAL,
    THUMBNAIL,
    HIRES;

    public final String key;

    ImageVariant() {
        key = name().toLowerCase();
    }
}

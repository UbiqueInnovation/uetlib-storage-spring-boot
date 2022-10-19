package ch.ubique.backend.storage;

public enum OutputVersion {
    V1("v1"),
    V2("v2"),
    V3("v3");

    private final String version;

    OutputVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}

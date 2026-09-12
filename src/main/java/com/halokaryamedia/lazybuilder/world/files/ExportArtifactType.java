package com.halokaryamedia.lazybuilder.world.files;

/** File type emitted by the server-side World Manager export store. */
public enum ExportArtifactType {
    JAVA_ZIP(".zip"),
    BEDROCK_WORLD(".mcworld");

    private final String extension;

    ExportArtifactType(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }
}

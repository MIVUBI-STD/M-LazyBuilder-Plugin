package com.halokaryamedia.lazybuilder.client;

/** Transient lifecycle for the map export workspace presentation. */
final class MapExportWorkspaceLifecycle {
    private boolean active;
    private boolean requestedSettings;
    private boolean requestedFormats;
    private boolean controlsReady;
    private int scroll;

    boolean active() {
        return active;
    }

    boolean requestedSettings() {
        return requestedSettings;
    }

    boolean requestedFormats() {
        return requestedFormats;
    }

    boolean controlsReady() {
        return controlsReady;
    }

    int scroll() {
        return scroll;
    }

    void enter() {
        active = true;
        requestedSettings = false;
        requestedFormats = false;
        controlsReady = false;
        scroll = 0;
    }

    void exit() {
        active = false;
        requestedSettings = false;
        requestedFormats = false;
        controlsReady = false;
        scroll = 0;
    }

    void markSettingsRequested() {
        requestedSettings = true;
    }

    void markFormatsRequested() {
        requestedFormats = true;
    }

    void markControlsReady() {
        controlsReady = true;
    }

    void scrollBy(int delta, int maxScroll) {
        scroll = Math.max(0, Math.min(Math.max(0, maxScroll), scroll + delta));
    }

    void clampScroll(int maxScroll) {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, maxScroll)));
    }
}

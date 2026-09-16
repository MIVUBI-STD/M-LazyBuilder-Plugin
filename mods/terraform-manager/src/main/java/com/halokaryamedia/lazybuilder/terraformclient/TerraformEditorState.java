package com.halokaryamedia.lazybuilder.terraformclient;

import com.halokaryamedia.lazybuilder.terraform.TerrainTool;
import com.halokaryamedia.lazybuilder.terraform.TerrainVariation;

/** Client-only editor state. Geometry remains owned by terraform-core. */
public final class TerraformEditorState {
    private boolean editorOpen;
    private TerrainTool tool = TerrainTool.CLIFF;
    private TerrainVariation variation = TerrainVariation.NATURAL;
    private double size = 18.0;
    private double height = 28.0;
    private boolean faceFlipped;

    public boolean editorOpen() { return editorOpen; }
    public void setEditorOpen(boolean value) { editorOpen = value; }
    public void toggleEditor() { editorOpen = !editorOpen; }
    public TerrainTool tool() { return tool; }
    public void setTool(TerrainTool value) { tool = value; }
    public TerrainVariation variation() { return variation; }
    public void setVariation(TerrainVariation value) { variation = value; }
    public double size() { return size; }
    public double height() { return height; }
    public boolean faceFlipped() { return faceFlipped; }
    public void flipFace() { faceFlipped = !faceFlipped; }

    public void adjustSize(double delta) { size = clamp(size + delta, 4.0, 96.0); }
    public void adjustHeight(double delta) { height = clamp(height + delta, 4.0, 192.0); }
    public void cancelStroke() { faceFlipped = false; }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

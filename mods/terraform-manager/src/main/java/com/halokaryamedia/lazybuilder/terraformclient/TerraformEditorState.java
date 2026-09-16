package com.halokaryamedia.lazybuilder.terraformclient;

import com.halokaryamedia.lazybuilder.terraform.TerrainTool;
import com.halokaryamedia.lazybuilder.terraform.TerrainVariation;

/** Client-only editor state. Geometry remains owned by terraform-core. */
public final class TerraformEditorState {
    private boolean editorOpen;
    private TerrainTool tool=TerrainTool.CLIFF;
    private TerrainVariation variation=TerrainVariation.NATURAL;
    private double size=18.0;
    private double height=28.0;
    private boolean faceFlipped;
    private long revision;

    public boolean editorOpen(){return editorOpen;}
    public void setEditorOpen(boolean value){if(editorOpen!=value){editorOpen=value;revision++;}}
    public void toggleEditor(){setEditorOpen(!editorOpen);}
    public TerrainTool tool(){return tool;}
    public void setTool(TerrainTool value){if(tool!=value){tool=value;revision++;}}
    public TerrainVariation variation(){return variation;}
    public void setVariation(TerrainVariation value){if(variation!=value){variation=value;revision++;}}
    public double size(){return size;}
    public double height(){return height;}
    public boolean faceFlipped(){return faceFlipped;}
    public long revision(){return revision;}
    public void flipFace(){faceFlipped=!faceFlipped;revision++;}
    public void adjustSize(double delta){double next=clamp(size+delta,4.0,96.0);if(next!=size){size=next;revision++;}}
    public void adjustHeight(double delta){double next=clamp(height+delta,4.0,192.0);if(next!=height){height=next;revision++;}}
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
}

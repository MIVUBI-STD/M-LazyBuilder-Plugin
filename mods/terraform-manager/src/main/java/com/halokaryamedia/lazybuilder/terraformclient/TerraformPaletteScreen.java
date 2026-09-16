package com.halokaryamedia.lazybuilder.terraformclient;

import com.halokaryamedia.lazybuilder.terraform.TerrainTool;
import com.halokaryamedia.lazybuilder.terraform.TerrainVariation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Compact tool/options palette over the live world, styled to match LazyBuilder World/Map Manager. */
final class TerraformPaletteScreen extends Screen {
    private static final int X=16,Y=22,W=200,ROW=22;
    TerraformPaletteScreen(){super(Text.literal("LazyBuilder Terraform"));}
    @Override public boolean shouldPause(){return false;}
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        TerraformEditorState s=TerraformManagerClient.state();panel(c,X,Y,W,220);c.drawText(textRenderer,"TERRAFORM",X+12,Y+10,TerraformUi.TEXT_PRIMARY,false);c.drawText(textRenderer,"Shape",X+12,Y+30,TerraformUi.TEXT_MUTED,false);
        int yy=Y+43;for(TerrainTool tool:TerrainTool.values()){row(c,X+10,yy,W-20,ROW,tool.name(),s.tool()==tool);yy+=ROW+4;}
        c.drawText(textRenderer,"Options",X+12,yy+5,TerraformUi.TEXT_MUTED,false);yy+=19;
        option(c,"Size",String.valueOf((int)s.size()),yy);yy+=24;option(c,"Height",String.valueOf((int)s.height()),yy);yy+=24;
        c.drawText(textRenderer,"Variation  "+pretty(s.variation().name()),X+12,yy+5,TerraformUi.TEXT_SECONDARY,false);
        c.drawText(textRenderer,"Live controls",X+12,Y+184,TerraformUi.TEXT_MUTED,false);
        c.drawText(textRenderer,"[ / ] Tool   V Variation   P Close",X+12,Y+197,TerraformUi.TEXT_SECONDARY,false);
        c.drawText(textRenderer,"Wheel Size   Shift+Wheel Height",X+12,Y+209,TerraformUi.TEXT_MUTED,false);
        super.render(c,mouseX,mouseY,delta);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){if(button!=0)return super.mouseClicked(mx,my,button);TerraformEditorState s=TerraformManagerClient.state();int yy=Y+43;for(TerrainTool tool:TerrainTool.values()){if(inside(mx,my,X+10,yy,W-20,ROW)){TerraformInteractionController.cancelStroke();s.setTool(tool);return true;}yy+=ROW+4;}
        yy+=19;if(inside(mx,my,X+132,yy,22,18)){s.adjustSize(-1);return true;}if(inside(mx,my,X+158,yy,22,18)){s.adjustSize(1);return true;}yy+=24;if(inside(mx,my,X+132,yy,22,18)){s.adjustHeight(-2);return true;}if(inside(mx,my,X+158,yy,22,18)){s.adjustHeight(2);return true;}yy+=24;if(inside(mx,my,X+10,yy,W-20,20)){TerraformInteractionController.cancelStroke();TerrainVariation[] values=TerrainVariation.values();s.setVariation(values[(s.variation().ordinal()+1)%values.length]);return true;}return super.mouseClicked(mx,my,button);}
    @Override public void close(){if(client!=null)client.setScreen(null);}
    private void option(DrawContext c,String label,String value,int y){c.drawText(textRenderer,label,X+12,y+5,TerraformUi.TEXT_SECONDARY,false);c.drawText(textRenderer,value,X+96,y+5,TerraformUi.TEXT_PRIMARY,false);small(c,X+132,y,"-");small(c,X+158,y,"+");}
    private void row(DrawContext c,int x,int y,int w,int h,String text,boolean selected){c.fill(x,y,x+w,y+h,selected?TerraformUi.ACCENT_FILL:TerraformUi.SURFACE_2);c.fill(x,y,x+2,y+h,selected?TerraformUi.ACCENT:TerraformUi.BORDER);c.drawText(textRenderer,pretty(text),x+10,y+7,selected?TerraformUi.TEXT_PRIMARY:TerraformUi.TEXT_SECONDARY,false);}
    private void small(DrawContext c,int x,int y,String text){c.fill(x,y,x+22,y+18,TerraformUi.SURFACE_3);c.drawCenteredTextWithShadow(textRenderer,text,x+11,y+5,TerraformUi.TEXT_PRIMARY);}
    private void panel(DrawContext c,int x,int y,int w,int h){c.fill(x,y,x+w,y+h,0xB8000000);c.fill(x+1,y+1,x+w-1,y+h-1,TerraformUi.BORDER);c.fill(x+2,y+2,x+w-2,y+h-2,TerraformUi.SURFACE_1);}
    private static boolean inside(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    private static String pretty(String s){String lower=s.toLowerCase();return Character.toUpperCase(lower.charAt(0))+lower.substring(1);}
}

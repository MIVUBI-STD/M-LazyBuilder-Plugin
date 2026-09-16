package com.halokaryamedia.lazybuilder.terraformclient;

import com.halokaryamedia.lazybuilder.terraform.TerrainTool;
import com.halokaryamedia.lazybuilder.terraform.TerrainVariation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Axiom-like interaction surface using the established LazyBuilder Map Manager
 * visual language. The panel configures the live editor; terrain work happens
 * directly in the world after the panel is closed.
 */
final class TerraformPaletteScreen extends Screen {
    private static final int X=16,Y=20,W=226,H=296;
    private static final int INNER_X=X+12;
    private static final int INNER_W=W-24;

    TerraformPaletteScreen(){super(Text.literal("LazyBuilder Terraform"));}

    @Override public boolean shouldPause(){return false;}

    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        TerraformEditorState s=TerraformManagerClient.state();
        TerraformUi.elevatedPanel(c,X,Y,W,H);

        c.drawText(textRenderer,"TERRAFORM",INNER_X,Y+10,TerraformUi.TEXT_PRIMARY,false);
        c.drawText(textRenderer,"Direct terrain editor",INNER_X,Y+23,TerraformUi.TEXT_MUTED,false);
        TerraformUi.divider(c,INNER_X,Y+39,X+W-12);

        c.drawText(textRenderer,"TOOL",INNER_X,Y+49,TerraformUi.TEXT_MUTED,false);
        drawToolButtons(c,mouseX,mouseY,s,Y+62);

        TerraformUi.divider(c,INNER_X,Y+94,X+W-12);
        c.drawText(textRenderer,"SETTINGS",INNER_X,Y+104,TerraformUi.TEXT_MUTED,false);
        optionRow(c,mouseX,mouseY,"Size",String.valueOf((int)s.size()),Y+119);
        optionRow(c,mouseX,mouseY,"Height",String.valueOf((int)s.height()),Y+145);

        c.drawText(textRenderer,"Variation",INNER_X,Y+177,TerraformUi.TEXT_SECONDARY,false);
        drawVariationButtons(c,mouseX,mouseY,s,Y+190);

        TerraformUi.divider(c,INNER_X,Y+222,X+W-12);
        drawStatus(c,mouseX,mouseY,s,Y+232);

        c.drawText(textRenderer,"LMB Draw   RMB Flip",INNER_X,Y+265,TerraformUi.TEXT_SECONDARY,false);
        c.drawText(textRenderer,"Wheel Size   Shift+Wheel Height",INNER_X,Y+278,TerraformUi.TEXT_MUTED,false);
        c.drawText(textRenderer,"Right Shift closes panel",INNER_X,Y+289,TerraformUi.TEXT_DISABLED,false);
        super.render(c,mouseX,mouseY,delta);
    }

    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0)return super.mouseClicked(mx,my,button);
        TerraformEditorState s=TerraformManagerClient.state();

        int toolY=Y+62;
        TerrainTool[] tools=TerrainTool.values();
        for(int i=0;i<tools.length;i++){
            int bx=INNER_X+i*68;
            if(inside(mx,my,bx,toolY,64,22)){
                TerraformInteractionController.cancelStroke();
                s.setTool(tools[i]);
                return true;
            }
        }

        if(inside(mx,my,X+164,Y+121,22,18)){s.adjustSize(-1);return true;}
        if(inside(mx,my,X+190,Y+121,22,18)){s.adjustSize(1);return true;}
        if(inside(mx,my,X+164,Y+147,22,18)){s.adjustHeight(-2);return true;}
        if(inside(mx,my,X+190,Y+147,22,18)){s.adjustHeight(2);return true;}

        int variationY=Y+190;
        TerrainVariation[] variations=TerrainVariation.values();
        for(int i=0;i<variations.length;i++){
            int bx=INNER_X+i*68;
            if(inside(mx,my,bx,variationY,64,22)){
                TerraformInteractionController.cancelStroke();
                s.setVariation(variations[i]);
                return true;
            }
        }

        if(s.undoAvailable()&&inside(mx,my,X+154,Y+235,58,18)){
            TerraformInteractionController.undo();
            return true;
        }
        return super.mouseClicked(mx,my,button);
    }

    @Override public void close(){if(client!=null)client.setScreen(null);}

    private void drawToolButtons(DrawContext c,int mouseX,int mouseY,TerraformEditorState s,int y){
        TerrainTool[] tools=TerrainTool.values();
        for(int i=0;i<tools.length;i++){
            int x=INNER_X+i*68;
            segmented(c,mouseX,mouseY,x,y,64,22,pretty(tools[i].name()),s.tool()==tools[i]);
        }
    }

    private void drawVariationButtons(DrawContext c,int mouseX,int mouseY,TerraformEditorState s,int y){
        TerrainVariation[] variations=TerrainVariation.values();
        for(int i=0;i<variations.length;i++){
            int x=INNER_X+i*68;
            segmented(c,mouseX,mouseY,x,y,64,22,pretty(variations[i].name()),s.variation()==variations[i]);
        }
    }

    private void segmented(DrawContext c,int mouseX,int mouseY,int x,int y,int w,int h,String label,boolean selected){
        boolean hot=inside(mouseX,mouseY,x,y,w,h);
        int border=selected?TerraformUi.ACCENT:(hot?TerraformUi.BORDER_BRIGHT:TerraformUi.BORDER);
        int fill=selected?(hot?TerraformUi.ACCENT_HOVER:TerraformUi.ACCENT_FILL):(hot?TerraformUi.SURFACE_3:TerraformUi.SURFACE_1);
        c.fill(x,y,x+w,y+h,border);
        c.fill(x+1,y+1,x+w-1,y+h-1,fill);
        c.drawCenteredTextWithShadow(textRenderer,label,x+w/2,y+7,selected?TerraformUi.TEXT_PRIMARY:TerraformUi.TEXT_SECONDARY);
    }

    private void optionRow(DrawContext c,int mouseX,int mouseY,String label,String value,int y){
        c.fill(INNER_X,y,X+W-12,y+22,TerraformUi.BORDER);
        c.fill(INNER_X+1,y+1,X+W-13,y+21,TerraformUi.SURFACE_1);
        c.drawText(textRenderer,label,INNER_X+8,y+7,TerraformUi.TEXT_SECONDARY,false);
        c.drawText(textRenderer,value,X+134,y+7,TerraformUi.TEXT_PRIMARY,false);
        small(c,mouseX,mouseY,X+164,y+2,"-");
        small(c,mouseX,mouseY,X+190,y+2,"+");
    }

    private void drawStatus(DrawContext c,int mouseX,int mouseY,TerraformEditorState s,int y){
        int accent=s.busy()?TerraformUi.ACCENT:TerraformUi.SUCCESS;
        c.fill(INNER_X,y,X+W-12,y+24,TerraformUi.BORDER);
        c.fill(INNER_X+1,y+1,X+W-13,y+23,TerraformUi.SURFACE_1);
        c.fill(INNER_X+1,y+1,INNER_X+3,y+23,accent);
        c.drawText(textRenderer,s.busy()?"Applying terrain…":"Ready to draw",INNER_X+10,y+8,s.busy()?TerraformUi.ACCENT_BRIGHT:TerraformUi.TEXT_SECONDARY,false);
        action(c,mouseX,mouseY,X+154,y+3,58,18,"Undo",s.undoAvailable());
    }

    private void small(DrawContext c,int mouseX,int mouseY,int x,int y,String label){
        boolean hot=inside(mouseX,mouseY,x,y,22,18);
        c.fill(x,y,x+22,y+18,hot?TerraformUi.BORDER_BRIGHT:TerraformUi.BORDER);
        c.fill(x+1,y+1,x+21,y+17,hot?TerraformUi.SURFACE_3:TerraformUi.SURFACE_2);
        c.drawCenteredTextWithShadow(textRenderer,label,x+11,y+5,TerraformUi.TEXT_PRIMARY);
    }

    private void action(DrawContext c,int mouseX,int mouseY,int x,int y,int w,int h,String label,boolean enabled){
        boolean hot=enabled&&inside(mouseX,mouseY,x,y,w,h);
        int border=enabled?(hot?TerraformUi.ACCENT_BRIGHT:TerraformUi.ACCENT):TerraformUi.BORDER;
        int fill=enabled?(hot?TerraformUi.ACCENT_HOVER:TerraformUi.ACCENT_FILL):TerraformUi.SURFACE_2;
        c.fill(x,y,x+w,y+h,border);
        c.fill(x+1,y+1,x+w-1,y+h-1,fill);
        c.drawCenteredTextWithShadow(textRenderer,label,x+w/2,y+5,enabled?TerraformUi.TEXT_PRIMARY:TerraformUi.TEXT_DISABLED);
    }

    private static boolean inside(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    private static String pretty(String s){String lower=s.toLowerCase();return Character.toUpperCase(lower.charAt(0))+lower.substring(1);}
}

package com.halokaryamedia.lazybuilder.terraformclient;

import com.halokaryamedia.lazybuilder.terraform.TerrainTool;
import com.halokaryamedia.lazybuilder.terraform.TerrainVariation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Axiom-like live editor behavior presented with Map Manager visual language.
 * The panel configures the editor; terrain work remains in the world.
 */
final class TerraformPaletteScreen extends Screen {
    private static final int X=16,Y=24,W=246,H=304;
    private static final int PAD=14;
    private static final int INNER_X=X+PAD;
    private static final int INNER_W=W-PAD*2;

    TerraformPaletteScreen(){super(Text.literal("LazyBuilder Terraform"));}

    @Override protected void init(){
        TerraformEditorState s=TerraformManagerClient.state();
        int three=(INNER_W-12)/3;

        int toolY=Y+57;
        TerrainTool[] tools=TerrainTool.values();
        for(int i=0;i<tools.length;i++){
            TerrainTool tool=tools[i];
            int bx=INNER_X+i*(three+6);
            TerraformButtonWidget button=new TerraformButtonWidget(
                    bx,toolY,three,26,Text.literal(pretty(tool.name())),
                    s.tool()==tool?TerraformButtonWidget.Style.SECONDARY:TerraformButtonWidget.Style.GHOST,
                    ()->{TerraformInteractionController.cancelStroke();s.setTool(tool);clearAndInit();});
            button.active=!s.busy();
            addDrawableChild(button);
        }

        addStepper(Y+115,true,s);
        addStepper(Y+145,false,s);

        int variationY=Y+202;
        TerrainVariation[] variations=TerrainVariation.values();
        for(int i=0;i<variations.length;i++){
            TerrainVariation variation=variations[i];
            int bx=INNER_X+i*(three+6);
            TerraformButtonWidget button=new TerraformButtonWidget(
                    bx,variationY,three,24,Text.literal(variationLabel(variation)),
                    s.variation()==variation?TerraformButtonWidget.Style.SECONDARY:TerraformButtonWidget.Style.GHOST,
                    ()->{TerraformInteractionController.cancelStroke();s.setVariation(variation);clearAndInit();});
            button.active=!s.busy();
            addDrawableChild(button);
        }

        TerraformButtonWidget undo=new TerraformButtonWidget(
                X+W-82,Y+243,62,22,Text.literal("Undo"),TerraformButtonWidget.Style.SECONDARY,
                TerraformInteractionController::undo);
        undo.active=s.undoAvailable();
        addDrawableChild(undo);
    }

    private void addStepper(int y,boolean size,TerraformEditorState state){
        TerraformButtonWidget minus=new TerraformButtonWidget(
                X+W-72,y+2,24,22,Text.literal("-"),TerraformButtonWidget.Style.GHOST,
                ()->{if(size)state.adjustSize(-1);else state.adjustHeight(-2);});
        TerraformButtonWidget plus=new TerraformButtonWidget(
                X+W-42,y+2,24,22,Text.literal("+"),TerraformButtonWidget.Style.GHOST,
                ()->{if(size)state.adjustSize(1);else state.adjustHeight(2);});
        minus.active=!state.busy();plus.active=!state.busy();
        addDrawableChild(minus);addDrawableChild(plus);
    }

    @Override public boolean shouldPause(){return false;}

    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        TerraformEditorState s=TerraformManagerClient.state();
        TerraformUi.elevatedPanel(c,X,Y,W,H);

        c.drawCenteredTextWithShadow(textRenderer,Text.literal("TERRAFORM"),X+W/2,Y+10,TerraformUi.TEXT_MUTED);

        c.drawTextWithShadow(textRenderer,Text.literal("TERRAIN TOOL"),INNER_X,Y+31,TerraformUi.TEXT_PRIMARY);
        c.drawTextWithShadow(textRenderer,Text.literal("Choose a form, then draw in the world"),INNER_X,Y+44,TerraformUi.TEXT_MUTED);

        TerraformUi.divider(c,INNER_X,Y+91,X+W-PAD);
        c.drawTextWithShadow(textRenderer,Text.literal("SETTINGS"),INNER_X,Y+101,TerraformUi.TEXT_MUTED);
        settingRow(c,"Size",String.valueOf((int)s.size()),Y+113);
        settingRow(c,"Height",String.valueOf((int)s.height()),Y+143);

        TerraformUi.divider(c,INNER_X,Y+177,X+W-PAD);
        c.drawTextWithShadow(textRenderer,Text.literal("TERRAIN CHARACTER"),INNER_X,Y+187,TerraformUi.TEXT_MUTED);

        TerraformUi.divider(c,INNER_X,Y+234,X+W-PAD);
        String status=s.busy()?"Applying terrain…":"Ready to draw";
        int statusColor=s.busy()?TerraformUi.ACCENT_BRIGHT:TerraformUi.SUCCESS;
        c.drawTextWithShadow(textRenderer,Text.literal(status),INNER_X,Y+249,statusColor);

        TerraformUi.divider(c,INNER_X,Y+273,X+W-PAD);
        c.drawTextWithShadow(textRenderer,Text.literal("LMB Draw   RMB Flip"),INNER_X,Y+281,TerraformUi.TEXT_SECONDARY);
        c.drawTextWithShadow(textRenderer,Text.literal("Wheel Size   Shift+Wheel Height"),INNER_X,Y+293,TerraformUi.TEXT_MUTED);
        super.render(c,mouseX,mouseY,delta);
    }

    private void settingRow(DrawContext c,String label,String value,int y){
        TerraformUi.panel(c,INNER_X,y,INNER_W,26);
        c.drawTextWithShadow(textRenderer,Text.literal(label),INNER_X+10,y+9,TerraformUi.TEXT_SECONDARY);
        c.drawTextWithShadow(textRenderer,Text.literal(value),X+W-102,y+9,TerraformUi.TEXT_PRIMARY);
    }

    @Override public void close(){if(client!=null)client.setScreen(null);}

    private static String variationLabel(TerrainVariation variation){
        return switch(variation){
            case SOFT -> "Smooth";
            case NATURAL -> "Balanced";
            case DRAMATIC -> "Rugged";
        };
    }

    private static String pretty(String s){String lower=s.toLowerCase();return Character.toUpperCase(lower.charAt(0))+lower.substring(1);}
}

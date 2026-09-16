package com.halokaryamedia.lazybuilder.terraformclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;

import java.util.Objects;

/** Terraform-local button chrome matching Map Manager's LbButtonWidget. */
final class TerraformButtonWidget extends PressableWidget {
    enum Style { PRIMARY, SECONDARY, GHOST, DANGER }

    private final Runnable action;
    private final Style style;

    TerraformButtonWidget(int x,int y,int width,int height,Text message,Style style,Runnable action){
        super(x,y,width,height,message);
        this.style=Objects.requireNonNull(style,"style");
        this.action=Objects.requireNonNull(action,"action");
    }

    @Override public void onPress(){if(active)action.run();}

    @Override protected void renderWidget(DrawContext context,int mouseX,int mouseY,float delta){
        boolean hot=active&&(isHovered()||isFocused());
        int x=getX(),y=getY(),right=x+getWidth(),bottom=y+getHeight();
        int border=switch(style){
            case PRIMARY -> hot?TerraformUi.ACCENT_BRIGHT:TerraformUi.ACCENT;
            case DANGER -> hot?TerraformUi.DANGER_BRIGHT:TerraformUi.DANGER;
            case SECONDARY -> hot?TerraformUi.BORDER_BRIGHT:TerraformUi.BORDER;
            case GHOST -> hot?TerraformUi.BORDER:TerraformUi.SURFACE_2;
        };
        int fill=switch(style){
            case PRIMARY -> hot?TerraformUi.ACCENT_HOVER:TerraformUi.ACCENT_FILL;
            case DANGER -> hot?TerraformUi.DANGER_HOVER:TerraformUi.DANGER_FILL;
            case SECONDARY -> hot?TerraformUi.SURFACE_3:TerraformUi.SURFACE_2;
            case GHOST -> hot?TerraformUi.SURFACE_2:TerraformUi.SURFACE_1;
        };
        int text=active?TerraformUi.TEXT_PRIMARY:TerraformUi.TEXT_DISABLED;
        context.fill(x,y,right,bottom,border);
        context.fill(x+1,y+1,right-1,bottom-1,fill);
        var renderer=MinecraftClient.getInstance().textRenderer;
        Text visible=fittedMessage(renderer);
        int textY=y+(getHeight()-8)/2;
        context.drawCenteredTextWithShadow(renderer,visible,x+getWidth()/2,textY,text);
    }

    private Text fittedMessage(net.minecraft.client.font.TextRenderer renderer){
        int available=Math.max(0,getWidth()-8);
        Text message=getMessage();
        if(renderer.getWidth(message)<=available)return message;
        String ellipsis="…";
        int width=Math.max(0,available-renderer.getWidth(ellipsis));
        return Text.literal(renderer.trimToWidth(message.getString(),width)+ellipsis);
    }

    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder){appendDefaultNarrations(builder);}
}

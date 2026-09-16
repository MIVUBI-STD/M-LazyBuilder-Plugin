package com.halokaryamedia.lazybuilder.terraformclient;

import com.halokaryamedia.lazybuilder.terraform.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Read-only world preview built from the same terraform-core field used by Paper. */
final class TerraformPreviewRenderer {
    private static List<BlockPos> surface=List.of();
    private static long observedRevision=Long.MIN_VALUE;
    private TerraformPreviewRenderer(){}
    static void register(){ClientTickEvents.END_CLIENT_TICK.register(TerraformPreviewRenderer::update);WorldRenderEvents.AFTER_ENTITIES.register(TerraformPreviewRenderer::render);}

    private static void update(MinecraftClient client){
        if(!TerraformManagerClient.state().editorOpen()||client.world==null){surface=List.of();return;}
        long revision=TerraformInteractionController.previewRevision();if(revision==observedRevision&&!TerraformInteractionController.drawing())return;observedRevision=revision;
        List<com.halokaryamedia.lazybuilder.terraform.Vec3d> points=TerraformInteractionController.previewPoints();if(points.isEmpty()){surface=List.of();return;}
        TerraformEditorState s=TerraformManagerClient.state();
        try{
            BoundedShapeField field=TerrainShapeFactory.create(s.tool(),points,TerraformInteractionController.previewFront(),s.size(),s.height(),s.variation(),TerraformInteractionController.previewSeed());
            surface=sample(field);
        }catch(RuntimeException ignored){surface=List.of();}
    }
    private static List<BlockPos> sample(BoundedShapeField field){
        ShapeBounds b=field.bounds();int minX=(int)Math.floor(b.minX()),maxX=(int)Math.ceil(b.maxX()),minY=(int)Math.floor(b.minY()),maxY=(int)Math.ceil(b.maxY()),minZ=(int)Math.floor(b.minZ()),maxZ=(int)Math.ceil(b.maxZ());
        long volume=(long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1);int step=volume>600_000?2:1;List<BlockPos> result=new ArrayList<>();
        for(int x=minX;x<=maxX;x+=step)for(int y=minY;y<=maxY;y+=step)for(int z=minZ;z<=maxZ;z+=step){
            double px=x+0.5*step,py=y+0.5*step,pz=z+0.5*step;if(!field.contains(px,py,pz))continue;
            if(!field.contains(px+step,py,pz)||!field.contains(px-step,py,pz)||!field.contains(px,py+step,pz)||!field.contains(px,py-step,pz)||!field.contains(px,py,pz+step)||!field.contains(px,py,pz-step))result.add(new BlockPos(x,y,z));
        }
        return List.copyOf(result);
    }
    private static void render(WorldRenderContext context){
        if(surface.isEmpty())return;MatrixStack matrices=context.matrixStack();VertexConsumerProvider consumers=context.consumers();if(matrices==null||consumers==null)return;
        Vec3d camera=context.camera().getPos();matrices.push();matrices.translate(-camera.x,-camera.y,-camera.z);VertexConsumer lines=consumers.getBuffer(RenderLayer.getLines());
        for(BlockPos p:surface)WorldRenderer.drawBox(matrices,lines,p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY()+1,p.getZ()+1,0.42f,0.62f,1.0f,0.50f);
        matrices.pop();
    }
}

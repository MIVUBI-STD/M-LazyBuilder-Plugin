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
    private static final long TARGET_SAMPLES=420_000L;
    private static final int MAX_SURFACE_POINTS=85_000;
    private static List<BlockPos> surface=List.of();
    private static long observedRevision=Long.MIN_VALUE;
    private TerraformPreviewRenderer(){}
    static void register(){ClientTickEvents.END_CLIENT_TICK.register(TerraformPreviewRenderer::update);WorldRenderEvents.AFTER_ENTITIES.register(TerraformPreviewRenderer::render);}

    private static void update(MinecraftClient client){
        if(!TerraformManagerClient.state().editorOpen()||client.world==null){surface=List.of();observedRevision=Long.MIN_VALUE;return;}
        long revision=TerraformInteractionController.previewRevision();if(revision==observedRevision&&!TerraformInteractionController.drawing())return;observedRevision=revision;
        List<com.halokaryamedia.lazybuilder.terraform.Vec3d> points=TerraformInteractionController.previewPoints();if(points.isEmpty()){surface=List.of();return;}
        TerraformEditorState s=TerraformManagerClient.state();
        try{
            BoundedShapeField field=TerrainShapeFactory.create(s.tool(),points,TerraformInteractionController.previewFront(),s.size(),s.height(),s.variation(),TerraformInteractionController.previewSeed());
            surface=sample(client,field);
        }catch(RuntimeException ignored){surface=List.of();}
    }
    private static List<BlockPos> sample(MinecraftClient client,BoundedShapeField field){
        ShapeBounds b=field.bounds();int minX=(int)Math.floor(b.minX()),maxX=(int)Math.ceil(b.maxX()),minY=(int)Math.floor(b.minY()),maxY=(int)Math.ceil(b.maxY()),minZ=(int)Math.floor(b.minZ()),maxZ=(int)Math.ceil(b.maxZ());
        long volume=(long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1);int step=previewStep(volume);List<BlockPos> result=new ArrayList<>();
        for(int x=minX;x<=maxX;x+=step)for(int y=minY;y<=maxY;y+=step)for(int z=minZ;z<=maxZ;z+=step){
            double px=x+0.5*step,py=y+0.5*step,pz=z+0.5*step;if(!field.contains(px,py,pz))continue;
            BlockPos pos=new BlockPos(x,y,z);if(!client.world.getBlockState(pos).isAir())continue;
            if(exposedAddition(client,field,pos,px,py,pz,step)){result.add(pos);if(result.size()>=MAX_SURFACE_POINTS)return List.copyOf(result);}
        }
        return List.copyOf(result);
    }
    private static int previewStep(long volume){
        if(volume<=TARGET_SAMPLES)return 1;
        return Math.max(2,(int)Math.ceil(Math.cbrt((double)volume/TARGET_SAMPLES)));
    }
    private static boolean exposedAddition(MinecraftClient client,BoundedShapeField field,BlockPos pos,double px,double py,double pz,int step){
        int[][] dirs={{step,0,0},{-step,0,0},{0,step,0},{0,-step,0},{0,0,step},{0,0,-step}};
        for(int[] d:dirs){
            if(!field.contains(px+d[0],py+d[1],pz+d[2]))return true;
            BlockPos neighbor=pos.add(d[0],d[1],d[2]);if(client.world.getBlockState(neighbor).isAir())return true;
        }
        return false;
    }
    private static void render(WorldRenderContext context){
        if(surface.isEmpty())return;MatrixStack matrices=context.matrixStack();VertexConsumerProvider consumers=context.consumers();if(matrices==null||consumers==null)return;
        Vec3d camera=context.camera().getPos();matrices.push();matrices.translate(-camera.x,-camera.y,-camera.z);VertexConsumer lines=consumers.getBuffer(RenderLayer.getLines());
        for(BlockPos p:surface)WorldRenderer.drawBox(matrices,lines,p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY()+1,p.getZ()+1,0.42f,0.62f,1.0f,0.50f);
        matrices.pop();
    }
}

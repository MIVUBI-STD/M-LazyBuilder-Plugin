package com.halokaryamedia.lazybuilder.terraformclient;

import com.halokaryamedia.lazybuilder.terraform.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Read-only world preview built from the same terraform-core field used by Paper. */
final class TerraformPreviewRenderer {
    private static final long TARGET_SAMPLES=420_000L;
    private static final int MAX_SURFACE_POINTS=85_000;
    private static final int MAX_GUIDE_POINTS=256;
    private static List<BlockPos> surface=List.of();
    private static List<BlockPos> guides=List.of();
    private static long observedRevision=Long.MIN_VALUE;

    private TerraformPreviewRenderer(){}

    static void register(){
        ClientTickEvents.END_CLIENT_TICK.register(TerraformPreviewRenderer::update);
        WorldRenderEvents.AFTER_ENTITIES.register(TerraformPreviewRenderer::render);
    }

    private static void update(MinecraftClient client){
        TerraformEditorState state=TerraformManagerClient.state();
        if(!state.editorOpen()||state.busy()||client.world==null){
            surface=List.of();guides=List.of();observedRevision=Long.MIN_VALUE;return;
        }

        long revision=TerraformInteractionController.previewRevision();
        if(revision==observedRevision&&!TerraformInteractionController.drawing())return;
        observedRevision=revision;

        List<com.halokaryamedia.lazybuilder.terraform.Vec3d> points=TerraformInteractionController.previewPoints();
        if(points.isEmpty()){surface=List.of();guides=List.of();return;}

        guides=guidePoints(points);
        try{
            BoundedShapeField field=TerrainShapeFactory.create(
                    state.tool(),points,TerraformInteractionController.previewFront(),
                    state.size(),state.height(),state.variation(),TerraformInteractionController.previewSeed());
            surface=sample(client,field);
        }catch(RuntimeException ignored){
            surface=List.of();
        }
    }

    private static List<BlockPos> guidePoints(List<com.halokaryamedia.lazybuilder.terraform.Vec3d> points){
        Set<BlockPos> result=new LinkedHashSet<>();
        for(com.halokaryamedia.lazybuilder.terraform.Vec3d point:points){
            result.add(new BlockPos((int)Math.floor(point.x()),(int)Math.floor(point.y()),(int)Math.floor(point.z())));
            if(result.size()>=MAX_GUIDE_POINTS)break;
        }
        return List.copyOf(result);
    }

    private static List<BlockPos> sample(MinecraftClient client,BoundedShapeField field){
        ShapeBounds b=field.bounds();
        int minX=(int)Math.floor(b.minX()),maxX=(int)Math.ceil(b.maxX());
        int minY=(int)Math.floor(b.minY()),maxY=(int)Math.ceil(b.maxY());
        int minZ=(int)Math.floor(b.minZ()),maxZ=(int)Math.ceil(b.maxZ());
        long volume=(long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1);
        int step=previewStep(volume);
        List<BlockPos> result=new ArrayList<>();

        for(int x=minX;x<=maxX;x+=step)for(int y=minY;y<=maxY;y+=step)for(int z=minZ;z<=maxZ;z+=step){
            double px=x+0.5*step,py=y+0.5*step,pz=z+0.5*step;
            if(!field.contains(px,py,pz))continue;
            BlockPos pos=new BlockPos(x,y,z);
            if(!client.world.getBlockState(pos).isAir())continue;
            if(exposedAddition(client,field,pos,px,py,pz,step)){
                result.add(pos);
                if(result.size()>=MAX_SURFACE_POINTS)return List.copyOf(result);
            }
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
            BlockPos neighbor=pos.add(d[0],d[1],d[2]);
            if(client.world.getBlockState(neighbor).isAir())return true;
        }
        return false;
    }

    private static void render(WorldRenderContext context){
        if(surface.isEmpty()&&guides.isEmpty())return;
        MatrixStack matrices=context.matrixStack();
        VertexConsumerProvider consumers=context.consumers();
        if(matrices==null||consumers==null)return;

        boolean drawing=TerraformInteractionController.drawing();
        Vec3d camera=context.camera().getPos();
        matrices.push();
        matrices.translate(-camera.x,-camera.y,-camera.z);
        VertexConsumer lines=consumers.getBuffer(RenderLayer.getLines());

        float alpha=drawing?0.72f:0.46f;
        float r=drawing?0.54f:0.42f;
        float g=drawing?0.70f:0.62f;
        float b=1.00f;
        for(BlockPos p:surface){
            VertexRendering.drawBox(matrices,lines,
                    p.getX()+0.03,p.getY()+0.03,p.getZ()+0.03,
                    p.getX()+0.97,p.getY()+0.97,p.getZ()+0.97,
                    r,g,b,alpha);
        }

        for(int i=0;i<guides.size();i++){
            BlockPos p=guides.get(i);
            boolean endpoint=i==0||i==guides.size()-1;
            float gr=endpoint?0.55f:0.72f;
            float gg=endpoint?0.78f:0.82f;
            float gb=1.00f;
            float ga=endpoint?0.95f:0.72f;
            double inset=endpoint?0.10:0.22;
            VertexRendering.drawBox(matrices,lines,
                    p.getX()+inset,p.getY()+inset,p.getZ()+inset,
                    p.getX()+1.0-inset,p.getY()+1.0-inset,p.getZ()+1.0-inset,
                    gr,gg,gb,ga);
        }
        matrices.pop();
    }
}

package com.halokaryamedia.lazybuilder.terraformclient;

import com.halokaryamedia.lazybuilder.terraform.*;
import com.halokaryamedia.lazybuilder.terraform.wire.TerraformWireProtocol;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One input-to-intent owner. Mouse mixins only forward physical events here. */
public final class TerraformInteractionController {
    private static final List<Vec3d> RAW_PATH=new ArrayList<>();
    private static boolean drawing;
    private static long strokeSeed;
    private static Vec3d lockedFront=new Vec3d(0,0,1);
    private static String lastOperationId;
    private static long lastStateRevision=-1;
    private static long pathRevision;

    private TerraformInteractionController(){}
    public static void register(){ClientTickEvents.END_CLIENT_TICK.register(TerraformInteractionController::tick);TerraformPreviewRenderer.register();}
    public static boolean active(){MinecraftClient client=MinecraftClient.getInstance();return TerraformManagerClient.state().editorOpen()&&client.currentScreen==null&&client.player!=null&&client.world!=null;}
    public static boolean drawing(){return drawing;}

    public static void beginStroke(){
        if(!active())return; MinecraftClient client=MinecraftClient.getInstance();Vec3d hit=hitPoint(client);if(hit==null)return;
        drawing=true;RAW_PATH.clear();RAW_PATH.add(hit);lockedFront=resolveFront(client);strokeSeed=System.nanoTime()^client.player.getUuid().getLeastSignificantBits();pathRevision++;
    }
    public static void endStroke(){
        if(!drawing)return; drawing=false;
        TerraformEditorState state=TerraformManagerClient.state();List<Vec3d> points=committablePoints(state.tool());
        if(points.isEmpty()){cancelStroke();return;}
        String id=UUID.randomUUID().toString();lastOperationId=id;
        List<TerraformWireProtocol.Point> wire=points.stream().map(p->new TerraformWireProtocol.Point(p.x(),p.y(),p.z())).toList();
        TerraformWireProtocol.Apply request=new TerraformWireProtocol.Apply(id,
                TerraformWireProtocol.Tool.valueOf(state.tool().name()),TerraformWireProtocol.Variation.valueOf(state.variation().name()),
                state.size(),state.height(),strokeSeed,lockedFront.x(),lockedFront.z(),wire);
        TerraformClientNetworking.send(request);RAW_PATH.clear();pathRevision++;
    }
    public static void cancelStroke(){drawing=false;RAW_PATH.clear();pathRevision++;}
    public static void flipFace(){if(active()){TerraformManagerClient.state().flipFace();lockedFront=lockedFront.multiply(-1.0);pathRevision++;}}
    public static void adjustWheel(double vertical,boolean shift){if(!active()||vertical==0)return;double delta=Math.copySign(shift?2.0:1.0,vertical);if(shift)TerraformManagerClient.state().adjustHeight(delta);else TerraformManagerClient.state().adjustSize(delta);pathRevision++;}
    public static void undo(){if(lastOperationId!=null)TerraformClientNetworking.send(new TerraformWireProtocol.Undo(UUID.randomUUID().toString()));}

    static List<Vec3d> previewPoints(){
        TerraformEditorState state=TerraformManagerClient.state();
        if(drawing)return committablePoints(state.tool());
        MinecraftClient client=MinecraftClient.getInstance();Vec3d hit=hitPoint(client);if(hit==null)return List.of();
        if(state.tool()==TerrainTool.MOUNTAIN)return List.of(hit);
        Vec3d front=resolveFront(client);Vec3d tangent=new Vec3d(-front.z(),0,front.x());return List.of(hit,hit.add(tangent.multiply(Math.max(6.0,state.size()*1.35))));
    }
    static Vec3d previewFront(){return drawing?lockedFront:resolveFront(MinecraftClient.getInstance());}
    static long previewSeed(){return drawing?strokeSeed:0x4C42544552524149L;}
    static long previewRevision(){return pathRevision*31L+TerraformManagerClient.state().revision();}

    private static void tick(MinecraftClient client){
        if(!TerraformManagerClient.state().editorOpen()){if(drawing)cancelStroke();return;}
        if(drawing){Vec3d hit=hitPoint(client);if(hit!=null){double min=Math.max(1.0,TerraformManagerClient.state().size()*0.08);if(RAW_PATH.getLast().distanceTo(hit)>=min){RAW_PATH.add(hit);pathRevision++;}}}
        long rev=TerraformManagerClient.state().revision();if(rev!=lastStateRevision){lastStateRevision=rev;pathRevision++;}
    }
    private static List<Vec3d> committablePoints(TerrainTool tool){
        if(RAW_PATH.isEmpty())return List.of();if(tool==TerrainTool.MOUNTAIN)return List.of(RAW_PATH.getFirst());
        if(RAW_PATH.size()>=2)return List.copyOf(RAW_PATH);
        Vec3d tangent=new Vec3d(-lockedFront.z(),0,lockedFront.x());return List.of(RAW_PATH.getFirst(),RAW_PATH.getFirst().add(tangent.multiply(Math.max(6.0,TerraformManagerClient.state().size()))));
    }
    private static Vec3d hitPoint(MinecraftClient client){
        HitResult target=client.crosshairTarget;if(!(target instanceof BlockHitResult hit)||target.getType()!=HitResult.Type.BLOCK)return null;
        net.minecraft.util.math.Vec3d p=hit.getPos();return new Vec3d(p.x,p.y,p.z);
    }
    private static Vec3d resolveFront(MinecraftClient client){
        if(client==null||client.player==null)return new Vec3d(0,0,1);
        if(client.crosshairTarget instanceof BlockHitResult hit){Direction d=hit.getSide();if(d.getAxis().isHorizontal()){
            Vec3d n=new Vec3d(d.getOffsetX(),0,d.getOffsetZ());return TerraformManagerClient.state().faceFlipped()?n.multiply(-1):n;
        }}
        double yaw=Math.toRadians(client.player.getYaw());Vec3d view=new Vec3d(-Math.sin(yaw),0,Math.cos(yaw)).horizontalNormalized();return TerraformManagerClient.state().faceFlipped()?view.multiply(-1):view;
    }
}

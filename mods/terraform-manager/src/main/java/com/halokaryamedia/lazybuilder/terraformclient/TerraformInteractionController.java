package com.halokaryamedia.lazybuilder.terraformclient;

import com.halokaryamedia.lazybuilder.terraform.*;
import com.halokaryamedia.lazybuilder.terraform.wire.TerraformWireProtocol;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One input-to-intent owner. Mouse mixins only forward physical events here. */
public final class TerraformInteractionController {
    private static final List<Vec3d> RAW_PATH=new ArrayList<>();
    private static boolean drawing;
    private static boolean continuingStroke;
    private static long strokeSeed;
    private static Vec3d lockedFront=new Vec3d(0,0,1);
    private static String lastOperationId;
    private static String observedWorldKey;
    private static ContinuationSnapshot continuation;
    private static long lastStateRevision=-1;
    private static long pathRevision;

    private TerraformInteractionController(){}
    public static void register(){ClientTickEvents.END_CLIENT_TICK.register(TerraformInteractionController::tick);TerraformPreviewRenderer.register();}
    public static boolean active(){MinecraftClient client=MinecraftClient.getInstance();return TerraformManagerClient.state().editorOpen()&&!TerraformManagerClient.state().busy()&&client.currentScreen==null&&client.player!=null&&client.world!=null;}
    public static boolean drawing(){return drawing;}

    public static void beginStroke(){
        if(!active())return;
        MinecraftClient client=MinecraftClient.getInstance();
        Vec3d hit=hitPoint(client);
        if(hit==null)return;

        TerraformEditorState state=TerraformManagerClient.state();
        drawing=true;
        continuingStroke=false;
        RAW_PATH.clear();

        if(canContinue(state,hit)){
            RAW_PATH.add(continuation.previous());
            RAW_PATH.add(continuation.end());
            if(continuation.end().distanceTo(hit)>0.35)RAW_PATH.add(hit);
            lockedFront=continuation.front();
            strokeSeed=continuation.seed();
            continuingStroke=true;
        }else{
            RAW_PATH.add(hit);
            lockedFront=resolveFront(client);
            strokeSeed=System.nanoTime()^client.player.getUuid().getLeastSignificantBits();
        }
        pathRevision++;
    }

    public static void endStroke(){
        if(!drawing)return;
        drawing=false;
        TerraformEditorState state=TerraformManagerClient.state();
        List<Vec3d> points=committablePoints(state.tool());
        if(points.isEmpty()){cancelStroke();return;}

        String id=UUID.randomUUID().toString();
        lastOperationId=id;
        List<TerraformWireProtocol.Point> wire=points.stream().map(p->new TerraformWireProtocol.Point(p.x(),p.y(),p.z())).toList();
        TerraformWireProtocol.Apply request=new TerraformWireProtocol.Apply(id,
                TerraformWireProtocol.Tool.valueOf(state.tool().name()),TerraformWireProtocol.Variation.valueOf(state.variation().name()),
                state.size(),state.height(),strokeSeed,lockedFront.x(),lockedFront.z(),wire);
        TerraformClientNetworking.send(request);

        rememberContinuation(state,points);
        RAW_PATH.clear();
        continuingStroke=false;
        pathRevision++;
    }

    public static void cancelStroke(){drawing=false;continuingStroke=false;RAW_PATH.clear();pathRevision++;}
    public static void resetRuntime(){drawing=false;continuingStroke=false;RAW_PATH.clear();lastOperationId=null;observedWorldKey=null;continuation=null;lastStateRevision=-1;TerraformManagerClient.state().clearOperationState();pathRevision++;}
    public static void flipFace(){if(active()){TerraformManagerClient.state().flipFace();lockedFront=lockedFront.multiply(-1.0);continuation=null;pathRevision++;showToolStatus("Face flipped");}}
    public static void adjustWheel(double vertical,boolean shift){if(!active()||vertical==0)return;double delta=Math.copySign(shift?2.0:1.0,vertical);if(shift)TerraformManagerClient.state().adjustHeight(delta);else TerraformManagerClient.state().adjustSize(delta);pathRevision++;showToolStatus(null);}
    public static void undo(){if(lastOperationId!=null&&TerraformManagerClient.state().undoAvailable()){continuation=null;TerraformClientNetworking.send(new TerraformWireProtocol.Undo(UUID.randomUUID().toString()));}}

    static List<Vec3d> previewPoints(){
        TerraformEditorState state=TerraformManagerClient.state();
        if(state.busy())return List.of();
        if(drawing)return committablePoints(state.tool());
        MinecraftClient client=MinecraftClient.getInstance();
        Vec3d hit=hitPoint(client);
        if(hit==null)return List.of();
        if(state.tool()==TerrainTool.MOUNTAIN)return List.of(hit);

        Vec3d front=resolveFront(client);
        Vec3d tangent=new Vec3d(-front.z(),0,front.x());
        Vec3d previewEnd=hit.add(tangent.multiply(Math.max(6.0,state.size()*1.35)));
        if(canContinue(state,hit))return List.of(continuation.previous(),continuation.end(),hit,previewEnd);
        return List.of(hit,previewEnd);
    }

    static Vec3d previewFront(){
        if(drawing)return lockedFront;
        TerraformEditorState state=TerraformManagerClient.state();
        Vec3d hit=hitPoint(MinecraftClient.getInstance());
        return hit!=null&&canContinue(state,hit)?continuation.front():resolveFront(MinecraftClient.getInstance());
    }
    static long previewSeed(){
        if(drawing)return strokeSeed;
        TerraformEditorState state=TerraformManagerClient.state();
        Vec3d hit=hitPoint(MinecraftClient.getInstance());
        return hit!=null&&canContinue(state,hit)?continuation.seed():0x4C42544552524149L;
    }
    static long previewRevision(){
        long revision=pathRevision*31L+TerraformManagerClient.state().revision();
        if(!drawing){
            MinecraftClient client=MinecraftClient.getInstance();
            if(client.crosshairTarget instanceof BlockHitResult hit&&client.crosshairTarget.getType()==HitResult.Type.BLOCK){
                revision=revision*31L+hit.getBlockPos().asLong();
                revision=revision*31L+hit.getSide().getId();
            }
            if(continuation!=null)revision=revision*31L+continuation.revisionToken();
        }
        return revision;
    }

    private static void tick(MinecraftClient client){
        String worldKey=currentWorldKey(client);
        if(!java.util.Objects.equals(worldKey,observedWorldKey)){
            observedWorldKey=worldKey;
            if(drawing)cancelStroke();
            lastOperationId=null;
            continuation=null;
            TerraformManagerClient.state().clearOperationState();
            pathRevision++;
        }
        if(!TerraformManagerClient.state().editorOpen()){if(drawing)cancelStroke();return;}
        if(TerraformManagerClient.state().busy()){if(drawing)cancelStroke();return;}
        if(drawing){
            Vec3d hit=hitPoint(client);
            if(hit!=null){
                double min=Math.max(1.0,TerraformManagerClient.state().size()*0.08);
                if(RAW_PATH.getLast().distanceTo(hit)>=min){RAW_PATH.add(hit);pathRevision++;}
            }
        }
        long rev=TerraformManagerClient.state().revision();if(rev!=lastStateRevision){lastStateRevision=rev;pathRevision++;}
    }

    private static boolean canContinue(TerraformEditorState state,Vec3d hit){
        if(state.busy()||continuation==null||state.tool()==TerrainTool.MOUNTAIN)return false;
        if(continuation.tool()!=state.tool()||continuation.variation()!=state.variation())return false;
        if(relativeDifference(continuation.size(),state.size())>0.30||relativeDifference(continuation.height(),state.height())>0.30)return false;
        double threshold=Math.max(3.0,Math.min(12.0,state.size()*0.60));
        return continuation.end().distanceTo(hit)<=threshold;
    }

    private static void rememberContinuation(TerraformEditorState state,List<Vec3d> points){
        if(state.tool()==TerrainTool.MOUNTAIN||points.size()<2){continuation=null;return;}
        Vec3d end=points.getLast();
        Vec3d previous=points.get(points.size()-2);
        if(previous.distanceTo(end)<0.25){continuation=null;return;}
        continuation=new ContinuationSnapshot(
                state.tool(),state.variation(),state.size(),state.height(),strokeSeed,lockedFront,
                previous,end,System.nanoTime());
    }

    private static double relativeDifference(double a,double b){return Math.abs(a-b)/Math.max(1.0,Math.max(Math.abs(a),Math.abs(b)));}

    private static List<Vec3d> committablePoints(TerrainTool tool){
        if(RAW_PATH.isEmpty())return List.of();
        if(tool==TerrainTool.MOUNTAIN)return List.of(RAW_PATH.getFirst());
        if(RAW_PATH.size()>=2)return List.copyOf(RAW_PATH);
        Vec3d tangent=new Vec3d(-lockedFront.z(),0,lockedFront.x());
        return List.of(RAW_PATH.getFirst(),RAW_PATH.getFirst().add(tangent.multiply(Math.max(6.0,TerraformManagerClient.state().size()))));
    }
    private static Vec3d hitPoint(MinecraftClient client){
        HitResult target=client.crosshairTarget;if(!(target instanceof BlockHitResult hit)||target.getType()!=HitResult.Type.BLOCK)return null;
        net.minecraft.util.math.Vec3d p=hit.getPos();return new Vec3d(p.x,p.y,p.z);
    }
    private static String currentWorldKey(MinecraftClient client){return client.world==null?null:client.world.getRegistryKey().getValue().toString();}
    private static Vec3d resolveFront(MinecraftClient client){return TerrainContextResolver.resolveFront(client, TerraformManagerClient.state().faceFlipped());}
    private static void showToolStatus(String prefix){
        MinecraftClient client=MinecraftClient.getInstance();if(client.player==null)return;TerraformEditorState s=TerraformManagerClient.state();
        String status=(prefix==null?"":prefix+"  ·  ")+pretty(s.tool().name())+"  Size "+(int)s.size()+"  Height "+(int)s.height()+"  "+pretty(s.variation().name());
        client.player.sendMessage(Text.literal(status),true);
    }
    private static String pretty(String value){String v=value.toLowerCase();return Character.toUpperCase(v.charAt(0))+v.substring(1);}

    private record ContinuationSnapshot(
            TerrainTool tool,
            TerrainVariation variation,
            double size,
            double height,
            long seed,
            Vec3d front,
            Vec3d previous,
            Vec3d end,
            long revisionToken) {}
}

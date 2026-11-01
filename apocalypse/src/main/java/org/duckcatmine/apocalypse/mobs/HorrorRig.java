package org.duckcatmine.apocalypse.mobs;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared authored pivots: the pack rebases each mesh to exactly the same joint. Forward is +Z. */
public final class HorrorRig {
    public static final List<String> PARTS=List.of("body","head","arm_left","arm_right","left","right");
    public record Joint(float x,float y,float z) {}
    public record Layout(float height,Map<String,Joint> pivots) {}
    public record Pose(float pitch,float roll) {}
    private static final Map<String,Layout> LAYOUTS=load();
    private HorrorRig() {}
    private static Map<String,Layout> load(){
        try(var reader=new InputStreamReader(Objects.requireNonNull(HorrorRig.class.getResourceAsStream("/horror-rigs.json")),StandardCharsets.UTF_8)){
            var root=JsonParser.parseReader(reader).getAsJsonObject();
            if(root.get("version").getAsInt()!=2)throw new IllegalArgumentException("Version de rig inconnue");
            Map<String,Layout> layouts=new LinkedHashMap<>();
            for(var entry:root.getAsJsonObject("models").entrySet()){
                var model=entry.getValue().getAsJsonObject();Map<String,Joint> joints=new LinkedHashMap<>();
                for(String part:PARTS){var p=model.getAsJsonObject("pivots").getAsJsonArray(part);joints.put(part,new Joint(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat()));}
                layouts.put(entry.getKey(),new Layout(model.get("height").getAsFloat()/16,Map.copyOf(joints)));
            }
            return Collections.unmodifiableMap(layouts);
        }catch(Exception e){throw new IllegalStateException("Articulations du bestiaire invalides",e);}
    }
    public static Set<String> kinds(){return LAYOUTS.keySet();}
    public static Layout layout(String kind){return Objects.requireNonNull(LAYOUTS.get(kind),kind);}
    public static float motionYaw(double dx,double dz,float stationary){
        return dx*dx+dz*dz<.0001?stationary:(float)Math.toDegrees(Math.atan2(-dx,dz));
    }
    public static Vector3f offset(String kind,String part,float scale,float yaw){
        Joint p=layout(kind).pivots().get(part);
        return new Vector3f((p.x()-8)/16,p.y()/16,(p.z()-8)/16).mul(scale)
                .rotateY((float)Math.toRadians(-yaw));
    }
    public static Quaternionf rotation(float yaw,Pose pose){
        return new Quaternionf().rotateY((float)Math.toRadians(-yaw)).rotateX(pose.pitch()).rotateZ(pose.roll());
    }
    /** Vanilla 26.3 ItemDisplayRenderer.submitInner rotates the mesh by PI around Y after our transform. */
    public static Quaternionf itemRendererCorrection(){
        return new Quaternionf(0,1,0,0);
    }
    public static Pose pose(String kind,String part,double phase,boolean moving,boolean running,boolean charging,boolean striking){
        float wave=moving?(float)Math.sin(phase):0;
        float leg=wave*(running?.95f:.58f),arm=wave*(running?.8f:.42f);
        boolean low="crawler".equals(kind);
        float pitch=switch(part){
            case "left" -> leg;
            case "right" -> -leg;
            case "arm_left" -> -arm;
            case "arm_right" -> arm;
            case "body" -> moving?(running?.1f:.04f):0;
            default -> 0;
        };
        if(low&&part.startsWith("arm_"))pitch*=.5f;
        if((charging||striking)&&part.startsWith("arm_"))pitch=-1.15f+(striking?.35f:0);
        if(charging&&"body".equals(part))pitch="spitter".equals(kind)?-.2f:.2f;
        if(charging&&"head".equals(part))pitch="spitter".equals(kind)?-.25f:.15f;
        float roll=low&&moving?(part.equals("arm_left")?-wave*.2f:part.equals("arm_right")?wave*.2f:0):0;
        return new Pose(pitch,roll);
    }
}

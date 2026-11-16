package xixi.interaction;

import com.elfmcys.yesstevemodel.Oo0ooO0oOooo000oOo0Ooooo;
import com.elfmcys.yesstevemodel.oo0oo0O0O0oo00OO00000Oo0;
import com.elfmcys.yesstevemodel.oooOoOoo000OoOo0OOoO0oO0;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;
import net.neoforged.fml.loading.FMLPaths;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class FaceAccessories {
    private static final Map<oooOoOoo000OoOo0OOoO0oO0,Optional<Alignment>> ALIGNMENTS=new WeakHashMap<>();
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("xixi-interaction");
    private static float[][] blush;
    private static boolean checked;

    private record Bone(int offset,Vector3f pivot) {}
    private record Alignment(List<Bone> face,Vector3f center,float size,float cheekSlope) {}

    private FaceAccessories(){}

    static boolean blushAvailable(){return blushMesh()!=null&&YsmExpressions.atlasAvailable();}

    private static float[][] blushMesh(){
        if(checked)return blush;
        checked=true;
        var path=FMLPaths.CONFIGDIR.get().resolve("xixi-face-accessories.json");
        if(Files.isRegularFile(path))try(var file=Files.newBufferedReader(path)){
            var loaded=new Gson().fromJson(file,new TypeToken<Map<String,float[][]>>(){});
            var quads=loaded==null?null:loaded.get("blush");
            if(quads==null||quads.length==0||quads.length>32)return null;
            for(float[] quad:quads){
                if(quad==null||quad.length!=16)return null;
                for(float value:quad)if(!Float.isFinite(value))return null;
            }
            blush=quads;
        }catch(IOException|RuntimeException ex){
            LOG.warn("Could not load the local cheek art",ex);
        }
        return blush;
    }

    static void renderBlush(oo0oo0O0O0oo00OO00000Oo0 frame,ByteBuffer bones,
                            YsmExpressions.Settings fit,PoseStack stack,SubmitNodeCollector queue){
        if(!blushAvailable())return;
        Alignment alignment=ALIGNMENTS.computeIfAbsent(frame.o0Oo00O0Ooo00oOo0O000Ooo,
            geometry->Optional.ofNullable(align(geometry))).orElse(null);
        if(alignment==null)return;
        float size=alignment.size()/16;
        Vector3f center=alignment.center();
        float depth=(float)fit.blushDepth;
        stack.pushPose();
        try{
            for(Bone bone:alignment.face()){
                int index=bone.offset();
                Vector3f pivot=bone.pivot();
                stack.translate(-at(bones,index,3)/16+pivot.x,at(bones,index,4)/16+pivot.y,at(bones,index,5)/16+pivot.z);
                stack.mulPose(new Quaternionf().rotationZYX(at(bones,index,2),at(bones,index,1),at(bones,index,0)));
                stack.scale(at(bones,index,6),at(bones,index,7),at(bones,index,8));
                stack.translate(-pivot.x,-pivot.y,-pivot.z);
            }
            stack.translate(center.x,center.y+fit.blushHeight,center.z-depth);
            queue.submitCustomGeometry(stack,RenderTypes.entityTranslucentEmissive(YsmExpressions.ATLAS,false),(pose,vertices)->{
                for(float[] q:blush){
                    for(int i=12;i>=0;i-=4){
                        float x=q[i]*size;
                        float z=alignment.cheekSlope()*x;
                        YsmExpressions.vertex(vertices,pose,x,q[i+1]*size,z,q[i+2],q[i+3],-1,LightCoordsUtil.FULL_BRIGHT);
                    }
                }
            });
        }finally{
            stack.popPose();
        }
    }

    private static float at(ByteBuffer buffer,int offset,int field){return buffer.getFloat(offset+field*Float.BYTES);}

    private static Alignment align(oooOoOoo000OoOo0OOoO0oO0 geometry){
        var definitions=geometry.OO0OoO00ooOOo0o00O000OoO();
        var named=new HashMap<String,Oo0ooO0oOooo000oOo0Ooooo>();
        var byId=new HashMap<Integer,Bone>();
        for(int i=0;i<definitions.size();i++){
            var definition=definitions.get(i);
            if(definition.OO0OoO00ooOOo0o00O000OoO()==null)continue;
            named.putIfAbsent(YsmExpressions.normalize(definition.OO0OoO00ooOOo0o00O000OoO()),definition);
            byId.put(definition.O0oo00O0OoooOOOO00ooO000(),new Bone(i*YsmExpressions.BONE_BYTES,pivot(definition)));
        }
        var head=named.get("head");
        var face=named.get("face");
        var mouth=named.get("mouth");
        var leftEye=named.get("lefteyeball");
        var rightEye=named.get("righteyeball");
        if(head==null||mouth==null||leftEye==null||rightEye==null){
            LOG.warn("No DS cheek attachment: head={}, mouth={}, left eye={}, right eye={}",
                head!=null,mouth!=null,leftEye!=null,rightEye!=null);
            return null;
        }
        int headId=head.O0oo00O0OoooOOOO00ooO000();
        int faceId=face==null?-1:face.O0oo00O0OoooOOOO00ooO000();
        List<Bone> chain=List.of();
        boolean includesFace=false;
        var paths=new ArrayList<IntList>();
        paths.addAll(geometry.O0oo00O0OoooOOOO00ooO000);
        paths.addAll(geometry.oo000Oo000o0Ooooooo00oOo);
        paths.addAll(geometry.OoO0Ooo0OO0Ooooo0O0OoOOO);
        paths.addAll(List.of(geometry.Oo0o00OoooOoo00o00O00O0o,geometry.o0oo0000oooOo0ooOOOo0OOo,
            geometry.O0OoOoO0oo0O00O0ooo0o0OO,geometry.OOOOoooo000OO0Ooo0o00ooO,geometry.OOoO0ooOO0OOoo0OOOoOo000,
            geometry.o0Oo00O0Ooo00oOo0O000Ooo,geometry.O000O0000O00000oo0o0OooO,geometry.Oo00OO0oOO0O0o0o00OOoOoo));
        for(var path:paths){
            int headIndex=path.indexOf(headId);
            int faceIndex=path.indexOf(faceId);
            boolean facePath=headIndex>=0&&faceIndex>headIndex;
            int end=facePath?faceIndex:headIndex;
            if(end<0||includesFace&&!facePath||includesFace==facePath&&end<chain.size())continue;
            var prefix=new ArrayList<Bone>();
            for(int i=0;i<=end;i++){
                var bone=byId.get(path.getInt(i));
                if(bone==null)break;
                prefix.add(bone);
            }
            if(prefix.size()==end+1){
                chain=List.copyOf(prefix);
                includesFace=facePath;
            }
        }
        if(chain.isEmpty()){
            // YSM exposes only the chains it uses for attachment points. DS can omit its head path here.
            var fallback=new ArrayList<Bone>();
            for(String name:List.of("root","allbody","upbody","neck","head")){
                var definition=named.get(name);
                if(definition!=null)fallback.add(byId.get(definition.O0oo00O0OoooOOOO00ooO000()));
            }
            chain=List.copyOf(fallback);
        }
        var lips=pivot(mouth);
        var left=pivot(leftEye);
        var right=pivot(rightEye);
        var eyes=new Vector3f(left).lerp(right,.5f);
        var skin=face==null?null:pivot(face);
        float size=Math.clamp(left.distance(right)*16/4.03f,.55f,1.65f);
        // The donor geometry is centered on its own mouth. On DS its cheek height sits between the lips and the eyes.
        float y=lips.y+(eyes.y-lips.y)*.21f;
        // Eye and lip pivots describe the front of the native face. Face can also be used as a depth reference
        // on rigs whose face pivot is on the skin rather than the center of the head.
        float z=Math.min(lips.z,eyes.z);
        if(skin!=null&&Math.abs(skin.z-z)<.025f)z=Math.min(z,skin.z);
        float slope=left.x==right.x?0:Math.clamp((left.z-right.z)/(left.x-right.x),-.15f,.15f);
        var center=new Vector3f(eyes.x,y,z-.007f);
        LOG.info("DS cheek attachment: {} transforms, face chain={}, Face {}, cheek decal {}",
            chain.size(),includesFace,skin,center);
        return new Alignment(chain,center,size,slope);
    }

    private static Vector3f pivot(Oo0ooO0oOooo000oOo0Ooooo bone){
        return new Vector3f(bone.o0oo0000oooOo0ooOOOo0OOo()/16,bone.O0OoOoO0oo0O00O0ooo0o0OO()/16,
            bone.OOOOoooo000OO0Ooo0o00ooO()/16);
    }
}

package xixi.interaction;

import com.elfmcys.yesstevemodel.oo0oo0O0O0oo00OO00000Oo0;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-side presentation settings for a single DS companion. */
public final class YsmExpressions {
    private static final Logger LOG=LoggerFactory.getLogger("xixi-interaction");
    static final Identifier ATLAS=Identifier.fromNamespaceAndPath("xixi_interaction","dfy_sticker_atlas");
    private static final int ATLAS_SIZE=512;
    static final int BONE_BYTES=12*Float.BYTES;
    private static boolean atlasChecked,atlasLoaded;
    private static final Map<UUID,Settings> SETTINGS=new HashMap<>();
    private static final Set<UUID> WAITING_FOR_FACE=new HashSet<>();
    private static final Map<UUID,Integer> AUTO_STICKERS_DRAWN=new HashMap<>();
    private static final Settings DEFAULT_SETTINGS=new Settings();

    private record Sticker(String name,int x,int y,float width,float height) {}
    // North face UV rectangles from the local donor's chifan and bq2–bq9 bones.
    private static final Sticker[] STICKERS={
        null,
        new Sticker("吃白饭",2,210,95.5f,100),
        new Sticker("可怜巴巴",104,210,93.5f,100),
        new Sticker("比心",206,210,100,93.5f),
        new Sticker("开饭啦",308,210,100,99),
        new Sticker("瞪大眼",410,210,100,92),
        new Sticker("贴符",2,314,100,97.5f),
        new Sticker("流汗",104,314,100,94.5f),
        new Sticker("死机",206,314,100,98.5f),
        new Sticker("小丑",308,314,96.5f,100)
    };

    static final class Settings {
        int sticker;
        double side=.57,height=.33,depth=.04,size=.52;
        boolean blush;
        double blushDepth,blushHeight;

        void resetSticker(){
            side=.57;height=.33;depth=.04;size=.52;
        }
    }

    private YsmExpressions(){}

    public static double headHeight(){return YsmInteractionModel.DS_PREVIEW.headHeight();}
    static Settings settings(UUID entity){return SETTINGS.computeIfAbsent(entity,id->new Settings());}
    static int stickerCount(){return STICKERS.length-1;}
    static String stickerName(int id){return id==0?"跟随自主":STICKERS[id].name();}

    static boolean atlasAvailable(){
        if(atlasChecked)return atlasLoaded;
        atlasChecked=true;
        var path=FMLPaths.CONFIGDIR.get().resolve("xixi-sticker-atlas.png");
        if(!Files.isRegularFile(path))return false;
        try(var input=Files.newInputStream(path)){
            var image=NativeImage.read(input);
            int width=image.getWidth();
            if(width!=image.getHeight()||(width!=ATLAS_SIZE&&width!=2*ATLAS_SIZE)){
                image.close();
                LOG.warn("Unexpected sticker atlas dimensions at {}",path);
                return false;
            }
            var texture=new DynamicTexture(()->"大肥鱼表情包",image);
            Minecraft.getInstance().getTextureManager().register(ATLAS,texture);
            atlasLoaded=true;
        }catch(IOException ex){
            LOG.warn("Could not load the local 大肥鱼 sticker atlas",ex);
        }
        return atlasLoaded;
    }

    private static ExpressionApi.Display automatic(MinecraftServer server,UUID entity){
        return InteractionMod.allowsAutoExpression(server,entity)?ExpressionApi.current(server,entity):null;
    }

    public static void applyFace(MinecraftServer server,UUID entity,oo0oo0O0O0oo00OO00000Oo0 frame,PoseStack pose,SubmitNodeCollector queue){
        var settings=SETTINGS.getOrDefault(entity,DEFAULT_SETTINGS);
        var auto=automatic(server,entity);
        if(!settings.blush&&(auto==null||!auto.blush()))return;
        var geometry=frame==null?null:frame.o0Oo00O0Ooo00oOo0O000Ooo;
        var raw=frame==null?null:frame.O000O0000O00000oo0o0OooO;
        if(geometry==null||raw==null||raw.limit()!=geometry.OO0OoO00ooOOo0o00O000OoO().size()*BONE_BYTES){
            if(WAITING_FOR_FACE.add(entity))LOG.info("DS blush is waiting for the evaluated YSM bone buffer");
            return;
        }
        WAITING_FOR_FACE.remove(entity);
        FaceAccessories.renderBlush(frame,raw.duplicate().order(ByteOrder.nativeOrder()),settings,pose,queue);
    }

    static String normalize(String name){return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[ _.-]","");}

    public static void submitSticker(MinecraftServer server,UUID entity,Vec3 anchor,float yaw,CameraRenderState camera,
                                     PoseStack pose,SubmitNodeCollector queue){
        var settings=SETTINGS.getOrDefault(entity,DEFAULT_SETTINGS);
        var auto=automatic(server,entity);
        int autoId=auto==null?0:auto.sticker();
        if(autoId==0)AUTO_STICKERS_DRAWN.remove(entity);
        int id=autoId!=0?autoId:settings.sticker;
        if(id==0||camera.orientation==null||!atlasAvailable())return;
        var sticker=STICKERS[id];
        double angle=Math.toRadians(yaw);
        double dx=settings.side*Math.cos(angle)-settings.depth*Math.sin(angle);
        double dz=settings.side*Math.sin(angle)+settings.depth*Math.cos(angle);
        float width=(float)settings.size*sticker.width()/Math.max(sticker.width(),sticker.height());
        float height=(float)settings.size*sticker.height()/Math.max(sticker.width(),sticker.height());
        float u0=(sticker.x()+.5f)/ATLAS_SIZE,v0=(sticker.y()+.5f)/ATLAS_SIZE;
        float u1=(sticker.x()+sticker.width()-.5f)/ATLAS_SIZE,v1=(sticker.y()+sticker.height()-.5f)/ATLAS_SIZE;
        pose.pushPose();
        pose.translate(anchor.x+dx,anchor.y+settings.height,anchor.z+dz);
        pose.mulPose(camera.orientation);
        queue.submitCustomGeometry(pose,RenderTypes.entityTranslucentEmissive(ATLAS,false),(matrix,vertices)->{
            vertex(vertices,matrix,-width/2,-height/2,u0,v1,1);
            vertex(vertices,matrix, width/2,-height/2,u1,v1,1);
            vertex(vertices,matrix, width/2, height/2,u1,v0,1);
            vertex(vertices,matrix,-width/2, height/2,u0,v0,1);
        });
        pose.popPose();
        if(autoId!=0&&!Integer.valueOf(autoId).equals(AUTO_STICKERS_DRAWN.put(entity,autoId)))
            LOG.info("DS automatic sticker submitted to renderer: {}",STICKERS[autoId].name());
    }

    static void vertex(VertexConsumer out,PoseStack.Pose pose,float x,float y,float u,float v,float normalZ){
        vertex(out,pose,x,y,0,u,v,normalZ,LightCoordsUtil.FULL_BRIGHT);
    }

    static void vertex(VertexConsumer out,PoseStack.Pose pose,float x,float y,float z,float u,float v,float normalZ,int light){
        out.addVertex(pose,x,y,z).setColor(-1).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light).setNormal(pose,0,0,normalZ);
    }
}

package xixi.interaction;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.neoforged.fml.loading.FMLPaths;

public final class InteractionConfig {
    public int maxSeconds=600;
    public boolean alignPositions=true;
    public boolean enableCarry=false;
    public boolean enableBackCarry=true;
    public boolean enableRainUmbrella=true;
    public boolean enableSleepIdle=true;
    public boolean enableIdleBackHug=true;
    public int layoutRevision;
    public Map<String,InteractionRules.Layout> layouts=new LinkedHashMap<>();
    public Map<String,InteractionRules.LapOffset> lapOffsets=new LinkedHashMap<>();
    public static final Path FILE=FMLPaths.CONFIGDIR.get().resolve("xixi-interaction.json");
    public static InteractionConfig load() throws Exception {
        Gson gson=new GsonBuilder().setPrettyPrinting().create();
        InteractionConfig c=Files.exists(FILE)?gson.fromJson(Files.readString(FILE),InteractionConfig.class):new InteractionConfig();
        if(c==null||c.maxSeconds<10||c.maxSeconds>3600||c.layouts==null)throw new IllegalArgumentException("互动配置时长应为 10–3600 秒");
        for(var e:c.layouts.entrySet())if(!InteractionRules.ALL.contains(e.getKey())||e.getValue()==null||!e.getValue().valid())throw new IllegalArgumentException("无效站位配置："+e.getKey());
        if(c.layoutRevision==0){
            replaceDefault(c,"sit",.78,0,0);
            replaceDefault(c,"lean",.65,0,0);
            replaceDefault(c,"maid_lap",.72,.2,90);
            replaceDefault(c,"player_lap",.72,.2,90);
            replaceDefault(c,"back_hug",0,-.7,0);
            replaceDefault(c,"hand",.8,0,0);
            replaceDefault(c,"umbrella",.8,0,0);
            c.layoutRevision=1;
        }
        if(c.layoutRevision==1){
            replaceDefault(c,"hand",.64,0,0);
            c.layoutRevision=2;
        }
        if(c.layoutRevision==2){
            replaceDefault(c,"maid_lap",.67,.2,175);
            replaceDefault(c,"player_lap",.64,.05,0);
            c.layoutRevision=3;
        }
        if(c.layoutRevision==3){
            replaceDefault(c,"maid_lap",.55,.28,180);
            replaceDefault(c,"player_lap",.56,.12,0);
            c.layoutRevision=4;
        }
        if(c.layoutRevision==4){
            replaceDefault(c,"maid_lap",.67,.2,175);
            c.layoutRevision=5;
        }
        if(c.layoutRevision==5){
            replaceDefault(c,"maid_lap",.78,.34,0);
            replaceDefault(c,"maid_lap",.67,.2,175);
            c.layoutRevision=6;
        }
        for(String a:new TreeSet<>(InteractionRules.ALL))c.layouts.putIfAbsent(a,InteractionRules.layout(a));
        if(c.lapOffsets==null)c.lapOffsets=new LinkedHashMap<>();
        c.lapOffsets.entrySet().removeIf(e->!InteractionRules.validLapOffsetKey(e.getKey())||e.getValue()==null||!e.getValue().valid());
        if(!Files.exists(FILE)){Files.createDirectories(FILE.getParent());Files.writeString(FILE,gson.toJson(c));}
        return c;
    }
    private static void replaceDefault(InteractionConfig config,String action,double side,double forward,double yaw){
        var saved=config.layouts.get(action);
        if(saved!=null&&Math.abs(saved.side()-side)<1e-8&&Math.abs(saved.forward()-forward)<1e-8
            &&Math.abs(saved.yaw()-yaw)<1e-8)config.layouts.put(action,InteractionRules.layout(action));
    }
    public void save() throws Exception {
        Path temp=FILE.resolveSibling("xixi-interaction.tmp");
        Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(this));
        try{Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING);}
    }
}

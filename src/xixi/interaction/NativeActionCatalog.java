package xixi.interaction;

import com.elfmcys.yesstevemodel.Oo0O0oOoooooo0O0O00Ooo0O;
import com.elfmcys.yesstevemodel.o00O0ooooooOoo0oOO0oo0O0;
import com.elfmcys.yesstevemodel.ooo0oo0ooo0oo0oo0o0oooO0;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

final class NativeActionCatalog {
    private NativeActionCatalog(){}

    static void refresh(MinecraftServer server){
        if(server==null)return;
        // Match the catalog shown by YSM's extra-animation menu for this model.
        var model=o00O0ooooooOoo0oOO0oo0O0.OO0OoO00ooOOo0o00O000OoO(YsmInteractionModel.DS_PREVIEW.id()).orElse(null);
        if(model==null){
            NativeActions.updateCatalog(server,Map.of());
            return;
        }
        var properties=model.o0oo0000oooOo0ooOOOo0OOo().O0oo00O0OoooOOOO00ooO000();
        var actions=new LinkedHashMap<String,NativeActions.Action>();
        addMenu(model,properties.Oo0o00OoooOoo00o00O00O0o(),actions);
        for(var menu:properties.O0OoOoO0oo0O00O0ooo0o0OO().entrySet()){
            if(!NativeActions.isDance(menu.getKey()))addMenu(model,menu.getValue(),actions);
        }
        NativeActions.updateCatalog(server,Collections.unmodifiableMap(actions));
    }

    private static void addMenu(ooo0oo0ooo0oo0oo0o0oooO0 model,Map<String,String> menu,
                                Map<String,NativeActions.Action> actions){
        var animations=model.OO0OoO00ooOOo0o00O000OoO().oo000Oo000o0Ooooooo00oOo();
        for(var entry:menu.entrySet()){
            String id=entry.getKey();
            // The menu key identifies the button. YSM sends its value to the animation controller.
            String playback=entry.getValue();
            if(id.startsWith("#")||playback==null||playback.isBlank()||playback.startsWith("#"))continue;
            String label=Oo0O0oOoooooo0O0O00Ooo0O.OO0OoO00ooOOo0o00O000OoO(model,
                "properties.extra_animation."+id,playback);
            String description=Oo0O0oOoooooo0O0O00Ooo0O.OO0OoO00ooOOo0o00O000OoO(model,
                "properties.extra_animation."+id+".desc","");
            if(NativeActions.isDance(id)||NativeActions.isDance(playback)||NativeActions.isDance(label)
                ||NativeActions.isDance(description))continue;
            var animation=animations.get(playback);
            int ticks=animation==null?100:Math.clamp((int)Math.ceil(animation.O0oo00O0OoooOOOO00ooO000*20)+4,20,600);
            var action=new NativeActions.Action(id,label,playback,ticks);
            if(actions.putIfAbsent(id,action)!=null&&actions.values().stream().noneMatch(a->a.animation().equals(playback))){
                actions.put(id+"/"+playback,new NativeActions.Action(id+"/"+playback,label,playback,ticks));
            }
        }
    }
}

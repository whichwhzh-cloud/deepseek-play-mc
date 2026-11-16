package xixi.interaction;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.dwinovo.numen.plugins.ysm.Ysm;

/** The geometry and texture needed by the current YSM animation set. */
record YsmInteractionModel(String id,String texture,float headPivotPixels) {
    static final YsmInteractionModel COMPANION=new YsmInteractionModel("xixi-action-prototype","default",31.7f);
    static final YsmInteractionModel OWNER=new YsmInteractionModel("xixi-player-hug-test","texture",27.825f);
    // Initial estimate for the preview. The DS model does not provide editable rig data here.
    static final YsmInteractionModel DS_PREVIEW=new YsmInteractionModel("DS鲸鱼娘flash.ysm","default",26.4f);

    float headHeight(){return headPivotPixels/16f;}
    boolean matches(Ysm.Look look){return look!=null&&id.equals(look.model());}
    String selectCommand(){
        return "model set {player} "+StringArgumentType.escapeIfRequired(id)+" "+StringArgumentType.escapeIfRequired(texture);
    }
}

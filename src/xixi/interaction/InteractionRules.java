package xixi.interaction;

import java.util.*;

/** Pure rules shared by the server and the offline regression tests. Units: blocks/degrees. */
public final class InteractionRules {
    public enum Mode { TEST_YSM, VANILLA, DS_PREVIEW }
    public static boolean validLapOffsetKey(String key){return lap(key)||key.equals("ds_maid_lap")||key.equals("ds_player_lap");}
    public record Layout(double side, double forward, double yaw) {
        public double[] at(double x,double z,double facing) {
            double r=Math.toRadians(facing);
            return new double[]{x+side*Math.cos(r)-forward*Math.sin(r),z+side*Math.sin(r)+forward*Math.cos(r)};
        }
        public boolean valid(){return Double.isFinite(side)&&Double.isFinite(forward)&&Double.isFinite(yaw)&&Math.abs(side)<=2&&Math.abs(forward)<=2&&Math.abs(yaw)<=360;}
    }
    public static final Set<String> SEATED=Set.of("sit","lean","maid_lap","player_lap","brush");
    public static final Set<String> WALKING=Set.of("hand","umbrella");
    public static final Set<String> CARRY=Set.of("carry","lift");
    public static final Layout BACK_SEAT=new Layout(0,-.36,0);
    public static final double BACK_SEAT_HEIGHT=.58;
    public static boolean bound(String action){return action.equals("hand")||adjustable(action);}
    public static boolean adjustable(String action){return action.equals("hug")||action.equals("back_hug")||SEATED.contains(action);}
    public static boolean lap(String action){return action.equals("maid_lap")||action.equals("player_lap");}
    public static boolean seatedPat(String action){return action.equals("lean")||action.equals("maid_lap");}
    public record LapOffset(double side,double forward,double height){
        public static final LapOffset ZERO=new LapOffset(0,0,0);
        public boolean valid(){
            return Math.abs(side)<=.8&&Math.abs(forward)<=.8&&Math.abs(height)<=.5;
        }
    }
    public static final Set<String> ALL=Set.of("sit","lean","maid_lap","player_lap","brush","pat","hug","hand","back_hug","ask_pat","umbrella","carry","lift","sleep");
    public static String transition(String current,String next,boolean sameCompanion,boolean brush) {
        if(!ALL.contains(next))return "未知动作";
        if(SEATED.contains(next)&&!next.equals("sit")&&(!sameCompanion||!SEATED.contains(current)))return "请先与这个伙伴一起坐下";
        if(next.equals("brush")&&!brush)return "梳理需要手持原版刷子";
        return null;
    }
    public static Layout layout(String action) {
        return switch(action){
            case "sit" -> new Layout(.68,0,0);
            case "lean" -> new Layout(.58,0,0);
            case "maid_lap" -> new Layout(.67,.28,175);
            case "player_lap" -> new Layout(.64,.05,0);
            case "brush" -> new Layout(.8,.2,90);
            case "hug","pat","ask_pat" -> new Layout(0,.8,180);
            case "back_hug" -> new Layout(0,-.55,0);
            case "hand" -> new Layout(.48,0,0);
            case "umbrella" -> new Layout(.76,0,0);
            case "sleep" -> new Layout(.8,0,0);
            default -> new Layout(0,-.8,0);
        };
    }
}

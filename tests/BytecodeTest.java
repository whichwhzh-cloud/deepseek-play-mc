import java.io.*;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Instruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.StoreInstruction;
public final class BytecodeTest {
    static void method(String owner,String method,String descriptor)throws Exception{
        try(InputStream input=ClassLoader.getSystemResourceAsStream(owner+".class")){
            if(input==null)throw new AssertionError("Missing class "+owner);
            boolean found=ClassFile.of().parse(input.readAllBytes()).methods().stream().anyMatch(m->m.methodName().stringValue().equals(method)&&m.methodType().stringValue().equals(descriptor));
            if(!found)throw new AssertionError("Missing mixin target "+owner+"."+method+descriptor);
        }
    }
    public static void main(String[] args)throws Exception{
        checkNumenTaskSelection();
        method("net/minecraft/client/model/HumanoidModel","setupAnim","(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V");
        method("net/minecraft/client/Camera","alignWithEntity","(F)V");
        method("net/minecraft/world/entity/Entity","push","(Lnet/minecraft/world/entity/Entity;)V");
        method("net/minecraft/client/renderer/entity/EntityRenderer","extractRenderState","(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V");
        method("net/minecraft/client/renderer/entity/LivingEntityRenderer","extractRenderState","(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V");
        method("net/minecraft/client/renderer/entity/EntityRenderDispatcher","submit","(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V");
        method("com/dwinovo/numen/core/task/combat/AttackCompanionTask","cleanup","()V");
        String steering="com/dwinovo/numen/pathing/drive/Steering";
        String driveArgs="(Lnet/minecraft/server/level/ServerPlayer;Lcom/dwinovo/numen/pathing/body/Controls;";
        method(steering,"pass",driveArgs+"DD)V");
        method(steering,"stop",driveArgs+"DDD)Z");
        method("com/dwinovo/numen/pathing/plan/CostModel","overhead","(Lcom/dwinovo/numen/pathing/plan/Maneuver;)D");
        method("com/elfmcys/yesstevemodel/OOOoooO00oo000OOOOoO0OO0","OO0OoO00ooOOo0o00O000OoO",
            "(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lcom/elfmcys/yesstevemodel/OoOo0OOOoo0o0oOOoooooo0O;");
        checkYsmBoneBuffer();
        checkYsmFaceSubmit();
        System.out.println("PASS: mixin target descriptors match installed dependencies (not a launch test)");
    }
    static void checkYsmFaceSubmit()throws Exception{
        String owner="com/elfmcys/yesstevemodel/OoO0oo0o00o0oO000000o0OO";
        String name="OO0OoO00ooOOo0o00O000OoO";
        String stack="Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;";
        String base="Lcom/elfmcys/yesstevemodel/";
        String renderer="(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            +base+"OoOo0OOOoo0o0oOOoooooo0O;"+stack+"Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";
        String model="("+base+"oo0oo0O0O0oo00OO00000Oo0;"+base+"O0o0O0O00ooo0ooo000o0ooo;"
            +stack+"Lnet/minecraft/client/renderer/rendertype/RenderType;)V";
        try(InputStream input=ClassLoader.getSystemResourceAsStream(owner+".class")){
            if(input==null)throw new AssertionError("Missing installed YSM avatar renderer");
            var method=ClassFile.of().parse(input.readAllBytes()).methods().stream()
                .filter(m->m.methodName().equalsString(name)&&m.methodType().equalsString(renderer)).findFirst().orElseThrow();
            long targets=method.code().orElseThrow().elementStream().filter(element->element instanceof InvokeInstruction call
                &&call.owner().asInternalName().equals(owner)&&call.name().equalsString(name)&&call.type().equalsString(model)).count();
            if(targets!=1)throw new AssertionError("Expected one YSM body geometry submit, found "+targets);
        }
    }
    static void checkNumenTaskSelection()throws Exception{
        try(InputStream input=ClassLoader.getSystemResourceAsStream("com/dwinovo/numen/task/CompanionBrain.class")){
            if(input==null)throw new AssertionError("Missing Numen companion brain");
            var tick=ClassFile.of().parse(input.readAllBytes()).methods().stream()
                .filter(method->method.methodName().equalsString("tick")
                    &&method.methodType().equalsString("(Lcom/dwinovo/numen/entity/NumenPlayer;)V"))
                .findFirst().orElseThrow();
            var instructions=tick.code().orElseThrow().elementStream().filter(Instruction.class::isInstance).toList();
            for(int i=0;i<instructions.size()-1;i++){
                if(instructions.get(i) instanceof InvokeInstruction call
                    &&call.owner().asInternalName().equals("com/dwinovo/numen/task/TaskSelector")
                    &&call.name().equalsString("select")
                    &&instructions.get(i+1) instanceof StoreInstruction store&&store.slot()==2)return;
            }
            throw new AssertionError("Numen's chosen physical task is no longer stored in local 2");
        }
    }
    static void checkYsmBoneBuffer()throws Exception{
        String modelState="com/elfmcys/yesstevemodel/OoOOoOOO0ooOOO0oO0OO0oo0";
        try(InputStream input=ClassLoader.getSystemResourceAsStream(modelState+".class")){
            if(input==null)throw new AssertionError("Missing installed YSM model state");
            String descriptor="(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
                +"Lcom/elfmcys/yesstevemodel/O0o0O0O00ooo0ooo000o0ooo;"
                +"Lcom/elfmcys/yesstevemodel/oo0oo0O0O0oo00OO00000Oo0;)V";
            var methods=ClassFile.of().parse(input.readAllBytes()).methods();
            var copy=methods.stream().filter(m->m.methodName().stringValue().equals("OO0OoO00ooOOo0o00O000OoO")
                &&m.methodType().stringValue().equals(descriptor)).findFirst().orElseThrow();
            long targets=copy.code().orElseThrow().elementStream()
                .filter(element->element instanceof InvokeInstruction invocation
                    &&invocation.owner().asInternalName().equals("org/lwjgl/system/MemoryUtil")
                    &&invocation.name().stringValue().equals("memCopy")
                    &&invocation.type().stringValue().equals("([FLjava/nio/ByteBuffer;)V")).count();
            if(targets!=1)throw new AssertionError("Expected one YSM render bone snapshot, found "+targets);
        }
    }
}

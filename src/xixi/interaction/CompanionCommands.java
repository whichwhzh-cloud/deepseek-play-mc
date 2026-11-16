package xixi.interaction;

import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.mc.Echo;
import com.dwinovo.numen.mc.McApi;
import com.dwinovo.numen.permission.Action;
import com.dwinovo.numen.sdk.Fn;
import com.dwinovo.numen.sdk.Pending;
import com.dwinovo.numen.sdk.ServerCall;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

public final class CompanionCommands {
    private static final Set<String> DELEGATED = Set.of("tp", "teleport", "kill");

    public record Request(String command) {}
    public record Result(boolean success, String output) {}

    private CompanionCommands() {}

    @Fn("Run a vanilla tp, teleport, or kill command requested explicitly by your owner. Translate their natural-language request to one command. Use @s only for yourself. For kill, use the exact entity or selector the owner asked for. The command uses the owner's existing permissions and Numen's usual command approval.")
    public static Pending<Result> run(ServerCall call, Request request) {
        String command = request.command() == null ? "" : request.command().strip();
        if (command.startsWith("/")) command = command.substring(1).strip();
        String[] parts = command.split("\\s+", 2);
        if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0
            || parts.length != 2 || !DELEGATED.contains(parts[0])) {
            return Pending.of(new Result(false, "需要一条带目标的 tp、teleport 或 kill 指令。"));
        }
        var companion = call.her();
        var owner = singleplayerOwner(companion);
        if (owner == null) return Pending.of(new Result(false, "当前单人世界的所有者不在线。"));
        var commands = companion.level().getServer().getCommands();
        String invalid = McApi.problem(commands.getDispatcher(), command, source(companion, owner));
        if (invalid != null) return Pending.of(new Result(false, invalid));

        String line = command;
        UUID ownerId = owner.getUUID();
        return call.authorize(Action.command(line, commands.getDispatcher().getRoot()))
            .then(ignored -> execute(companion, ownerId, line));
    }

    private static ServerPlayer singleplayerOwner(NumenPlayer companion) {
        var server = companion.level().getServer();
        var owner = companion.resolveOwnerPlayer();
        return owner != null && !owner.hasDisconnected()
            && server.isSingleplayerOwner(new NameAndId(owner.getGameProfile())) ? owner : null;
    }

    private static CommandSourceStack source(NumenPlayer companion, ServerPlayer owner) {
        return companion.createCommandSourceStack().withPermission(owner.createCommandSourceStack().permissions());
    }

    private static Result execute(NumenPlayer companion, UUID ownerId, String line) {
        var owner = singleplayerOwner(companion);
        if (owner == null || !ownerId.equals(owner.getUUID()) || companion.isRemoved()) {
            return new Result(false, "角色状态已变化，指令未执行。");
        }
        var echo = new Echo(companion.commandSource().shouldInformAdmins());
        var succeeded = new AtomicBoolean();
        var commandSource = source(companion, owner).withSource(echo)
            .withCallback((success, result) -> { if (success) succeeded.set(true); });
        companion.level().getServer().getCommands().performPrefixedCommand(commandSource, line);
        return new Result(succeeded.get(), String.join("\n", echo.lines()));
    }
}

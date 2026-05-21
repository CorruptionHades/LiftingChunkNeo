package me.corruptionhades.liftingchunkneo.challenge;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ChallengeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("liftingchunks")
                .requires(source -> source.hasPermission(2))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("start")
                        .executes(ctx -> startChallenge(ctx.getSource())))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("stop")
                        .executes(ctx -> stopChallenge(ctx.getSource())))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("pause")
                        .executes(ctx -> pauseChallenge(ctx.getSource())))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("status")
                        .executes(ctx -> showStatus(ctx.getSource())))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("debug")
                        .executes(ctx -> toggleDebug(ctx.getSource())))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("forcelift")
                        .executes(ctx -> forceLift(ctx.getSource())))
                .then(Commands.literal("debug_force")
                        .then(Commands.argument("yForce", DoubleArgumentType.doubleArg())
                                .executes(ctx -> applyDebugForce(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "yForce")))))
        );
    }

    private static int startChallenge(CommandSourceStack source) {
        ChallengeManager mgr = ChallengeManager.getInstance();

        if (mgr.getState() == ChallengeState.ACTIVE) {
            source.sendFailure(Component.literal("§cChallenge is already running!"));
            return 0;
        }

        mgr.start();
        source.sendSuccess(() -> Component.literal("§a☁ LiftingChunks challenge started! Keep moving across chunks!"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int stopChallenge(CommandSourceStack source) {
        ChallengeManager mgr = ChallengeManager.getInstance();

        if (mgr.getState() == ChallengeState.IDLE) {
            source.sendFailure(Component.literal("§cNo challenge is running!"));
            return 0;
        }

        long elapsed = mgr.getElapsedMillis();
        long seconds = elapsed / 1000;
        long minutes = seconds / 60;
        long secs = seconds % 60;

        mgr.stop();
        source.sendSuccess(() -> Component.literal("§c§lChallenge ended! §7Final time: §e" + String.format("%02d:%02d", minutes, secs)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int pauseChallenge(CommandSourceStack source) {
        ChallengeManager mgr = ChallengeManager.getInstance();

        if (mgr.getState() == ChallengeState.IDLE) {
            source.sendFailure(Component.literal("§cNo challenge is running!"));
            return 0;
        }

        mgr.togglePause();

        if (mgr.getState() == ChallengeState.PAUSED) {
            source.sendSuccess(() -> Component.literal("§6⏸ Challenge paused"), true);
        } else {
            source.sendSuccess(() -> Component.literal("§a▶ Challenge resumed"), true);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int showStatus(CommandSourceStack source) {
        ChallengeManager mgr = ChallengeManager.getInstance();
        ChallengeState state = mgr.getState();

        if (state == ChallengeState.IDLE) {
            source.sendSuccess(() -> Component.literal("§7LiftingChunks: §cIdle"), false);
        } else {
            long elapsed = mgr.getElapsedMillis();
            long seconds = elapsed / 1000;
            long minutes = seconds / 60;
            long secs = seconds % 60;

            String stateColor = state == ChallengeState.ACTIVE ? "§aRunning" : "§6Paused";
            source.sendSuccess(() -> Component.literal("§7LiftingChunks: " + stateColor + " §7| §e" + String.format("%02d:%02d", minutes, secs)), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int toggleDebug(CommandSourceStack source) {
        ChallengeManager mgr = ChallengeManager.getInstance();
        mgr.toggleDebug();

        if (mgr.isDebugEnabled()) {
            source.sendSuccess(() -> Component.literal("§e[LiftingChunks] Debug mode enabled"), true);
        } else {
            source.sendSuccess(() -> Component.literal("§e[LiftingChunks] Debug mode disabled"), true);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int forceLift(CommandSourceStack source) {
        if (!source.isPlayer()) {
            source.sendFailure(Component.literal("§cOnly players can use this command"));
            return 0;
        }

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            ChallengeManager.getInstance().triggerChunkLift(player);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int applyDebugForce(CommandSourceStack source, double force) {
        if (!source.isPlayer()) {
            source.sendFailure(Component.literal("§cOnly players can use this command"));
            return 0;
        }

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            ChallengeManager.getInstance().applyForceToNearestChunk(player, force);
        }
        return Command.SINGLE_SUCCESS;
    }
}

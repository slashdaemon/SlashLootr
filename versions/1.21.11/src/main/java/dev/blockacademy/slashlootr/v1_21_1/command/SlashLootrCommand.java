package dev.blockacademy.slashlootr.v1_21_1.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.blockacademy.slashlootr.v1_21_1.store.SlashLootrState;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * Admin commands:
 *   /slashlootr forget here                 - wipe ALL players' loot at the block you're looking at
 *   /slashlootr forget at <pos>             - wipe ALL players' loot at a specific block
 *   /slashlootr forget player <player>      - wipe a player's loot at every container in this dimension
 *   /slashlootr forget all                  - wipe everything in this dimension
 */
public final class SlashLootrCommand {
    private SlashLootrCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx, Commands.CommandSelection env) {
        dispatcher.register(Commands.literal("slashlootr")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("forget")
                        .then(Commands.literal("here").executes(SlashLootrCommand::forgetHere))
                        .then(Commands.literal("at")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(SlashLootrCommand::forgetAt)))
                        .then(Commands.literal("player")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(SlashLootrCommand::forgetPlayer)))
                        .then(Commands.literal("all").executes(SlashLootrCommand::forgetAll))));
    }

    private static int forgetHere(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        BlockPos resolved = sp.blockPosition();
        var hit = sp.pick(5.0D, 0.0F, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult bhr) {
            resolved = bhr.getBlockPos();
        }
        final BlockPos target = resolved;
        SlashLootrState.get((ServerLevel) sp.level()).forgetBlock(target.asLong());
        ctx.getSource().sendSuccess(() -> Component.literal("SlashLootr: forgot all player loot at " + posStr(target)), true);
        return 1;
    }

    private static int forgetAt(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        ServerLevel level = ctx.getSource().getLevel();
        SlashLootrState.get(level).forgetBlock(pos.asLong());
        ctx.getSource().sendSuccess(() -> Component.literal("SlashLootr: forgot all player loot at " + posStr(pos)), true);
        return 1;
    }

    private static int forgetPlayer(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "player");
        ServerLevel level = ctx.getSource().getLevel();
        for (ServerPlayer p : targets) {
            SlashLootrState.get(level).forgetPlayerEverywhere(p.getUUID());
        }
        int count = targets.size();
        ctx.getSource().sendSuccess(() -> Component.literal("SlashLootr: forgot loot for " + count + " player(s) in " + level.dimension().identifier()), true);
        return targets.size();
    }

    private static int forgetAll(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        // Replace state by clearing — easiest via direct map access would require exposing; for now:
        // Iterate known entries and forget each. Simplification: just create a new empty state
        // via the existing forget* helpers iteratively isn't possible without enumeration.
        // Pragmatic v1: instruct admins to delete the .dat file while server is offline.
        ctx.getSource().sendFailure(Component.literal("forget all not yet implemented — stop server and delete world/<dim>/data/slashlootr.dat"));
        return 0;
    }

    private static String posStr(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}

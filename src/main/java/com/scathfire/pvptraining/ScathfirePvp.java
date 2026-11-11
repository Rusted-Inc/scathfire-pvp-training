package com.scathfire.pvptraining;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

public class ScathfirePvp implements ModInitializer {
	public static final String MOD_ID = "scathfirepvp";
	public static final String PVP_WORLD_NAME = "Scathfire PvP World";
	public static final Logger LOGGER = LoggerFactory.getLogger("Scathfire's PvP Training");


	/** The fight each real player is in, by player id. */
	private static final Map<UUID, Stats.Session> SESSIONS = new HashMap<>();
	/** Players in Free Practice, who have a training dummy out. */
	private static final Set<UUID> PRACTICING = new HashSet<>();
	/** A hidden chat line that tells the menus a match just ended, so they show the results. */
	public static final String MATCH_OVER = "[scathfirepvp:match-over]";
	private static final String DUMMY = "Dummy";

	private record Later(int atTick, Runnable task) {}
	private static final List<Later> LATER = new ArrayList<>();
	private static int serverTicks;

	/** Runs a task a few ticks from now, e.g. after a teleport has settled. */
	public static void later(int ticks, Runnable task) {
		LATER.add(new Later(serverTicks + ticks, task));
	}

	private static void tick(MinecraftServer server) {
		serverTicks++;
		WorldBuilder.tick(server);
		AimTrainer.tick(server);
		if (!LATER.isEmpty()) {
			List<Later> due = new ArrayList<>();
			LATER.removeIf(l -> {
				if (l.atTick() <= serverTicks) {
					due.add(l);
					return true;
				}
				return false;
			});
			for (Later l : due) l.task().run();
		}
	}

	/** Puts a player at a spot, facing a direction (180 is north). */
	public static void teleport(ServerPlayer player, double x, double y, double z, float yaw) {
		if (player.level() instanceof ServerLevel level) {
			player.teleportTo(level, x, y, z, java.util.Set.<Relative>of(), yaw, 10f, true);
		}
	}

	/** In the PvP World, sends the player to a zone's spawn pad. Returns true if it moved them. */
	private static boolean sendToZone(MinecraftServer server, ServerPlayer player, Zones.Zone zone) {
		if (zone == null || !isPvpWorld(server) || !WorldBuilder.built(server)) return false;
		boolean inside = player.getX() >= zone.minX() && player.getX() <= zone.maxX() + 1
			&& player.getZ() >= zone.minZ() && player.getZ() <= zone.maxZ() + 1;
		teleport(player, zone.x() + 0.5, Zones.FLOOR + 1, zone.padZ() + 0.5, 180f);
		return !inside;
	}

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> registerCommands(dispatcher));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(AimTrainer::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(ScathfirePvp::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(ScathfirePvp::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(ScathfirePvp::afterDeath);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> onJoin(server, handler.player)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			endSession(handler.player.getUUID());
			AimTrainer.stop(server, handler.player, false);
		});
		LOGGER.info("Scathfire's PvP Training loaded");
	}

	// ---------------------------------------------------------------- commands

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("spt")
			.executes(ScathfirePvp::help)
			.then(Commands.literal("fight")
				.then(Commands.argument("mode", StringArgumentType.word())
					.then(Commands.argument("level", StringArgumentType.word())
						.executes(ctx -> fight(ctx, StringArgumentType.getString(ctx, "mode"), StringArgumentType.getString(ctx, "level"), 0))
						.then(Commands.argument("rounds", IntegerArgumentType.integer(0, 9))
							.executes(ctx -> fight(ctx, StringArgumentType.getString(ctx, "mode"), StringArgumentType.getString(ctx, "level"),
								IntegerArgumentType.getInteger(ctx, "rounds")))))))
			.then(Commands.literal("practice")
				.then(Commands.argument("mode", StringArgumentType.word())
					.executes(ctx -> practice(ctx, StringArgumentType.getString(ctx, "mode")))))
			.then(Commands.literal("drill")
				.then(Commands.argument("name", StringArgumentType.word())
					.executes(ctx -> drill(ctx, StringArgumentType.getString(ctx, "name")))))
			.then(Commands.literal("stop").executes(ScathfirePvp::stop))
			.then(Commands.literal("go")
				.then(Commands.argument("zone", StringArgumentType.word())
					.executes(ctx -> go(ctx, StringArgumentType.getString(ctx, "zone")))))
			.then(Commands.literal("aim")
				.then(Commands.argument("speed", StringArgumentType.word())
					.executes(ctx -> aim(ctx, StringArgumentType.getString(ctx, "speed")))))
			.then(Commands.literal("rebuild").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				if (!isPvpWorld(ctx.getSource().getServer())) {
					say(player, "Rebuilding only works in the PvP World.", ChatFormatting.YELLOW);
					return 0;
				}
				WorldBuilder.buildAll(player);
				return 1;
			})));
	}

	private static int go(CommandContext<CommandSourceStack> ctx, String zoneId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		if (!isPvpWorld(server)) {
			say(player, "The arenas are in the PvP World. Click PvP World on the title screen.", ChatFormatting.YELLOW);
			return 0;
		}
		if ("hub".equals(zoneId)) {
			teleport(player, 0.5, Zones.FLOOR + 1, 6.5, 180f);
			return 1;
		}
		Zones.Zone zone = Zones.zone(zoneId);
		if (zone == null) {
			say(player, "Unknown arena.", ChatFormatting.RED);
			return 0;
		}
		teleport(player, zone.x() + 0.5, Zones.FLOOR + 1, zone.padZ() + 0.5, 180f);
		say(player, zone.title() + ": " + zone.subtitle() + ". Press a button to start.", ChatFormatting.AQUA);
		return 1;
	}

	private static int aim(CommandContext<CommandSourceStack> ctx, String speed) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		if (!AimTrainer.validSpeed(speed)) {
			say(player, "Speeds are easy, medium and pro.", ChatFormatting.RED);
			return 0;
		}
		if (!carpetInstalled()) {
			say(player, "The Aim Trainer bot needs the bot engine (Carpet PvP).", ChatFormatting.RED);
			return 0;
		}
		if (!isPvpWorld(server) || !WorldBuilder.built(server)) {
			say(player, "The Aim Trainer runs in the PvP World.", ChatFormatting.YELLOW);
			return 0;
		}
		stopEverything(server, player);
		AimTrainer.start(server, player, speed);
		return 1;
	}

	/** Ends any fight, practice dummy or aim session the player has going. */
	private static void stopEverything(MinecraftServer server, ServerPlayer player) {
		if (SESSIONS.containsKey(player.getUUID())) {
			endSession(player.getUUID());
			if (carpetInstalled()) runAs(server, player, "auto-setup stop");
		}
		removeDummy(server, player);
		AimTrainer.stop(server, player, false);
	}

	private static int help(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSuccess(() -> Component.literal("Scathfire's PvP Training: press P to open the menu, or use /spt fight <mode> <level>, /spt drill <name>, /spt stop").withStyle(ChatFormatting.AQUA), false);
		return 1;
	}

	private static int fight(CommandContext<CommandSourceStack> ctx, String modeId, String levelId, int rounds) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Modes.Mode mode = Modes.mode(modeId);
		Modes.Level level = Modes.level(levelId);
		if (mode == null || level == null) {
			say(player, "Unknown mode or level.", ChatFormatting.RED);
			return 0;
		}
		if (!mode.ready()) {
			say(player, mode.name() + " is coming soon.", ChatFormatting.YELLOW);
			return 0;
		}
		if (!carpetInstalled()) {
			say(player, "The bot engine (Carpet PvP) isn't installed. Put the Carpet PvP 26.3 jar in your mods folder.", ChatFormatting.RED);
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		endSession(player.getUUID());
		removeDummy(server, player);
		Stats.Session session = new Stats.Session();
		session.mode = mode.id();
		session.level = level.id();
		session.rounds = rounds;
		session.startedAt = System.currentTimeMillis();
		SESSIONS.put(player.getUUID(), session);
		AimTrainer.stop(server, player, false);
		say(player, "Setting up " + mode.name() + " vs a " + level.name() + " bot, " + Modes.roundsLabel(rounds)
			+ ". Press P and choose Stop fight to end it.", ChatFormatting.AQUA);
		String command = "auto-setup " + mode.engine() + " " + level.engine();
		if (sendToZone(server, player, Zones.forMode(mode.id()))) {
			later(10, () -> runAs(server, player, command));
		} else {
			runAs(server, player, command);
		}
		return 1;
	}

	private static int practice(CommandContext<CommandSourceStack> ctx, String modeId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		Modes.Mode mode = Modes.mode(modeId);
		if (mode == null || !mode.practiceReady()) {
			say(player, "That mode isn't ready for Free Practice yet.", ChatFormatting.YELLOW);
			return 0;
		}
		if (!carpetInstalled()) {
			say(player, "Free Practice needs the bot engine (Carpet PvP) for the kit and the dummy.", ChatFormatting.RED);
			return 0;
		}
		if (SESSIONS.containsKey(player.getUUID())) {
			endSession(player.getUUID());
			runAs(server, player, "auto-setup stop");
		}
		removeDummy(server, player);
		AimTrainer.stop(server, player, false);
		boolean moved = sendToZone(server, player, Zones.zone("practice"));
		Runnable give = () -> {
			runAs(server, player, "bot kit give @s " + mode.kit());
			runAs(server, player, "player " + DUMMY + " spawn at ~ ~ ~-6 facing 0 0");
		};
		if (moved) later(10, give); else give.run();
		PRACTICING.add(player.getUUID());
		say(player, "Free Practice: you have the " + mode.name() + " kit and a training dummy that doesn't hit back. Run /spt practice "
			+ mode.id() + " again for a fresh kit and dummy.", ChatFormatting.AQUA);
		return 1;
	}

	private static void removeDummy(MinecraftServer server, ServerPlayer player) {
		if (PRACTICING.remove(player.getUUID()) && carpetInstalled()) {
			runAs(server, player, "player " + DUMMY + " kill");
		}
	}

	private static int drill(CommandContext<CommandSourceStack> ctx, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Modes.Drill drill = Modes.drill(name);
		if (drill == null) {
			say(player, "Unknown drill.", ChatFormatting.RED);
			return 0;
		}
		if (!carpetInstalled()) {
			say(player, "The bot engine (Carpet PvP) isn't installed, and the drills need it.", ChatFormatting.RED);
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		stopEverything(server, player);
		String command = "bot drill " + drill.engine();
		if (sendToZone(server, player, Zones.zone(drill.id()))) later(10, () -> runAs(server, player, command));
		else runAs(server, player, command);
		return 1;
	}

	private static int stop(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		boolean hadFight = SESSIONS.containsKey(player.getUUID());
		endSession(player.getUUID());
		removeDummy(ctx.getSource().getServer(), player);
		AimTrainer.stop(ctx.getSource().getServer(), player, false);
		if (carpetInstalled()) {
			runAs(ctx.getSource().getServer(), player, "bot drill stop");
			if (hadFight) runAs(ctx.getSource().getServer(), player, "auto-setup stop");
		}
		say(player, "Stopped. Your items are back.", ChatFormatting.AQUA);
		return 1;
	}

	/** Runs a command as the player, so Carpet PvP sets things up where they stand. */
	private static int runAs(MinecraftServer server, ServerPlayer player, String command) {
		CommandSourceStack source = server.createCommandSourceStack().withEntity(player).withPosition(player.position());
		server.getCommands().performPrefixedCommand(source, command);
		return 1;
	}

	private static boolean carpetInstalled() {
		return FabricLoader.getInstance().isModLoaded("carpet");
	}

	private static void say(ServerPlayer player, String text, ChatFormatting color) {
		player.sendSystemMessage(Component.literal(text).withStyle(color));
	}

	// ---------------------------------------------------------------- stats

	/** Carpet PvP's bots are server-side fake players. */
	public static boolean isBot(Entity entity) {
		return entity instanceof ServerPlayer && entity.getClass().getSimpleName().contains("Fake");
	}

	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		Entity attacker = source.getEntity();
		if (attacker instanceof ServerPlayer player && !isBot(player) && isBot(entity)) {
			Stats.Session s = SESSIONS.get(player.getUUID());
			if (s != null) {
				s.hits++;
				s.damageDealt += damageTaken;
			}
		}
		if (entity instanceof ServerPlayer player && !isBot(player)) {
			Stats.Session s = SESSIONS.get(player.getUUID());
			if (s != null) {
				if (blocked) s.blocked++;
				else {
					s.hitsTaken++;
					s.damageTaken += damageTaken;
				}
			}
		}
	}

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (isBot(entity) && source.getEntity() instanceof ServerPlayer killer && !isBot(killer)) {
			Stats.Session s = SESSIONS.get(killer.getUUID());
			if (s != null) {
				s.wins++;
				record(s, true);
				say(killer, "You won the round! Score " + s.wins + " - " + s.losses, ChatFormatting.GREEN);
				checkMatchOver(killer, s);
			}
		} else if (entity instanceof ServerPlayer player && !isBot(player)) {
			Stats.Session s = SESSIONS.get(player.getUUID());
			if (s != null) {
				s.losses++;
				record(s, false);
				say(player, "The bot took that round. Score " + s.wins + " - " + s.losses, ChatFormatting.YELLOW);
				checkMatchOver(player, s);
			}
		}
	}

	/** In a best-of match, ends it once someone has won enough rounds. */
	private static void checkMatchOver(ServerPlayer player, Stats.Session s) {
		if (s.rounds <= 0) return;
		int need = s.rounds / 2 + 1;
		if (s.wins < need && s.losses < need) return;
		s.result = s.wins >= need ? "win" : "loss";
		MinecraftServer server = player.level().getServer();
		server.execute(() -> {
			if (SESSIONS.get(player.getUUID()) != s) return;
			endSession(player.getUUID());
			if ("win".equals(s.result)) {
				Stats.Data data = Stats.load();
				data.matchWins++;
				Stats.save(data);
			}
			say(player, ("win".equals(s.result) ? "You won the match " : "The bot won the match ") + s.wins + " - " + s.losses + "!",
				"win".equals(s.result) ? ChatFormatting.GREEN : ChatFormatting.RED);
			if (carpetInstalled()) runAs(server, player, "auto-setup stop");
			player.sendSystemMessage(Component.literal(MATCH_OVER));
		});
	}

	private static void record(Stats.Session s, boolean won) {
		Stats.Data data = Stats.load();
		Stats.ModeStats m = data.modes.computeIfAbsent(s.mode, k -> new Stats.ModeStats());
		if (won) {
			m.wins++;
			int levelIndex = Modes.LEVELS.indexOf(Modes.level(s.level));
			data.highestBeaten.merge(s.mode, levelIndex, Math::max);
		} else {
			m.losses++;
		}
		data.last = copyOf(s);
		Stats.save(data);
	}

	private static void endSession(UUID id) {
		Stats.Session s = SESSIONS.remove(id);
		if (s == null) return;
		s.endedAt = System.currentTimeMillis();
		Stats.Data data = Stats.load();
		Stats.ModeStats m = data.modes.computeIfAbsent(s.mode, k -> new Stats.ModeStats());
		m.hits += s.hits;
		m.hitsTaken += s.hitsTaken;
		m.damageDealt += s.damageDealt;
		m.damageTaken += s.damageTaken;
		m.blocked += s.blocked;
		data.last = s;
		Stats.save(data);
	}

	private static Stats.Session copyOf(Stats.Session s) {
		Stats.Session c = new Stats.Session();
		c.mode = s.mode;
		c.level = s.level;
		c.wins = s.wins;
		c.losses = s.losses;
		c.hits = s.hits;
		c.hitsTaken = s.hitsTaken;
		c.damageDealt = s.damageDealt;
		c.damageTaken = s.damageTaken;
		c.blocked = s.blocked;
		c.startedAt = s.startedAt;
		c.rounds = s.rounds;
		c.result = s.result;
		c.endedAt = System.currentTimeMillis();
		return c;
	}

	// ---------------------------------------------------------------- the hub

	private static boolean isPvpWorld(MinecraftServer server) {
		return PVP_WORLD_NAME.equals(server.getWorldData().getLevelName());
	}

	private static void onJoin(MinecraftServer server, ServerPlayer player) {
		if (!isPvpWorld(server)) return;
		say(player, "Welcome to the PvP World, " + player.getName().getString() + "! Press P any time for the training menu.", ChatFormatting.AQUA);
		if (!carpetInstalled()) {
			say(player, "Heads up: the bot engine (Carpet PvP for 26.3) isn't installed, so bots and drills won't start.", ChatFormatting.YELLOW);
		}
		if (!WorldBuilder.built(server) && !WorldBuilder.building()) {
			WorldBuilder.buildAll(player);
		}
	}

}

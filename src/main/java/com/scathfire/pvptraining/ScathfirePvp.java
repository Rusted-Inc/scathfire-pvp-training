package com.scathfire.pvptraining;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

public class ScathfirePvp implements ModInitializer {
	public static final String MOD_ID = "scathfirepvp";
	public static final String PVP_WORLD_NAME = "Scathfire PvP World";
	public static final Logger LOGGER = LoggerFactory.getLogger("Scathfire's PvP Training");

	private static final String HUB_MARKER = "scathfirepvp-hub.txt";

	/** The fight each real player is in, by player id. */
	private static final Map<UUID, Stats.Session> SESSIONS = new HashMap<>();
	/** Players in Free Practice, who have a training dummy out. */
	private static final Set<UUID> PRACTICING = new HashSet<>();
	/** A hidden chat line that tells the menus a match just ended, so they show the results. */
	public static final String MATCH_OVER = "[scathfirepvp:match-over]";
	private static final String DUMMY = "Dummy";

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> registerCommands(dispatcher));
		ServerLivingEntityEvents.AFTER_DAMAGE.register(ScathfirePvp::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(ScathfirePvp::afterDeath);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> onJoin(server, handler.player)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> endSession(handler.player.getUUID()));
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
			.then(Commands.literal("hub").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				buildHub(ctx.getSource().getServer(), player, true);
				return 1;
			})));
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
		say(player, "Setting up " + mode.name() + " vs a " + level.name() + " bot, " + Modes.roundsLabel(rounds)
			+ ". Press P and choose Stop fight to end it.", ChatFormatting.AQUA);
		return runAs(server, player, "auto-setup " + mode.engine() + " " + level.engine());
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
		runAs(server, player, "bot kit give @s " + mode.kit());
		runAs(server, player, "player " + DUMMY + " spawn");
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
		return runAs(ctx.getSource().getServer(), player, "bot drill " + drill.engine());
	}

	private static int stop(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		boolean hadFight = SESSIONS.containsKey(player.getUUID());
		endSession(player.getUUID());
		removeDummy(ctx.getSource().getServer(), player);
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
		buildHub(server, player, false);
		say(player, "Welcome to the PvP World, " + player.getName().getString() + "! Press P to open the training menu.", ChatFormatting.AQUA);
		if (!carpetInstalled()) {
			say(player, "Heads up: the bot engine (Carpet PvP for 26.3) isn't installed, so bots and drills won't start.", ChatFormatting.YELLOW);
		}
	}

	/** A navy grid-paper floor around the player, built once per world unless forced. */
	private static void buildHub(MinecraftServer server, ServerPlayer player, boolean force) {
		Path marker = server.getWorldPath(LevelResource.ROOT).resolve(HUB_MARKER);
		if (!force && Files.exists(marker)) return;
		if (!(player.level() instanceof ServerLevel level)) return;

		BlockPos center = player.blockPosition().below();
		int r = 15;
		BlockState navy = Blocks.CONCRETE.blue().defaultBlockState();
		BlockState line = Blocks.CONCRETE.lightBlue().defaultBlockState();
		BlockState margin = Blocks.CONCRETE.red().defaultBlockState();
		BlockState edge = Blocks.STAINED_GLASS.lightBlue().defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();

		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				BlockPos floor = center.offset(dx, 0, dz);
				BlockState state = navy;
				if (dx == -r + 3) state = margin;
				else if (Math.floorMod(dx, 5) == 0 || Math.floorMod(dz, 5) == 0) state = line;
				level.setBlockAndUpdate(floor, state);
				for (int dy = 1; dy <= 4; dy++) {
					level.setBlockAndUpdate(floor.above(dy), air);
				}
				if (Math.abs(dx) == r || Math.abs(dz) == r) {
					level.setBlockAndUpdate(floor.above(), edge);
				}
			}
		}
		try {
			Files.writeString(marker, "hub built\n");
		} catch (Exception e) {
			LOGGER.warn("Could not write hub marker", e);
		}
	}
}

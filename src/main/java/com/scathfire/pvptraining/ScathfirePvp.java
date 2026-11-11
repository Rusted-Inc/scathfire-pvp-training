package com.scathfire.pvptraining;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
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
						.executes(ctx -> fight(ctx, StringArgumentType.getString(ctx, "mode"), StringArgumentType.getString(ctx, "level"))))))
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

	private static int fight(CommandContext<CommandSourceStack> ctx, String modeId, String levelId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
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
		endSession(player.getUUID());
		Stats.Session session = new Stats.Session();
		session.mode = mode.id();
		session.level = level.id();
		session.startedAt = System.currentTimeMillis();
		SESSIONS.put(player.getUUID(), session);
		say(player, "Setting up " + mode.name() + " vs a " + level.name() + " bot. Press P and choose Stop fight to end it.", ChatFormatting.AQUA);
		return runAs(ctx.getSource().getServer(), player, "auto-setup " + mode.engine() + " " + level.engine());
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
				say(killer, "You won the round! Wins this session: " + s.wins, ChatFormatting.GREEN);
			}
		} else if (entity instanceof ServerPlayer player && !isBot(player)) {
			Stats.Session s = SESSIONS.get(player.getUUID());
			if (s != null) {
				s.losses++;
				record(s, false);
			}
		}
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
		BlockState navy = Blocks.BLUE_CONCRETE.defaultBlockState();
		BlockState line = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
		BlockState margin = Blocks.RED_CONCRETE.defaultBlockState();
		BlockState edge = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
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

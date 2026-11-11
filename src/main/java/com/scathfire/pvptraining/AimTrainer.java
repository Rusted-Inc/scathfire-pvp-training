package com.scathfire.pvptraining;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Aim Trainer: a Carpet PvP fake player that strafes, jumps and sprints like an opponent
 * but never attacks. You get 60 seconds to hit it as often as you can. It takes no damage.
 */
public final class AimTrainer {
	public static final String BOT = "AimBot";
	/** Hidden chat lines the client reads to drive the score display. */
	public static final String MARK = "[scathfirepvp:aim]";
	private static final int DURATION_TICKS = 60 * 20;
	private static final Random RANDOM = new Random();

	/** How a speed moves: ticks between changes, chance to jump, chance to sprint. */
	private record Speed(String id, int changeEvery, float jump, float sprint) {}

	private static final Map<String, Speed> SPEEDS = Map.of(
		"easy", new Speed("easy", 30, 0.10f, 0.0f),
		"medium", new Speed("medium", 16, 0.25f, 0.5f),
		"pro", new Speed("pro", 8, 0.45f, 1.0f)
	);

	private static final class Session {
		UUID player;
		Speed speed;
		int ticks;
		int hits;
		int nextChange;
		double centerX;
		double centerZ;
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private AimTrainer() {}

	public static boolean validSpeed(String id) {
		return SPEEDS.containsKey(id);
	}

	public static void start(MinecraftServer server, ServerPlayer player, String speedId) {
		stop(server, player, false);
		Zones.Zone zone = Zones.zone("aim");
		Speed speed = SPEEDS.get(speedId);
		Session s = new Session();
		s.player = player.getUUID();
		s.speed = speed;
		s.centerX = zone.x() + 0.5;
		s.centerZ = zone.padZ() - 10.5;

		ScathfirePvp.teleport(player, zone.x() + 0.5, Zones.FLOOR + 1, zone.padZ() + 0.5, 180f);
		run(server, String.format(Locale.ROOT, "player %s spawn at %.1f %d %.1f facing 0 0", BOT, s.centerX, Zones.FLOOR + 1, s.centerZ));
		SESSIONS.put(s.player, s);
		player.sendSystemMessage(Component.literal(MARK + "start:" + speed.id() + ":" + (DURATION_TICKS / 20)));
		player.sendSystemMessage(Component.literal("Aim Trainer (" + speed.id() + "): hit the bot as many times as you can in 60 seconds!").withStyle(ChatFormatting.AQUA));
	}

	/** Ends the session. With {@code report}, the player gets their score. */
	public static void stop(MinecraftServer server, ServerPlayer player, boolean report) {
		Session s = SESSIONS.remove(player.getUUID());
		if (s == null) return;
		run(server, "player " + BOT + " kill");
		if (report) {
			player.sendSystemMessage(Component.literal(MARK + "end:" + s.hits + ":" + s.speed.id()));
			player.sendSystemMessage(Component.literal("Time! You hit the bot " + s.hits + " times.").withStyle(ChatFormatting.GREEN));
		} else {
			player.sendSystemMessage(Component.literal(MARK + "cancel"));
		}
	}

	public static boolean active(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	/** Counts hits on the bot and cancels all damage to it. Returns false to cancel. */
	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer bot) || !BOT.equals(bot.getName().getString())) return true;
		Entity attacker = source.getEntity();
		if (attacker instanceof ServerPlayer player) {
			Session s = SESSIONS.get(player.getUUID());
			if (s != null) {
				s.hits++;
				player.sendSystemMessage(Component.literal(MARK + "hit:" + s.hits));
				run(player.level().getServer(), "playsound minecraft:entity.experience_orb.pickup player " + player.getName().getString() + " ~ ~ ~ 0.6 1.6");
			}
		}
		return false;
	}

	public static void tick(MinecraftServer server) {
		Iterator<Session> it = SESSIONS.values().iterator();
		while (it.hasNext()) {
			Session s = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(s.player);
			ServerPlayer bot = server.getPlayerList().getPlayerByName(BOT);
			if (player == null) {
				it.remove();
				run(server, "player " + BOT + " kill");
				continue;
			}
			s.ticks++;
			if (s.ticks >= DURATION_TICKS) {
				it.remove();
				SESSIONS.put(s.player, s);
				stop(server, player, true);
				return;
			}
			if (bot == null) continue;

			if (s.ticks % 4 == 0) {
				run(server, String.format(Locale.ROOT, "player %s look at %.2f %.2f %.2f", BOT, player.getX(), player.getEyeY(), player.getZ()));
			}
			if (s.ticks >= s.nextChange) {
				s.nextChange = s.ticks + s.speed.changeEvery() + RANDOM.nextInt(Math.max(1, s.speed.changeEvery()));
				move(server, s, bot);
			}
		}
	}

	/** Picks the next move like a strafing player, steering back toward the middle when it drifts. */
	private static void move(MinecraftServer server, Session s, ServerPlayer bot) {
		double dx = s.centerX - bot.getX();
		double dz = s.centerZ - bot.getZ();
		double yaw = Math.toRadians(bot.getYRot());
		double leftX = Math.cos(yaw);
		double leftZ = Math.sin(yaw);
		double fwdX = -Math.sin(yaw);
		double fwdZ = Math.cos(yaw);
		boolean far = dx * dx + dz * dz > 36;

		String dir;
		if (far) {
			double side = dx * leftX + dz * leftZ;
			double ahead = dx * fwdX + dz * fwdZ;
			dir = Math.abs(side) > Math.abs(ahead) ? (side > 0 ? "left" : "right") : (ahead > 0 ? "forward" : "backward");
		} else {
			float roll = RANDOM.nextFloat();
			dir = roll < 0.4f ? "left" : roll < 0.8f ? "right" : roll < 0.9f ? "forward" : "backward";
		}
		run(server, "player " + BOT + " move " + dir);
		run(server, "player " + BOT + (RANDOM.nextFloat() < s.speed.sprint() ? " sprint" : " unsprint"));
		if (RANDOM.nextFloat() < s.speed.jump()) run(server, "player " + BOT + " jump");
	}

	private static void run(MinecraftServer server, String command) {
		CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
		try {
			server.getCommands().performPrefixedCommand(source, command);
		} catch (Exception e) {
			ScathfirePvp.LOGGER.warn("Aim trainer command failed: {}", command, e);
		}
	}
}

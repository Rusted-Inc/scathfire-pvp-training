package com.scathfire.pvptraining;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Builds the pre-made PvP World with commands (fill, setblock, summon), one area per server tick
 * so the game doesn't freeze. Each area's chunks are loaded first so every command lands.
 */
public final class WorldBuilder {
	/** Written into the world folder once the v0.1.1 world is built. */
	public static final String MARKER = "scathfirepvp-world-v2.txt";
	private static final int Y = Zones.FLOOR;
	private static final String LABEL_TAG = "spt_label";

	private record Job(String name, int minX, int minZ, int maxX, int maxZ, List<String> commands) {}

	private static final Deque<Job> QUEUE = new ArrayDeque<>();
	private static UUID notify;
	private static int total;

	private WorldBuilder() {}

	public static boolean built(MinecraftServer server) {
		return Files.exists(marker(server));
	}

	private static Path marker(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(MARKER);
	}

	public static boolean building() {
		return !QUEUE.isEmpty();
	}

	/** Queues the whole world. The player gets updates and is sent to the hub when it is done. */
	public static void buildAll(ServerPlayer player) {
		QUEUE.clear();
		List<String> clear = new ArrayList<>();
		clear.add("kill @e[type=text_display,tag=" + LABEL_TAG + "]");
		clear.add("gamerule spawn_mobs false");
		clear.add("gamerule advance_time false");
		clear.add("gamerule advance_weather false");
		clear.add("time set noon");
		QUEUE.add(new Job("setup", -2, -2, 2, 2, clear));
		QUEUE.add(hub());
		for (Zones.Zone zone : Zones.ZONES) QUEUE.add(zone(zone));
		total = QUEUE.size();
		notify = player.getUUID();
		player.sendSystemMessage(Component.literal("Building your PvP World: the hub, 6 fight zones and 5 drill rooms...").withStyle(ChatFormatting.AQUA));
	}

	/** Called every server tick: builds the next area in the queue. */
	public static void tick(MinecraftServer server) {
		if (QUEUE.isEmpty()) return;
		Job job = QUEUE.poll();
		ServerLevel level = server.overworld();
		for (int cx = Math.floorDiv(job.minX(), 16); cx <= Math.floorDiv(job.maxX(), 16); cx++) {
			for (int cz = Math.floorDiv(job.minZ(), 16); cz <= Math.floorDiv(job.maxZ(), 16); cz++) {
				level.getChunk(cx, cz);
			}
		}
		CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
		for (String command : job.commands()) {
			try {
				server.getCommands().performPrefixedCommand(source, command);
			} catch (Exception e) {
				ScathfirePvp.LOGGER.warn("World build command failed: {}", command, e);
			}
		}

		ServerPlayer player = notify == null ? null : server.getPlayerList().getPlayer(notify);
		if (QUEUE.isEmpty()) {
			try {
				Files.writeString(marker(server), "PvP World v0.1.1 built\n");
			} catch (Exception e) {
				ScathfirePvp.LOGGER.warn("Could not write the world marker", e);
			}
			if (player != null) {
				ScathfirePvp.teleport(player, 0.5, Y + 1, 6.5, 180f);
				player.sendSystemMessage(Component.literal("Your PvP World is ready! Press a button to go to an arena, or press P for the menu.").withStyle(ChatFormatting.GREEN));
			}
			notify = null;
		} else if (player != null && (total - QUEUE.size()) % 4 == 0) {
			player.sendSystemMessage(Component.literal("Building... " + (total - QUEUE.size()) + " of " + total).withStyle(ChatFormatting.GRAY));
		}
	}

	// ------------------------------------------------------------------ the hub

	private static Job hub() {
		List<String> c = new ArrayList<>();
		int r = 18;
		clearAbove(c, -r, -r, r, r, 16);
		fill(c, -r, Y, -r, r, Y, r, "blue_concrete");
		for (int i = -r; i <= r; i += 6) {
			fill(c, i, Y, -r, i, Y, r, "light_blue_concrete");
			fill(c, -r, Y, i, r, Y, i, "light_blue_concrete");
		}
		fill(c, -r + 3, Y, -r, -r + 3, Y, r, "red_concrete");
		walls(c, -r, -r, r, r, 6);
		c.add("setworldspawn 0 " + (Y + 1) + " 6");

		label(c, 0.5, Y + 6, -2.5, "Scathfire's PvP Training", "#F3A034", 3f);
		label(c, 0.5, Y + 4.6, -2.5, "Press a button to go to an arena. P opens the menu.", "#C9D5EA", 1f);

		// North row: the fight zones and Free Practice.
		String[] fights = {"sword", "crystal", "mace", "smp", "ranged", "practice"};
		for (int i = 0; i < fights.length; i++) {
			Zones.Zone z = Zones.zone(fights[i]);
			int x = -10 + i * 4;
			button(c, x, -10, "spt go " + z.id(), z.title(), z.kind() == Zones.Kind.PRACTICE ? "#C9D5EA" : "#8FB4FF");
		}
		// South row: the aim trainer and the drills.
		String[] drills = {"aim", "crystaltiming", "retotem", "stunslam", "pearlcatch"};
		for (int i = 0; i < drills.length; i++) {
			Zones.Zone z = Zones.zone(drills[i]);
			button(c, -8 + i * 4, 12, "spt go " + z.id(), z.title(), "#F3A034");
		}
		label(c, 0.5, Y + 3.2, -12.5, "FIGHT A BOT", "#8FB4FF", 1.4f);
		label(c, 0.5, Y + 3.2, 14.5, "DRILLS", "#F3A034", 1.4f);
		button(c, 14, 0, "spt rebuild", "Rebuild arenas", "#FF7A7F");
		return new Job("hub", -r, -r, r, r, c);
	}

	// ------------------------------------------------------------------ a zone

	private static Job zone(Zones.Zone z) {
		List<String> c = new ArrayList<>();
		int x1 = z.minX(), x2 = z.maxX(), z1 = z.minZ(), z2 = z.maxZ();
		clearAbove(c, x1, z1, x2, z2, z.height());
		fill(c, x1, Y, z1, x2, Y, z2, z.floor());
		for (int x = x1; x <= x2; x += 5) fill(c, x, Y, z1, x, Y, z2, z.lines());
		for (int zz = z1; zz <= z2; zz += 5) fill(c, x1, Y, zz, x2, Y, zz, z.lines());
		fill(c, x1 + 3, Y, z1, x1 + 3, Y, z2, "red_concrete");
		walls(c, x1, z1, x2, z2, z.height());

		int cx = z.x();
		int pad = z.padZ();
		fill(c, cx - 1, Y, pad - 1, cx + 1, Y, pad + 1, "light_blue_concrete");
		label(c, cx + 0.5, Y + 5.5, pad - 6.5, z.title(), "#EAF0FB", 3f);
		label(c, cx + 0.5, Y + 4.3, pad - 6.5, z.subtitle(), "#9FB0CC", 1.2f);

		int row = pad - 3;
		switch (z.kind()) {
			case FIGHT -> {
				for (int i = 0; i < Modes.LEVELS.size(); i++) {
					Modes.Level level = Modes.LEVELS.get(i);
					button(c, cx - 6 + i * 3, row, "spt fight " + z.id() + " " + level.id() + " 3", level.name(), i >= 3 ? "#F3A034" : "#8FB4FF");
				}
				label(c, cx + 0.5, Y + 3.3, row + 0.5 - 1.5, "Pick a bot level (best of 3)", "#C9D5EA", 0.9f);
			}
			case PRACTICE -> {
				String[] kits = {"sword", "crystal", "mace", "smp", "ranged", "axe"};
				for (int i = 0; i < kits.length; i++) {
					Modes.Mode mode = Modes.mode(kits[i]);
					button(c, cx - 7 + i * 3, row, "spt practice " + mode.id(), mode.name(), "#8FB4FF");
				}
				label(c, cx + 0.5, Y + 3.3, row - 1, "Pick a kit. A dummy appears that never hits back.", "#C9D5EA", 0.9f);
			}
			case DRILL -> {
				button(c, cx, row, "spt drill " + z.id(), "Start drill", "#F3A034");
			}
			case AIM -> {
				String[] speeds = {"easy", "medium", "pro"};
				for (int i = 0; i < speeds.length; i++) {
					button(c, cx - 3 + i * 3, row, "spt aim " + speeds[i], speeds[i].substring(0, 1).toUpperCase(Locale.ROOT) + speeds[i].substring(1), i == 2 ? "#F3A034" : "#8FB4FF");
				}
				label(c, cx + 0.5, Y + 3.3, row - 1, "60 seconds. Hit the bot as many times as you can.", "#C9D5EA", 0.9f);
			}
		}
		button(c, cx + 10, row, "spt stop", "Stop", "#FF7A7F");
		button(c, cx - 10, row, "spt go hub", "Back to hub", "#C9D5EA");
		return new Job(z.id(), x1, z1, x2, z2, c);
	}

	// ------------------------------------------------------------------ pieces

	private static void fill(List<String> c, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		c.add(String.format(Locale.ROOT, "fill %d %d %d %d %d %d %s", x1, y1, z1, x2, y2, z2, block));
	}

	/** Empties the space above the floor, in slices small enough for one fill each. */
	private static void clearAbove(List<String> c, int x1, int z1, int x2, int z2, int height) {
		int perSlice = Math.max(1, 30000 / ((x2 - x1 + 1) * height));
		for (int z = z1; z <= z2; z += perSlice) {
			fill(c, x1 - 1, Y + 1, z, x2 + 1, Y + height, Math.min(z2, z + perSlice - 1), "air");
		}
	}

	/** Glass at your feet so you can see the edge, invisible barriers above it. */
	private static void walls(List<String> c, int x1, int z1, int x2, int z2, int height) {
		fill(c, x1, Y + 1, z1, x2, Y + 1, z1, "light_blue_stained_glass");
		fill(c, x1, Y + 1, z2, x2, Y + 1, z2, "light_blue_stained_glass");
		fill(c, x1, Y + 1, z1, x1, Y + 1, z2, "light_blue_stained_glass");
		fill(c, x2, Y + 1, z1, x2, Y + 1, z2, "light_blue_stained_glass");
		fill(c, x1, Y + 2, z1, x2, Y + height, z1, "barrier");
		fill(c, x1, Y + 2, z2, x2, Y + height, z2, "barrier");
		fill(c, x1, Y + 2, z1, x1, Y + height, z2, "barrier");
		fill(c, x2, Y + 2, z1, x2, Y + height, z2, "barrier");
	}

	/** A command block in the floor with a button on top, and a floating label above it. */
	private static void button(List<String> c, int x, int z, String command, String text, String color) {
		String run = "execute as @p[distance=..6] run " + command;
		c.add(String.format(Locale.ROOT, "setblock %d %d %d command_block{Command:\"%s\"} replace", x, Y, z, run));
		c.add(String.format(Locale.ROOT, "setblock %d %d %d stone_button[face=floor] replace", x, Y + 1, z));
		label(c, x + 0.5, Y + 2.1, z + 0.5, text, color, 1f);
	}

	private static void label(List<String> c, double x, double y, double z, String text, String color, float scale) {
		String safe = text.replace("\\", "").replace("\"", "'");
		c.add(String.format(Locale.ROOT,
			"summon text_display %.2f %.2f %.2f {Tags:[\"%s\"],billboard:\"center\",shadow:1b,background:0,"
				+ "text:{text:\"%s\",color:\"%s\",bold:1b},"
				+ "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[%.2ff,%.2ff,%.2ff]}}",
			x, y, z, LABEL_TAG, safe, color, scale, scale, scale));
	}
}

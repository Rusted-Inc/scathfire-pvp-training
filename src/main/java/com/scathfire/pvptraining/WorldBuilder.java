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
	public static final String MARKER = "scathfirepvp-world-v3.txt";
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
		clear.add("kill @e[tag=" + LABEL_TAG + "]");
		clear.add("gamerule spawn_mobs false");
		clear.add("gamerule advance_time false");
		clear.add("gamerule advance_weather false");
		clear.add("time set noon");
		QUEUE.add(new Job("setup", -2, -2, 2, 2, clear));
		QUEUE.add(hub());
		for (Zones.Zone zone : Zones.ZONES) QUEUE.add(zone(zone));
		total = QUEUE.size();
		notify = player.getUUID();
		player.sendSystemMessage(Component.literal("Building your PvP World (map revamp): the hub, 6 fight zones and 5 drill rooms...").withStyle(ChatFormatting.AQUA));
	}

	/** Called every server tick: builds the next area in the queue. */
	private static int wait;

	public static void tick(MinecraftServer server) {
		if (QUEUE.isEmpty()) return;
		// One area every 10 ticks, so building never causes one long freeze.
		if (wait-- > 0) return;
		wait = 10;
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
				ScathfirePvp.teleport(player, 0.5, Y + 1, 0.5, 180f);
				player.sendSystemMessage(Component.literal("Your PvP World is ready! Press a button to go to an arena, or press P for the menu.").withStyle(ChatFormatting.GREEN));
			}
			notify = null;
		} else if (player != null && (total - QUEUE.size()) % 4 == 0) {
			player.sendSystemMessage(Component.literal("Building... " + (total - QUEUE.size()) + " of " + total).withStyle(ChatFormatting.GRAY));
		}
	}

	// ------------------------------------------------------------------ the hub

	/** A kit, worn by an armor stand at the hub gate, the way practice servers show their kits. */
	private record Gear(String head, String chest, String legs, String feet, String main, String off) {}

	private static Gear gear(String id) {
		return switch (id) {
			case "crystal" -> new Gear("netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots", "end_crystal", "totem_of_undying");
			case "mace" -> new Gear("netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots", "mace", "wind_charge");
			case "smp" -> new Gear("netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots", "netherite_sword", "shield");
			case "ranged" -> new Gear("iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "bow", "arrow");
			case "practice" -> new Gear("iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "iron_axe", "shield");
			default -> new Gear("diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots", "diamond_sword", "golden_apple");
		};
	}

	private static Job hub() {
		List<String> c = new ArrayList<>();
		int r = 20;
		clearAbove(c, -r, -r, r, r, 14);
		fill(c, -r, Y, -r, r, Y, r, "blue_concrete");
		for (int i = -r; i <= r; i += 5) {
			fill(c, i, Y, -r, i, Y, r, "light_blue_concrete");
			fill(c, -r, Y, i, r, Y, i, "light_blue_concrete");
		}
		fill(c, -r + 3, Y, -r, -r + 3, Y, r, "red_concrete");
		ring(c, -r, -r, r, r, "quartz_block");
		walls(c, -r, -r, r, r, 8);
		towers(c, -r, -r, r, r, "blue_concrete", "sea_lantern", 8);

		// The spawn dais in the middle.
		fill(c, -3, Y, -3, 3, Y, 3, "light_blue_concrete");
		fill(c, -1, Y, -1, 1, Y, 1, "orange_concrete");
		c.add(String.format(Locale.ROOT, "setblock 0 %d 0 sea_lantern", Y));
		c.add("setworldspawn 0 " + (Y + 1) + " 0");

		label(c, 0.5, Y + 10, -15.5, "Scathfire's PvP Training", "#F3A034", 4f);
		label(c, 0.5, Y + 8.4, -15.5, "Walk through a gate and press its button. P opens the menu.", "#C9D5EA", 1.4f);

		// North gates: the fight zones and Free Practice, each with its kit on an armor stand.
		String[] fights = {"sword", "crystal", "mace", "smp", "ranged", "practice"};
		for (int i = 0; i < fights.length; i++) {
			Zones.Zone z = Zones.zone(fights[i]);
			gate(c, -15 + i * 6, -12, z, true);
		}
		// South gates: the aim trainer and the drills.
		String[] drills = {"aim", "crystaltiming", "retotem", "stunslam", "pearlcatch"};
		for (int i = 0; i < drills.length; i++) {
			gate(c, -12 + i * 6, 12, Zones.zone(drills[i]), false);
		}
		label(c, 0.5, Y + 3.4, -6.5, "FIGHT A BOT", "#8FB4FF", 1.6f);
		label(c, 0.5, Y + 3.4, 7.5, "DRILLS", "#F3A034", 1.6f);
		button(c, 17, 0, "spt rebuild", "Rebuild arenas", "#FF7A7F");
		return new Job("hub", -r, -r, r, r, c);
	}

	/** An archway in the zone's own blocks, a button under it, and for fight zones its kit on a stand. */
	private static void gate(List<String> c, int x, int z, Zones.Zone zone, boolean kit) {
		Zones.Palette p = Zones.palette(zone.look());
		fill(c, x - 1, Y, z - 2, x + 1, Y, z + 1, p.floor());
		fill(c, x - 1, Y + 1, z, x - 1, Y + 4, z, p.pillar());
		fill(c, x + 1, Y + 1, z, x + 1, Y + 4, z, p.pillar());
		fill(c, x - 1, Y + 5, z, x + 1, Y + 5, z, p.border());
		button(c, x, z + (z < 0 ? 1 : -1), "spt go " + zone.id(), "", "#000000");
		label(c, x + 0.5, Y + 6.6, z + 0.5, zone.title(), zone.kind() == Zones.Kind.FIGHT || zone.kind() == Zones.Kind.PRACTICE ? "#8FB4FF" : "#F3A034", 1.3f);
		if (kit) {
			Gear g = gear(zone.id());
			c.add(String.format(Locale.ROOT,
				"summon armor_stand %.1f %d %.1f {Tags:[\"%s\"],NoGravity:1b,Invulnerable:1b,ShowArms:1b,NoBasePlate:1b,Rotation:[0f,0f],"
					+ "equipment:{head:{id:\"minecraft:%s\"},chest:{id:\"minecraft:%s\"},legs:{id:\"minecraft:%s\"},feet:{id:\"minecraft:%s\"},"
					+ "mainhand:{id:\"minecraft:%s\"},offhand:{id:\"minecraft:%s\"}}}",
				x + 0.5, Y + 1, z - 1.5, LABEL_TAG, g.head(), g.chest(), g.legs(), g.feet(), g.main(), g.off()));
		}
	}

	// ------------------------------------------------------------------ a zone

	private static Job zone(Zones.Zone z) {
		List<String> c = new ArrayList<>();
		Zones.Palette p = Zones.palette(z.look());
		int x1 = z.minX(), x2 = z.maxX(), z1 = z.minZ(), z2 = z.maxZ();
		int cx = z.x();
		int pad = z.padZ();
		clearAbove(c, x1, z1, x2, z2, z.height());
		fill(c, x1, Y, z1, x2, Y, z2, p.floor());
		for (int x = x1; x <= x2; x += 5) fill(c, x, Y, z1, x, Y, z2, p.lines());
		for (int zz = z1; zz <= z2; zz += 5) fill(c, x1, Y, zz, x2, Y, zz, p.lines());
		ring(c, x1, z1, x2, z2, p.border());
		walls(c, x1, z1, x2, z2, z.height());
		towers(c, x1, z1, x2, z2, p.pillar(), p.light(), 7);

		// Side pillars with lights, well clear of where Carpet PvP builds its arena.
		for (int zz = z1 + 8; zz <= z2 - 4; zz += 8) {
			for (int side : new int[] {-16, 16}) {
				fill(c, cx + side, Y + 1, zz, cx + side, Y + 4, zz, p.pillar());
				c.add(String.format(Locale.ROOT, "setblock %d %d %d %s", cx + side, Y + 5, zz, p.light()));
			}
		}
		decorate(c, z, p);

		// The spawn pad and its buttons.
		fill(c, cx - 1, Y, pad - 1, cx + 1, Y, pad + 1, "light_blue_concrete");
		c.add(String.format(Locale.ROOT, "setblock %d %d %d orange_concrete", cx, Y, pad));
		// The zone's name hangs high over the far wall, out of the way of the fight.
		label(c, cx + 0.5, Y + 14, z1 + 3.5, z.title(), "#EAF0FB", 7f);
		label(c, cx + 0.5, Y + 11.5, z1 + 3.5, z.subtitle(), "#9FB0CC", 2.5f);

		int row = pad - 3;
		switch (z.kind()) {
			case FIGHT -> {
				for (int i = 0; i < Modes.LEVELS.size(); i++) {
					Modes.Level level = Modes.LEVELS.get(i);
					button(c, cx - 6 + i * 3, row, "spt fight " + z.id() + " " + level.id() + " 3", level.name(), i >= 3 ? "#F3A034" : "#8FB4FF");
				}
				label(c, cx + 0.5, Y + 3.3, row + 0.5, "Pick a bot level (best of 3)", "#C9D5EA", 0.9f);
			}
			case PRACTICE -> {
				String[] kits = {"sword", "crystal", "mace", "smp", "ranged", "axe"};
				for (int i = 0; i < kits.length; i++) {
					Modes.Mode mode = Modes.mode(kits[i]);
					button(c, cx - 7 + i * 3, row, "spt practice " + mode.id(), mode.name(), "#8FB4FF");
				}
				label(c, cx + 0.5, Y + 3.3, row + 0.5, "Pick a kit. A dummy appears that never hits back.", "#C9D5EA", 0.9f);
			}
			case DRILL -> button(c, cx, row, "spt drill " + z.id(), "Start drill", "#F3A034");
			case AIM -> {
				String[] speeds = {"easy", "medium", "pro"};
				for (int i = 0; i < speeds.length; i++) {
					button(c, cx - 3 + i * 3, row, "spt aim " + speeds[i], speeds[i].substring(0, 1).toUpperCase(Locale.ROOT) + speeds[i].substring(1), i == 2 ? "#F3A034" : "#8FB4FF");
				}
				label(c, cx + 0.5, Y + 3.3, row + 0.5, "60 seconds. Hit the bot as many times as you can.", "#C9D5EA", 0.9f);
			}
		}
		button(c, cx + 10, row, "spt stop", "Stop", "#FF7A7F");
		button(c, cx - 10, row, "spt go hub", "Back to hub", "#C9D5EA");
		return new Job(z.id(), x1, z1, x2, z2, c);
	}

	/**
	 * Each look gets its own details, all in the side strips (more than 12 blocks from the
	 * centre) or past the far end, so Carpet PvP's arena always has room.
	 */
	private static void decorate(List<String> c, Zones.Zone z, Zones.Palette p) {
		int cx = z.x();
		int z1 = z.minZ();
		int pad = z.padZ();
		int[] sides = {-14, 14};
		switch (z.look()) {
			case END -> {
				for (int dx : new int[] {-6, 6}) {
					fill(c, cx + dx - 1, Y + 1, z1 + 5, cx + dx + 1, Y + 10, z1 + 7, "obsidian");
					c.add(String.format(Locale.ROOT, "setblock %d %d %d end_rod", cx + dx, Y + 11, z1 + 6));
				}
				for (int s : sides) {
					for (int zz = pad - 36; zz <= pad - 6; zz += 6) fill(c, cx + s, Y, zz, cx + s + (s < 0 ? -1 : 1), Y, zz + 2, "end_stone_bricks");
				}
			}
			case DEEP_DARK -> {
				for (int s : new int[] {-15, 15}) {
					for (int zz : new int[] {pad - 14, pad - 26}) {
						fill(c, cx + s - 1, Y + 1, zz - 1, cx + s + 1, Y + 16, zz + 1, "deepslate_bricks");
						c.add(String.format(Locale.ROOT, "setblock %d %d %d soul_lantern", cx + s, Y + 17, zz));
					}
					for (int zz = pad - 38; zz <= pad - 4; zz += 7) fill(c, cx + s - 2, Y, zz, cx + s, Y, zz + 1, "sculk");
				}
			}
			case MOSSY -> {
				for (int s : new int[] {-16, 16}) {
					for (int zz = pad - 38; zz <= pad - 4; zz += 9) {
						c.add(String.format(Locale.ROOT, "place feature minecraft:oak %d %d %d", cx + s, Y + 1, zz + 4));
					}
				}
				for (int s : sides) {
					for (int zz = pad - 34; zz <= pad - 8; zz += 13) fill(c, cx + s - 1, Y + 1, zz, cx + s + 1, Y + 1, zz + 2, "moss_block");
				}
			}
			case DESERT -> {
				for (int s : sides) {
					for (int zz = pad - 36; zz <= pad - 8; zz += 7) {
						fill(c, cx + s, Y + 1, zz, cx + s, Y + 2, zz + 3, "sandstone");
						c.add(String.format(Locale.ROOT, "setblock %d %d %d chiseled_sandstone", cx + s, Y + 3, zz + 1));
					}
				}
			}
			case YARD -> {
				for (int s : sides) {
					for (int zz = pad - 36; zz <= pad - 8; zz += 7) {
						fill(c, cx + s, Y + 1, zz, cx + s, Y + 2, zz, "hay_block");
						c.add(String.format(Locale.ROOT, "setblock %d %d %d target", cx + s, Y + 3, zz));
					}
				}
			}
			case RANGE -> {
				for (int s : sides) {
					for (int zz = pad - 36; zz <= pad - 8; zz += 6) {
						c.add(String.format(Locale.ROOT, "setblock %d %d %d target", cx + s, Y + 1, zz));
						c.add(String.format(Locale.ROOT, "setblock %d %d %d sea_lantern", cx + s, Y + 2, zz));
					}
				}
			}
			default -> {
				for (int s : sides) {
					for (int zz = pad - 36; zz <= pad - 8; zz += 7) fill(c, cx + s, Y + 1, zz, cx + s, Y + 1, zz, "quartz_slab");
				}
			}
		}
	}

	/** A two-block border in the zone's trim block, just inside the walls. */
	private static void ring(List<String> c, int x1, int z1, int x2, int z2, String block) {
		fill(c, x1, Y, z1, x2, Y, z1 + 1, block);
		fill(c, x1, Y, z2 - 1, x2, Y, z2, block);
		fill(c, x1, Y, z1, x1 + 1, Y, z2, block);
		fill(c, x2 - 1, Y, z1, x2, Y, z2, block);
	}

	/** A 3x3 tower in each inside corner with a light on top. */
	private static void towers(List<String> c, int x1, int z1, int x2, int z2, String block, String light, int height) {
		int[][] corners = {{x1 + 2, z1 + 2}, {x2 - 4, z1 + 2}, {x1 + 2, z2 - 4}, {x2 - 4, z2 - 4}};
		for (int[] k : corners) {
			fill(c, k[0], Y + 1, k[1], k[0] + 2, Y + height, k[1] + 2, block);
			c.add(String.format(Locale.ROOT, "setblock %d %d %d %s", k[0] + 1, Y + height + 1, k[1] + 1, light));
		}
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
		if (!text.isEmpty()) label(c, x + 0.5, Y + 2.1, z + 0.5, text, color, 1f);
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

package com.scathfire.pvptraining;

import java.util.List;

/**
 * Every area of the pre-made PvP World: the hub, a zone for each fight mode, Free Practice,
 * and a room for each drill. Zones face north: you stand on the pad looking at a row of
 * buttons, and Carpet PvP builds its fight arena about 20 blocks in front of you
 * (x within 8 of the centre, z from pad-28 to pad-12). Decorations stay out of that space.
 */
public final class Zones {
	private Zones() {}

	/** The floor height of the flat world (grass is at -61, you stand at -60). */
	public static final int FLOOR = -61;

	public enum Kind { FIGHT, PRACTICE, DRILL, AIM }

	/** The look of a zone, with a few ideas taken from PvP practice server maps. */
	public enum Look { CLASSIC, END, DEEP_DARK, MOSSY, DESERT, YARD, RANGE }

	/**
	 * @param id     matches a mode id, "practice", a drill id, or "aim"
	 * @param x      centre of the zone
	 * @param padZ   z of the spawn pad; the zone runs from padZ + 6 north to padZ - 44
	 * @param height how tall the barrier walls are
	 */
	public record Zone(String id, String title, String subtitle, Kind kind, int x, int padZ, Look look, int height) {
		public int minX() { return x - 20; }
		public int maxX() { return x + 20; }
		public int minZ() { return padZ - 44; }
		public int maxZ() { return padZ + 6; }
	}

	/** Blocks for each look: floor, grid lines, border ring, pillars, the light on top. */
	public record Palette(String floor, String lines, String border, String pillar, String light) {}

	public static Palette palette(Look look) {
		return switch (look) {
			case CLASSIC -> new Palette("blue_concrete", "light_blue_concrete", "quartz_block", "quartz_pillar", "sea_lantern");
			case END -> new Palette("obsidian", "crying_obsidian", "end_stone_bricks", "purpur_pillar", "end_rod");
			case DEEP_DARK -> new Palette("deepslate_tiles", "polished_deepslate", "cobbled_deepslate", "deepslate_bricks", "soul_lantern");
			case MOSSY -> new Palette("grass_block", "moss_block", "mossy_stone_bricks", "stone_bricks", "lantern");
			case DESERT -> new Palette("smooth_sandstone", "cut_sandstone", "chiseled_sandstone", "sandstone", "lantern");
			case YARD -> new Palette("light_gray_concrete", "white_concrete", "polished_andesite", "stripped_oak_log", "lantern");
			case RANGE -> new Palette("blue_concrete", "light_blue_concrete", "polished_blackstone", "polished_blackstone_bricks", "sea_lantern");
		};
	}

	private static final int FIGHT_ROW = -60;
	private static final int DRILL_ROW = 100;

	public static final List<Zone> ZONES = List.of(
		new Zone("sword", "Sword", "Crits, W-tap, strafing", Kind.FIGHT, -150, FIGHT_ROW, Look.CLASSIC, 20),
		new Zone("crystal", "Crystal", "Hit crystal, anchors, totems", Kind.FIGHT, -90, FIGHT_ROW, Look.END, 24),
		new Zone("mace", "Mace", "Smash timing, stun slam", Kind.FIGHT, -30, FIGHT_ROW, Look.DEEP_DARK, 48),
		new Zone("smp", "SMP", "Netherite, shield, pearls", Kind.FIGHT, 30, FIGHT_ROW, Look.MOSSY, 20),
		new Zone("ranged", "Ranged", "Bow pressure, leading shots", Kind.FIGHT, 90, FIGHT_ROW, Look.DESERT, 20),
		new Zone("practice", "Free Practice", "A kit and a dummy, no score", Kind.PRACTICE, 150, FIGHT_ROW, Look.YARD, 20),
		new Zone("aim", "Aim Trainer", "Hit a bot that moves like a player", Kind.AIM, -120, DRILL_ROW, Look.RANGE, 20),
		new Zone("crystaltiming", "Crystal timing", "Place and break, fast", Kind.DRILL, -60, DRILL_ROW, Look.END, 20),
		new Zone("retotem", "Re-totem", "New totem after every pop", Kind.DRILL, 0, DRILL_ROW, Look.CLASSIC, 20),
		new Zone("stunslam", "Stun slam", "Axe the shield, then smash", Kind.DRILL, 60, DRILL_ROW, Look.DEEP_DARK, 40),
		new Zone("pearlcatch", "Pearl catch", "Pearl + wind charge for height", Kind.DRILL, 120, DRILL_ROW, Look.MOSSY, 48)
	);

	public static Zone zone(String id) {
		for (Zone z : ZONES) if (z.id().equals(id)) return z;
		return null;
	}

	/** Where a mode's fights happen: its own zone, or Sword's for anything without one. */
	public static Zone forMode(String modeId) {
		Zone z = zone(modeId);
		return z != null && z.kind() == Kind.FIGHT ? z : zone("sword");
	}
}

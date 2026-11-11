package com.scathfire.pvptraining;

import java.util.List;

/**
 * Every area of the pre-made PvP World: the hub, a zone for each fight mode, Free Practice,
 * and a room for each drill. Zones face north: you stand on the pad looking at a row of
 * buttons, and Carpet PvP builds its fight arena about 20 blocks in front of you.
 */
public final class Zones {
	private Zones() {}

	/** The floor height of the flat world (grass is at -61, you stand at -60). */
	public static final int FLOOR = -61;

	public enum Kind { FIGHT, PRACTICE, DRILL, AIM }

	/**
	 * @param id        matches a mode id, "practice", a drill id, or "aim"
	 * @param x         centre of the zone
	 * @param padZ      z of the spawn pad; the zone runs from padZ + 6 north to padZ - 44
	 * @param floor     main floor block
	 * @param lines     grid line block
	 * @param height    how tall the barrier walls are
	 */
	public record Zone(String id, String title, String subtitle, Kind kind, int x, int padZ, String floor, String lines, int height) {
		public int minX() { return x - 20; }
		public int maxX() { return x + 20; }
		public int minZ() { return padZ - 44; }
		public int maxZ() { return padZ + 6; }
	}

	private static final int FIGHT_ROW = -60;
	private static final int DRILL_ROW = 110;

	public static final List<Zone> ZONES = List.of(
		new Zone("sword", "Sword", "Crits, W-tap, strafing", Kind.FIGHT, -150, FIGHT_ROW, "blue_concrete", "light_blue_concrete", 20),
		new Zone("crystal", "Crystal", "Hit crystal, anchors, totems", Kind.FIGHT, -90, FIGHT_ROW, "obsidian", "crying_obsidian", 24),
		new Zone("mace", "Mace", "Smash timing, stun slam", Kind.FIGHT, -30, FIGHT_ROW, "polished_deepslate", "deepslate_tiles", 48),
		new Zone("smp", "SMP", "Netherite, shield, pearls", Kind.FIGHT, 30, FIGHT_ROW, "grass_block", "stone_bricks", 20),
		new Zone("ranged", "Ranged", "Bow pressure, leading shots", Kind.FIGHT, 90, FIGHT_ROW, "smooth_sandstone", "cut_sandstone", 20),
		new Zone("practice", "Free Practice", "A kit and a dummy, no score", Kind.PRACTICE, 150, FIGHT_ROW, "light_gray_concrete", "light_blue_concrete", 20),
		new Zone("aim", "Aim Trainer", "Hit a bot that moves like a player", Kind.AIM, -120, DRILL_ROW, "blue_concrete", "light_blue_concrete", 20),
		new Zone("crystaltiming", "Crystal timing", "Place and break, fast", Kind.DRILL, -60, DRILL_ROW, "obsidian", "crying_obsidian", 20),
		new Zone("retotem", "Re-totem", "New totem after every pop", Kind.DRILL, 0, DRILL_ROW, "blue_concrete", "light_blue_concrete", 20),
		new Zone("stunslam", "Stun slam", "Axe the shield, then smash", Kind.DRILL, 60, DRILL_ROW, "polished_deepslate", "deepslate_tiles", 40),
		new Zone("pearlcatch", "Pearl catch", "Pearl + wind charge for height", Kind.DRILL, 120, DRILL_ROW, "blue_concrete", "light_blue_concrete", 48)
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

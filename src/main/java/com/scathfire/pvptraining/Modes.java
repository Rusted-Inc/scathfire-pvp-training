package com.scathfire.pvptraining;

import java.util.List;

/**
 * Every fight mode, bot level and drill the menus offer, and the Carpet PvP names they run.
 * Shared by the server (which runs the commands) and the client (which draws the menus).
 */
public final class Modes {
	private Modes() {}

	/** A fight mode. {@code engine} is the Carpet PvP auto-setup mode, or null while it is coming soon. */
	public record Mode(String id, String name, String skills, String tag, String engine) {
		public boolean ready() {
			return engine != null;
		}
	}

	/** A bot level. {@code engine} is the Carpet PvP difficulty it runs. */
	public record Level(String id, String name, String note, String reaction, String aim, String moves, String engine) {}

	/** A drill. {@code engine} is the Carpet PvP drill name. */
	public record Drill(String id, String name, String about, String engine) {}

	public static final List<Mode> MODES = List.of(
		new Mode("sword", "Sword", "Crits, W-tap, strafing, hit select", "best first mode", "sword"),
		new Mode("crystal", "Crystal", "Hit crystal, anchors, totems", "hardest", "crystal"),
		new Mode("mace", "Mace", "Smash timing, breach swap, stun slam", "trending", "mace"),
		new Mode("smp", "SMP", "Netherite, shield, pearls, no crystals", "real fights", "smp"),
		new Mode("ranged", "Ranged", "Bow pressure, leading your shots", "aim check", "ranged"),
		new Mode("axe", "Axe + Shield", "Shield timing, disabling shields", "coming soon", null),
		new Mode("nethpot", "Neth Pot", "Pot timing, spacing, pressure", "coming soon", null),
		new Mode("uhc", "UHC", "No regen, bow, rod, buckets", "coming soon", null),
		new Mode("cart", "Cart", "Insta-cart, crossbow cart, overload", "coming soon", null),
		new Mode("spear", "Spear", "Jab spacing, charge, Lunge", "coming soon", null)
	);

	public static final List<Level> LEVELS = List.of(
		new Level("beginner", "Beginner", "learning", "Slow", "Misses a lot", "Basic hits only", "beginner"),
		new Level("normal", "Normal", "warm-up", "Medium", "Decent", "Main moves of the mode", "casual"),
		new Level("hard", "Hard", "real fight", "Fast", "Accurate", "Advanced moves", "average"),
		new Level("expert", "Expert", "tier test", "Very fast", "Very accurate", "Pro combos, mind games", "skilled"),
		new Level("nightmare", "Nightmare", "good luck", "Human limit", "Near perfect", "Everything it knows", "expert")
	);

	public static final List<Drill> DRILLS = List.of(
		new Drill("aim", "Aim", "Keep your crosshair on a strafing target.", "aim"),
		new Drill("crystaltiming", "Crystal timing", "Place and break crystals as fast as you can.", "crystaltiming"),
		new Drill("retotem", "Re-totem", "Put a new totem in your offhand after every pop.", "retotem"),
		new Drill("stunslam", "Stun slam", "Axe the shield, then land the mace smash.", "stunslam"),
		new Drill("pearlcatch", "Pearl catch", "Throw a pearl and catch it with a wind charge.", "pearlcatch")
	);

	public static Mode mode(String id) {
		for (Mode m : MODES) if (m.id().equals(id)) return m;
		return null;
	}

	public static Level level(String id) {
		for (Level l : LEVELS) if (l.id().equals(id)) return l;
		return null;
	}

	public static Drill drill(String id) {
		for (Drill d : DRILLS) if (d.id().equals(id)) return d;
		return null;
	}
}

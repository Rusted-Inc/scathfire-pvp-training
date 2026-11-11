package com.scathfire.pvptraining;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Wins, losses and fight numbers, saved to config/scathfirepvp-stats.json.
 * The server side writes it; the menus read the same file in singleplayer.
 */
public final class Stats {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final class ModeStats {
		public int wins;
		public int losses;
		public int hits;
		public int hitsTaken;
		public double damageDealt;
		public double damageTaken;
		public int blocked;
	}

	/** One fight session, from the start of a fight until it is stopped or a new one starts. */
	public static final class Session {
		public String mode = "";
		public String level = "";
		public int wins;
		public int losses;
		public int hits;
		public int hitsTaken;
		public double damageDealt;
		public double damageTaken;
		public int blocked;
		public long startedAt;
		public long endedAt;
	}

	public static final class Data {
		public Map<String, ModeStats> modes = new LinkedHashMap<>();
		public Session last = new Session();
		public Map<String, Integer> highestBeaten = new LinkedHashMap<>();

		public int totalWins() {
			int n = 0;
			for (ModeStats s : modes.values()) n += s.wins;
			return n;
		}

		public int totalLosses() {
			int n = 0;
			for (ModeStats s : modes.values()) n += s.losses;
			return n;
		}

		/** LT5 to HT1, from the hardest bot level beaten in any mode. */
		public String rank() {
			int best = -1;
			for (int v : highestBeaten.values()) best = Math.max(best, v);
			return switch (best) {
				case 0 -> "HT5";
				case 1 -> "LT4";
				case 2 -> "HT3";
				case 3 -> "LT2";
				case 4 -> "HT1";
				default -> "LT5";
			};
		}
	}

	private Stats() {}

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("scathfirepvp-stats.json");
	}

	public static synchronized Data load() {
		Path f = file();
		if (Files.isRegularFile(f)) {
			try {
				Data d = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class);
				if (d != null) {
					if (d.modes == null) d.modes = new LinkedHashMap<>();
					if (d.last == null) d.last = new Session();
					if (d.highestBeaten == null) d.highestBeaten = new LinkedHashMap<>();
					return d;
				}
			} catch (Exception e) {
				ScathfirePvp.LOGGER.warn("Could not read {}, starting fresh", f, e);
			}
		}
		return new Data();
	}

	public static synchronized void save(Data data) {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(data), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ScathfirePvp.LOGGER.warn("Could not save stats", e);
		}
	}
}

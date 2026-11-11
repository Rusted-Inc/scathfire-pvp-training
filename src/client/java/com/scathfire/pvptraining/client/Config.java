package com.scathfire.pvptraining.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/** Client settings, saved to config/scathfirepvp.json. */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Config instance;

	/** The save folder of the PvP World, once it has been made. */
	public String worldFolder = "";
	/** Play the intro animation when the game starts. */
	public boolean intro = true;
	/** Optional OpenRouter key for smarter coach tips. */
	public String openRouterKey = "";
	/** The OpenRouter model the coach asks. Free models end in :free. */
	public String coachModel = "meta-llama/llama-3.3-70b-instruct:free";
	/** Best Aim Trainer hits for each speed. */
	public java.util.Map<String, Integer> aimBest = new java.util.HashMap<>();

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("scathfirepvp.json");
	}

	public static Config get() {
		if (instance == null) {
			instance = new Config();
			try {
				if (Files.isRegularFile(file())) {
					Config read = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), Config.class);
					if (read != null) instance = read;
				}
			} catch (Exception ignored) {
				// a broken file just means default settings
			}
		}
		return instance;
	}

	public void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
			// settings stay for this session
		}
	}
}

package com.scathfire.pvptraining.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.scathfire.pvptraining.Modes;
import com.scathfire.pvptraining.Stats;

/** Tips after a fight: built-in ones that always work, and optional AI ones through OpenRouter. */
public final class Coach {
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	private Coach() {}

	public static String summary(Stats.Session s) {
		Modes.Mode mode = Modes.mode(s.mode);
		Modes.Level level = Modes.level(s.level);
		return String.format(Locale.ROOT, "%s vs %s bot: %d wins, %d losses. Hits landed %d, hits taken %d, blocked %d. Damage dealt %.1f, damage taken %.1f.",
			mode == null ? "No mode" : mode.name(), level == null ? "?" : level.name(), s.wins, s.losses, s.hits, s.hitsTaken, s.blocked, s.damageDealt, s.damageTaken);
	}

	/** Three tips worked out from the numbers, no internet needed. */
	public static List<String> offlineTips(Stats.Session s) {
		List<String> tips = new ArrayList<>();
		int exchanges = s.hits + s.hitsTaken;
		if (exchanges == 0) {
			tips.add("Fight a bot first, then come back here for tips on that fight.");
			tips.add("New to PvP? Start with Sword on Beginner and learn crit timing.");
			tips.add("Try the Aim drill to warm up your tracking.");
			return tips;
		}
		double trade = exchanges == 0 ? 0 : (double) s.hits / exchanges;
		if (trade < 0.45) {
			tips.add("You're losing trades: you took more hits than you landed. Hit select: wait for the bot to swing, then hit right after.");
		} else {
			tips.add("Good trading: you landed most exchanges. Now W-tap after each hit to keep the combo going.");
		}
		if (s.damageTaken > s.damageDealt * 1.3) {
			tips.add("You took a lot more damage than you dealt. Strafe with A and D so the bot misses, and back off to heal.");
		} else {
			tips.add("Your damage is ahead. Push for crits: jump and hit while falling for extra damage.");
		}
		if ("crystal".equals(s.mode)) {
			tips.add("For crystal: keep obsidian, crystals and totems in the same hotbar slots every fight, and run the Crystal timing drill.");
		} else if ("mace".equals(s.mode)) {
			tips.add("For mace: axe the shield first, then smash. The Stun slam drill trains exactly that.");
		} else if (s.blocked == 0 && ("smp".equals(s.mode) || "sword".equals(s.mode))) {
			tips.add("You never blocked a hit. In SMP, raise your shield when the bot charges in.");
		} else if (s.losses > s.wins) {
			tips.add("Drop one level and win a few in a row, then step back up.");
		} else {
			tips.add("You're winning here. Try the next level up.");
		}
		return tips;
	}

	public static boolean hasKey() {
		return !Config.get().openRouterKey.isBlank();
	}

	/** Asks the AI for three tips about the fight. Finishes with the tips, or an error message as one line. */
	public static CompletableFuture<List<String>> aiTips(Stats.Session s) {
		Config cfg = Config.get();
		JsonObject body = new JsonObject();
		body.addProperty("model", cfg.coachModel);
		JsonArray messages = new JsonArray();
		JsonObject system = new JsonObject();
		system.addProperty("role", "system");
		system.addProperty("content", "You are a friendly Minecraft Java PvP coach for 1.9+ combat. Give exactly 3 short, specific tips, one per line, no numbering, no intro. Use simple words.");
		messages.add(system);
		JsonObject user = new JsonObject();
		user.addProperty("role", "user");
		user.addProperty("content", "My last practice session against an AI bot: " + summary(s) + " What should I work on?");
		messages.add(user);
		body.add("messages", messages);

		HttpRequest request = HttpRequest.newBuilder(URI.create("https://openrouter.ai/api/v1/chat/completions"))
			.timeout(Duration.ofSeconds(40))
			.header("Authorization", "Bearer " + cfg.openRouterKey.trim())
			.header("Content-Type", "application/json")
			.header("X-Title", "Scathfire's PvP Training")
			.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
			.build();

		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
			List<String> tips = new ArrayList<>();
			if (response.statusCode() != 200) {
				tips.add("The AI coach answered with an error (" + response.statusCode() + "). Check your key and model in Coach settings.");
				return tips;
			}
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			String content = json.getAsJsonArray("choices").get(0).getAsJsonObject()
				.getAsJsonObject("message").get("content").getAsString();
			for (String line : content.split("\n")) {
				String clean = line.replaceAll("^[\\s\\-*\\d.)]+", "").trim();
				if (!clean.isEmpty()) tips.add(clean);
				if (tips.size() == 3) break;
			}
			if (tips.isEmpty()) tips.add("The AI coach didn't send any tips. Try again.");
			return tips;
		}).exceptionally(error -> List.of("Couldn't reach the AI coach. Are you online?"));
	}
}

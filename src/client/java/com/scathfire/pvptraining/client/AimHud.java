package com.scathfire.pvptraining.client;

import java.util.Locale;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import com.scathfire.pvptraining.client.ui.Theme;

/** The Aim Trainer score box at the top of the screen while a round runs. */
public final class AimHud {
	private static boolean active;
	private static String speed = "";
	private static long startedAt;
	private static long durationMs;
	private static int hits;
	private static int swings;
	private static boolean attackWasDown;
	private static long lastHitAt;

	private AimHud() {}

	public static void start(String speedId, int seconds) {
		active = true;
		speed = speedId;
		startedAt = Util.getMillis();
		durationMs = seconds * 1000L;
		hits = 0;
		swings = 0;
	}

	public static void hit(int total) {
		hits = total;
		lastHitAt = Util.getMillis();
	}

	public static void cancel() {
		active = false;
	}

	/** Ends the round and returns {hits, swings}. */
	public static int[] end(int serverHits) {
		active = false;
		return new int[] {serverHits, Math.max(swings, serverHits)};
	}

	public static String speed() {
		return speed;
	}

	/** Counts each press of the attack key while a round runs, hit or miss. */
	public static void clientTick(Minecraft mc) {
		if (!active || mc.player == null) {
			attackWasDown = false;
			return;
		}
		boolean down = mc.options.keyAttack.isDown();
		if (down && !attackWasDown && mc.gui.screen() == null) swings++;
		attackWasDown = down;
	}

	public static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!active) return;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int w = 190;
		int h = 38;
		int x = g.guiWidth() / 2 - w / 2;
		int y = 6;
		long left = Math.max(0, durationMs - (Util.getMillis() - startedAt));
		boolean flash = Util.getMillis() - lastHitAt < 150;

		Theme.box(g, x, y, w, h, 0xE00F1D36, flash ? Theme.PEN : Theme.LINE);
		Theme.text(g, font, "AIM TRAINER / " + speed.toUpperCase(Locale.ROOT), x + 6, y + 5, 0.75f, Theme.FIRE, false);
		String time = String.format(Locale.ROOT, "%d.%ds", left / 1000, (left % 1000) / 100);
		Theme.text(g, font, time, x + w - 6 - font.width(time), y + 5, 1f, left < 10000 ? Theme.MARGIN : Theme.INK, false);

		int acc = swings == 0 ? 0 : Math.round(100f * hits / Math.max(swings, hits));
		String[][] cells = {{"Hits", String.valueOf(hits)}, {"Swings", String.valueOf(Math.max(swings, hits))}, {"Accuracy", acc + "%"}};
		int cw = (w - 12) / 3;
		for (int i = 0; i < cells.length; i++) {
			int cx = x + 6 + i * cw;
			Theme.text(g, font, cells[i][1], cx, y + 16, 1.3f, Theme.INK, false);
			Theme.text(g, font, cells[i][0], cx, y + 29, 0.6f, Theme.MUTED, false);
		}
		g.fill(x + 1, y + h - 2, x + 1 + (int) ((w - 2) * (left / (float) Math.max(1, durationMs))), y + h - 1, Theme.PEN);
	}
}

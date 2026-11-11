package com.scathfire.pvptraining.client.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The look from the Fieldwork site: navy grid paper, a red margin line, pen blue and fire orange. */
public final class Theme {
	public static final int PAPER = 0xFF0F1D36;
	public static final int CARD = 0xFF15274A;
	public static final int LINE = 0xFF263A5C;
	public static final int DASH = 0xFF3A5480;
	public static final int INK = 0xFFEAF0FB;
	public static final int SOFT = 0xFFC9D5EA;
	public static final int MUTED = 0xFF9FB0CC;
	public static final int PEN = 0xFF8FB4FF;
	public static final int PEN_HOVER = 0xFFB3CCFF;
	public static final int MARGIN = 0xFFFF7A7F;
	public static final int FIRE = 0xFFF3A034;
	public static final int FIRE_HOVER = 0xFFFFB75C;
	public static final int NAVY_TEXT = 0xFF0F1D36;
	private static final int GRID = 0x1296BEFF;
	private static final int GRID_STRONG = 0x2196BEFF;

	private Theme() {}

	/** The grid-paper background with the red margin line. */
	public static void paper(GuiGraphicsExtractor g, int width, int height) {
		g.fill(0, 0, width, height, PAPER);
		for (int x = 0; x < width; x += 12) g.fill(x, 0, x + 1, height, x % 60 == 0 ? GRID_STRONG : GRID);
		for (int y = 0; y < height; y += 12) g.fill(0, y, width, y + 1, y % 60 == 0 ? GRID_STRONG : GRID);
		int margin = Math.max(24, width / 14);
		g.fill(margin, 0, margin + 1, height, withAlpha(MARGIN, 0.5f));
	}

	public static int marginX(int width) {
		return Math.max(24, width / 14);
	}

	public static int withAlpha(int argb, float alpha) {
		int a = (int) (((argb >>> 24) & 0xFF) * clamp(alpha));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public static float clamp(float v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	/** Progress from 0 to 1 of an animation that starts {@code delay} ms in and lasts {@code duration} ms. */
	public static float progress(long elapsed, long delay, long duration) {
		return clamp((elapsed - delay) / (float) duration);
	}

	public static float easeOut(float t) {
		float u = 1 - t;
		return 1 - u * u * u;
	}

	/** The website's button pop: rises 44px, overshoots a little, settles. Returns {offsetY, scale, alpha}. */
	public static float[] pop(float t) {
		if (t <= 0) return new float[] {22, 0.7f, 0};
		if (t >= 1) return new float[] {0, 1, 1};
		if (t < 0.65f) {
			float k = easeOut(t / 0.65f);
			return new float[] {22 + (-3 - 22) * k, 0.7f + (1.04f - 0.7f) * k, Math.min(1, k * 1.4f)};
		}
		float k = easeOut((t - 0.65f) / 0.35f);
		return new float[] {-3 + 3 * k, 1.04f + (1 - 1.04f) * k, 1};
	}

	/** Text drawn at a scale, from its top-left corner. */
	public static void text(GuiGraphicsExtractor g, Font font, String s, float x, float y, float scale, int color, boolean shadow) {
		if (((color >>> 24) & 0xFF) < 6) return;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, s, 0, 0, color, shadow);
		g.pose().popMatrix();
	}

	public static void centered(GuiGraphicsExtractor g, Font font, String s, float cx, float y, float scale, int color, boolean shadow) {
		text(g, font, s, cx - font.width(s) * scale / 2f, y, scale, color, shadow);
	}

	/**
	 * The website title: each letter rises and fades in, one after another.
	 * Returns the width drawn.
	 */
	public static float rise(GuiGraphicsExtractor g, Font font, String s, float x, float y, float scale, int color, long elapsed, long delay) {
		float cx = x;
		for (int i = 0; i < s.length(); i++) {
			String ch = String.valueOf(s.charAt(i));
			float t = progress(elapsed, delay + i * 55L, 720);
			float k = easeOut(t);
			float dy = (1 - k) * 9 * scale - (t > 0.6f && t < 1 ? (float) Math.sin((t - 0.6f) / 0.4f * Math.PI) * 0.6f * scale : 0);
			text(g, font, ch, cx, y + dy, scale, withAlpha(color, Math.min(1, t * 1.6f)), true);
			cx += font.width(ch) * scale;
		}
		return cx - x;
	}

	public static float riseCentered(GuiGraphicsExtractor g, Font font, String s, float cx, float y, float scale, int color, long elapsed, long delay) {
		return rise(g, font, s, cx - font.width(s) * scale / 2f, y, scale, color, elapsed, delay);
	}

	/** Text that fades up 6px into place. */
	public static void fadeUp(GuiGraphicsExtractor g, Font font, String s, float x, float y, float scale, int color, long elapsed, long delay) {
		float t = easeOut(progress(elapsed, delay, 500));
		text(g, font, s, x, y + (1 - t) * 6, scale, withAlpha(color, t), false);
	}

	/** A box with a 1px border. */
	public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
		g.fill(x, y, x + w, y + h, border);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
	}

	/** Splits text into lines that fit {@code maxWidth} at the given scale. */
	public static List<String> wrap(Font font, String text, int maxWidth, float scale) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String test = line.isEmpty() ? word : line + " " + word;
			if (font.width(test) * scale > maxWidth && !line.isEmpty()) {
				lines.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(test);
			}
		}
		if (!line.isEmpty()) lines.add(line.toString());
		return lines;
	}
}

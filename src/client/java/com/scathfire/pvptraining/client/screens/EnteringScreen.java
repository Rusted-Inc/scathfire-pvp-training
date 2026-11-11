package com.scathfire.pvptraining.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import com.scathfire.pvptraining.client.WorldLauncher;
import com.scathfire.pvptraining.client.ui.BaseScreen;
import com.scathfire.pvptraining.client.ui.Theme;

/** Shown for a moment after clicking PvP World, then the world opens (or is made the first time). */
public class EnteringScreen extends BaseScreen {
	private boolean launched;

	public EnteringScreen(Screen parent) {
		super("Entering PvP World", parent);
	}

	@Override
	public void tick() {
		if (!launched && elapsed() > 1300) {
			launched = true;
			WorldLauncher.launch(Minecraft.getInstance(), parent);
		}
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		float cx = width / 2f;
		float cy = height / 2f;
		Theme.riseCentered(g, font, "Entering PvP World", cx, cy - 26, 2.6f, Theme.INK, elapsed(), 80);
		for (int i = 0; i < 3; i++) {
			double phase = (elapsed() / 1000.0 + i * 0.15) % 1.0;
			float bounce = phase < 0.4 ? (float) Math.sin(phase / 0.4 * Math.PI) : 0;
			int x = (int) cx - 16 + i * 12;
			int y = (int) (cy + 6 - bounce * 5);
			g.fill(x, y, x + 6, y + 6, Theme.withAlpha(i == 2 ? Theme.FIRE : Theme.PEN, 0.35f + bounce * 0.65f));
		}
		String note = "Opening your arenas (the first time takes a little longer)";
		Theme.fadeUp(g, font, note, cx - font.width(note) * 0.8f / 2f, cy + 24, 0.8f, Theme.MUTED, elapsed(), 500);
	}
}

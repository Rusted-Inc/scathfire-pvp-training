package com.scathfire.pvptraining.client.screens;

import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import com.scathfire.pvptraining.client.ui.BaseScreen;
import com.scathfire.pvptraining.client.ui.SButton;
import com.scathfire.pvptraining.client.ui.Theme;

/** Shown when an Aim Trainer round ends. */
public class AimResultsScreen extends BaseScreen {
	private final String speed;
	private final int hits;
	private final int swings;
	private final int best;
	private final boolean newBest;

	public AimResultsScreen(Screen parent, String speed, int hits, int swings, int best, boolean newBest) {
		super("Aim results", parent);
		this.speed = speed;
		this.hits = hits;
		this.swings = swings;
		this.best = best;
		this.newBest = newBest;
	}

	@Override
	protected void init() {
		super.init();
		int by = height - 28;
		addRenderableWidget(new SButton(left(), by, 70, 20, "Close", SButton.Style.NORMAL, 900, this::onClose));
		addRenderableWidget(new SButton(right() - 214, by, 100, 20, "Other speed", SButton.Style.NORMAL, 950,
			() -> go(new AimScreen(null))));
		addRenderableWidget(new SButton(right() - 106, by, 106, 20, "Go again", SButton.Style.HERO, 1000,
			() -> runAndClose("spt aim " + speed)));
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		header(g, "Aim Trainer / " + speed, newBest ? "New personal best!" : "Time's up");
		float cx = width / 2f;
		int y = contentTop() + 6;
		Theme.riseCentered(g, font, hits + " hits", cx, y, 4f, newBest ? Theme.FIRE : Theme.INK, elapsed(), 350);

		int acc = swings == 0 ? 0 : Math.round(100f * hits / swings);
		String[][] cells = {{"Swings", String.valueOf(swings)}, {"Accuracy", acc + "%"}, {"Best", best + " hits"}};
		int x = left();
		int colW = (right() - x) / cells.length;
		y += 48;
		for (int i = 0; i < cells.length; i++) {
			float t = Theme.easeOut(Theme.progress(elapsed(), 650 + i * 100L, 450));
			float col = x + i * colW + colW / 2f;
			Theme.centered(g, font, cells[i][1], col, y + (1 - t) * 6, 1.6f, Theme.withAlpha(Theme.INK, t), false);
			Theme.centered(g, font, cells[i][0], col, y + 16 + (1 - t) * 6, 0.75f, Theme.withAlpha(Theme.MUTED, t), false);
		}
		String tip = acc < 50 ? "Tip: slow your mouse down a little and lead the bot in the way it's strafing."
			: acc < 80 ? "Tip: nice tracking. Try to hit right as the bot lands from a jump."
			: "Tip: great accuracy. Try the next speed up.";
		float t = Theme.easeOut(Theme.progress(elapsed(), 1100, 500));
		String line = tip.toLowerCase(Locale.ROOT).isEmpty() ? "" : tip;
		Theme.centered(g, font, line, cx, y + 40, 1f, Theme.withAlpha(Theme.SOFT, t), false);
	}
}

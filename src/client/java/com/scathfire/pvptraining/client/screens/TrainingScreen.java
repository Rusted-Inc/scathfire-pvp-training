package com.scathfire.pvptraining.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import com.scathfire.pvptraining.Stats;
import com.scathfire.pvptraining.client.ui.BaseScreen;
import com.scathfire.pvptraining.client.ui.SButton;
import com.scathfire.pvptraining.client.ui.Theme;

/** Start PvP Training: Fight a Bot, Drills or Free Practice. */
public class TrainingScreen extends BaseScreen {
	private Stats.Data stats;

	public TrainingScreen(Screen parent) {
		super("Start PvP Training", parent);
	}

	@Override
	protected void init() {
		super.init();
		stats = Stats.load();
		int x = left();
		int w = right() - x;
		int gap = 12;
		int cardW = (w - gap * 2) / 3;
		int cardH = Math.min(118, height - contentTop() - 60);
		int y = contentTop() + 16;

		SButton bot = new SButton(x, y, cardW, cardH, "Fight a Bot", SButton.Style.NORMAL, 300,
			() -> go(new ModesScreen(this, false))).card("1v1 against an AI bot. Pick a mode and a level, then fight round after round.", "most popular");
		SButton drills = new SButton(x + cardW + gap, y, cardW, cardH, "Drills", SButton.Style.NORMAL, 390,
			() -> go(new DrillsScreen(this))).card("No fighting. Train aim, crystal speed, re-totems, stun slams and pearl catches.", "beat your best");
		SButton free = new SButton(x + (cardW + gap) * 2, y, cardW, cardH, "Free Practice", SButton.Style.NORMAL, 480,
			() -> go(new ModesScreen(this, true))).card("Get a mode's kit and a dummy that takes hits but never fights back. No score.", "just vibes");
		addRenderableWidget(bot);
		addRenderableWidget(drills);
		addRenderableWidget(free);

		int by = height - 28;
		addRenderableWidget(new SButton(x, by, 70, 20, "Close", SButton.Style.NORMAL, 600, this::onClose));
		addRenderableWidget(new SButton(x + 76, by, 80, 20, "Go to hub", SButton.Style.NORMAL, 620,
			() -> runAndClose("spt go hub")));
		addRenderableWidget(new SButton(right() - 190, by, 90, 20, "Stop", SButton.Style.NORMAL, 650,
			() -> runAndClose("spt stop")));
		addRenderableWidget(new SButton(right() - 92, by, 92, 20, "Coach tips", SButton.Style.HERO, 700,
			() -> go(new CoachScreen(this))));
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		String name = Minecraft.getInstance().getUser().getName();
		header(g, "welcome back, " + name, "Start PvP Training");

		int cw = 120;
		int cx = right() - cw;
		int cy = Math.max(10, height / 16);
		float a = Theme.easeOut(Theme.progress(elapsed(), 500, 500));
		Theme.box(g, cx, cy, cw, 30, Theme.withAlpha(Theme.CARD, a), Theme.withAlpha(Theme.LINE, a));
		g.fill(cx + 5, cy + 5, cx + 25, cy + 25, Theme.withAlpha(Theme.FIRE, a));
		Theme.centered(g, font, name.isEmpty() ? "S" : name.substring(0, 1).toUpperCase(), cx + 15, cy + 11, 1f, Theme.withAlpha(0xFF2A1400, a), false);
		Theme.text(g, font, name, cx + 31, cy + 6, 1f, Theme.withAlpha(Theme.INK, a), false);
		String line = "Rank " + stats.rank() + " / " + stats.totalWins() + " wins";
		Theme.text(g, font, line, cx + 31, cy + 18, 0.75f, Theme.withAlpha(Theme.MUTED, a), false);
	}
}

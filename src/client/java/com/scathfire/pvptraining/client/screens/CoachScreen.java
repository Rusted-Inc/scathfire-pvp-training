package com.scathfire.pvptraining.client.screens;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import com.scathfire.pvptraining.Stats;
import com.scathfire.pvptraining.client.Coach;
import com.scathfire.pvptraining.client.ui.BaseScreen;
import com.scathfire.pvptraining.client.ui.SButton;
import com.scathfire.pvptraining.client.ui.Theme;

/** Your last fight in numbers, with three tips. */
public class CoachScreen extends BaseScreen {
	private Stats.Session session;
	private List<String> tips;
	private String source = "Built-in coach";
	private boolean asking;
	private long tipsShownAt;

	public CoachScreen(Screen parent) {
		super("Coach", parent);
	}

	@Override
	protected void init() {
		super.init();
		if (session == null) {
			session = Stats.load().last;
			tips = Coach.offlineTips(session);
			tipsShownAt = 0;
		}
		int x = left();
		int by = height - 28;
		addRenderableWidget(new SButton(x, by, 70, 20, "Back", SButton.Style.NORMAL, 600, this::back));
		addRenderableWidget(new SButton(right() - 202, by, 90, 20, "Settings", SButton.Style.NORMAL, 650,
			() -> go(new CoachSettingsScreen(this))));
		addRenderableWidget(new SButton(right() - 104, by, 104, 20, "Ask AI coach", SButton.Style.HERO, 700, this::askAi));
	}

	private void askAi() {
		if (asking) return;
		if (!Coach.hasKey()) {
			tips = List.of("The AI coach needs a free OpenRouter key. Open Settings to add one. The built-in tips above still work without it.");
			source = "AI coach";
			tipsShownAt = elapsed();
			return;
		}
		asking = true;
		source = "AI coach is thinking...";
		Coach.aiTips(session).thenAccept(result -> Minecraft.getInstance().execute(() -> {
			tips = result;
			source = "AI coach";
			asking = false;
			tipsShownAt = elapsed();
		}));
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		header(g, "your last session", "Coach tips");
		int x = left();
		int w = right() - x;
		int y = contentTop() + 8;

		float a = Theme.easeOut(Theme.progress(elapsed(), 300, 500));
		Theme.box(g, x, y, w, 34, Theme.withAlpha(Theme.CARD, a), Theme.withAlpha(Theme.LINE, a));
		int ly = y + 6;
		for (String line : Theme.wrap(font, Coach.summary(session), w - 16, 0.9f)) {
			Theme.text(g, font, line, x + 8, ly, 0.9f, Theme.withAlpha(Theme.SOFT, a), false);
			ly += 10;
		}

		y += 44;
		Theme.fadeUp(g, font, source, x, y, 1f, Theme.FIRE, elapsed(), 450);
		y += 14;
		for (int i = 0; i < tips.size(); i++) {
			float t = Theme.easeOut(Theme.progress(elapsed(), tipsShownAt + 550 + i * 140L, 500));
			int tx = x + 14;
			g.fill(x + 2, y + 3, x + 8, y + 9, Theme.withAlpha(Theme.PEN, t));
			for (String line : Theme.wrap(font, tips.get(i), w - 20, 1f)) {
				Theme.text(g, font, line, tx, y + (1 - t) * 6, 1f, Theme.withAlpha(Theme.INK, t), false);
				y += 11;
			}
			y += 6;
		}
	}
}

package com.scathfire.pvptraining.client.screens;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import com.scathfire.pvptraining.Modes;
import com.scathfire.pvptraining.Stats;
import com.scathfire.pvptraining.client.Coach;
import com.scathfire.pvptraining.client.ui.BaseScreen;
import com.scathfire.pvptraining.client.ui.SButton;
import com.scathfire.pvptraining.client.ui.Theme;

/** Opens when a best-of match ends: the score, your numbers, and what to do next. */
public class ResultsScreen extends BaseScreen {
	private Stats.Session session;
	private List<String> tips;

	public ResultsScreen(Screen parent) {
		super("Match results", parent);
	}

	@Override
	protected void init() {
		super.init();
		if (session == null) {
			session = Stats.load().last;
			tips = Coach.offlineTips(session);
		}
		int x = left();
		int by = height - 28;
		addRenderableWidget(new SButton(x, by, 70, 20, "Close", SButton.Style.NORMAL, 900, this::onClose));
		addRenderableWidget(new SButton(right() - 214, by, 100, 20, "Coach tips", SButton.Style.NORMAL, 950,
			() -> go(new CoachScreen(this))));
		addRenderableWidget(new SButton(right() - 106, by, 106, 20, "Rematch", SButton.Style.HERO, 1000,
			() -> runAndClose("spt fight " + session.mode + " " + session.level + " " + session.rounds)));
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		boolean won = "win".equals(session.result);
		Modes.Mode mode = Modes.mode(session.mode);
		Modes.Level level = Modes.level(session.level);
		String kicker = (mode == null ? "" : mode.name()) + " vs " + (level == null ? "" : level.name()) + " bot / " + Modes.roundsLabel(session.rounds);
		header(g, kicker, won ? "You won!" : "The bot won");

		float cx = width / 2f;
		int y = contentTop() + 6;
		String score = session.wins + " - " + session.losses;
		Theme.riseCentered(g, font, score, cx, y, 4f, won ? Theme.FIRE : Theme.INK, elapsed(), 350);

		y += 46;
		String[][] numbers = {
			{"Hits landed", String.valueOf(session.hits)},
			{"Hits taken", String.valueOf(session.hitsTaken)},
			{"Blocked", String.valueOf(session.blocked)},
			{"Damage dealt", String.format(Locale.ROOT, "%.1f", session.damageDealt)},
			{"Damage taken", String.format(Locale.ROOT, "%.1f", session.damageTaken)}
		};
		int x = left();
		int w = right() - x;
		int colW = w / numbers.length;
		for (int i = 0; i < numbers.length; i++) {
			float t = Theme.easeOut(Theme.progress(elapsed(), 600 + i * 90L, 450));
			float col = x + i * colW + colW / 2f;
			Theme.centered(g, font, numbers[i][1], col, y + (1 - t) * 6, 1.6f, Theme.withAlpha(Theme.INK, t), false);
			Theme.centered(g, font, numbers[i][0], col, y + 16 + (1 - t) * 6, 0.75f, Theme.withAlpha(Theme.MUTED, t), false);
		}

		y += 34;
		if (!tips.isEmpty()) {
			float t = Theme.easeOut(Theme.progress(elapsed(), 1100, 500));
			Theme.text(g, font, "Coach says:", x, y, 1f, Theme.withAlpha(Theme.FIRE, t), false);
			int ly = y + 12;
			for (String line : Theme.wrap(font, tips.get(0), w, 1f)) {
				if (ly > height - 40) break;
				Theme.text(g, font, line, x, ly, 1f, Theme.withAlpha(Theme.SOFT, t), false);
				ly += 10;
			}
		}
	}
}

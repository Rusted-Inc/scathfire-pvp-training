package com.scathfire.pvptraining.client.screens;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import com.scathfire.pvptraining.Modes;
import com.scathfire.pvptraining.client.ui.BaseScreen;
import com.scathfire.pvptraining.client.ui.SButton;
import com.scathfire.pvptraining.client.ui.Theme;

/** Pick a level, see what the bot can do, then start the fight. */
public class LevelScreen extends BaseScreen {
	private static String picked = "hard";
	private static int rounds = 3;
	private final List<SButton> roundButtons = new ArrayList<>();
	private final Modes.Mode mode;
	private final List<SButton> buttons = new ArrayList<>();
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;

	public LevelScreen(Screen parent, Modes.Mode mode) {
		super("Pick a level", parent);
		this.mode = mode == null ? Modes.MODES.get(0) : mode;
	}

	@Override
	protected void init() {
		super.init();
		buttons.clear();
		roundButtons.clear();
		int x = left();
		int top = contentTop() + 6;
		int listW = Math.min(150, (right() - x) / 3);
		int rowH = Math.min(22, (height - top - 44) / 5 - 4);
		for (int i = 0; i < Modes.LEVELS.size(); i++) {
			Modes.Level level = Modes.LEVELS.get(i);
			SButton b = new SButton(x, top + i * (rowH + 4), listW, rowH, level.name() + "  - " + level.note(),
				level.id().equals(picked) ? SButton.Style.SELECTED : SButton.Style.NORMAL, 250 + i * 80L, () -> {
					picked = level.id();
					for (int j = 0; j < buttons.size(); j++) {
						buttons.get(j).setStyle(Modes.LEVELS.get(j).id().equals(picked) ? SButton.Style.SELECTED : SButton.Style.NORMAL);
					}
				});
			buttons.add(b);
			addRenderableWidget(b);
		}
		panelX = x + listW + 14;
		panelY = top;
		panelW = right() - panelX;
		panelH = 5 * (rowH + 4) - 4;

		int pillY = panelY + panelH - 24;
		int pillX = panelX + 52;
		for (int i = 0; i < Modes.ROUNDS.length; i++) {
			int value = Modes.ROUNDS[i];
			SButton pill = new SButton(pillX + i * 62, pillY, 58, 16, Modes.roundsLabel(value),
				value == rounds ? SButton.Style.SELECTED : SButton.Style.NORMAL, 650 + i * 60L, () -> {
					rounds = value;
					for (int j = 0; j < roundButtons.size(); j++) {
						roundButtons.get(j).setStyle(Modes.ROUNDS[j] == rounds ? SButton.Style.SELECTED : SButton.Style.NORMAL);
					}
				});
			roundButtons.add(pill);
			addRenderableWidget(pill);
		}

		int by = height - 28;
		addRenderableWidget(new SButton(x, by, 70, 20, "Back", SButton.Style.NORMAL, 800, this::back));
		addRenderableWidget(new SButton(right() - 110, by, 110, 20, "Start fight", SButton.Style.FIRE, 850,
			() -> runAndClose("spt fight " + mode.id() + " " + picked + " " + rounds)));
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		header(g, "Fight a Bot / " + mode.name() + " / how brave are you?", "Pick a level");
		Modes.Level level = Modes.level(picked);
		if (level == null) return;

		float a = Theme.easeOut(Theme.progress(elapsed(), 600, 500));
		Theme.box(g, panelX, panelY, panelW, panelH, Theme.withAlpha(Theme.CARD, a), Theme.withAlpha(Theme.LINE, a));
		int px = panelX + 10;
		int py = panelY + 10;
		Theme.text(g, font, mode.name() + " vs " + level.name() + " bot", px, py, 1.5f, Theme.withAlpha(Theme.INK, a), false);
		String[][] rows = {
			{"Reaction", level.reaction()},
			{"Aim", level.aim()},
			{"Moves", level.moves()},
			{"Arena", "Built next to you, cleared after"}
		};
		int colW = (panelW - 20) / 2;
		for (int i = 0; i < rows.length; i++) {
			int rx = px + (i % 2) * colW;
			int ry = py + 24 + (i / 2) * 26;
			Theme.text(g, font, rows[i][0], rx, ry, 0.75f, Theme.withAlpha(Theme.MUTED, a), false);
			for (String line : Theme.wrap(font, rows[i][1], colW - 6, 1f)) {
				Theme.text(g, font, line, rx, ry + 9, 1f, Theme.withAlpha(Theme.INK, a), false);
				ry += 10;
			}
		}
		Theme.text(g, font, "Rounds", px, panelY + panelH - 20, 0.9f, Theme.withAlpha(Theme.MUTED, a), false);
		String tip = "Press P during the fight and choose Stop fight to end it early.";
		Theme.text(g, font, tip, px, panelY + panelH - 36, 0.75f, Theme.withAlpha(Theme.MUTED, a), false);
	}
}

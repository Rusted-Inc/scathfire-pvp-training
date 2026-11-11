package com.scathfire.pvptraining.client.screens;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import com.scathfire.pvptraining.Modes;
import com.scathfire.pvptraining.client.ui.BaseScreen;
import com.scathfire.pvptraining.client.ui.SButton;
import com.scathfire.pvptraining.client.ui.Theme;

/** Pick a mode: ten cards, five of them ready now. */
public class ModesScreen extends BaseScreen {
	private static String picked = "sword";
	/** True for Free Practice, false for Fight a Bot. */
	private final boolean practice;
	private final List<SButton> cards = new ArrayList<>();
	private final List<Modes.Mode> cardModes = new ArrayList<>();

	public ModesScreen(Screen parent, boolean practice) {
		super("Pick a mode", parent);
		this.practice = practice;
	}

	private boolean usable(Modes.Mode mode) {
		return practice ? mode.practiceReady() : mode.ready();
	}

	@Override
	protected void init() {
		super.init();
		cards.clear();
		cardModes.clear();
		int x = left();
		int w = right() - x;
		int gap = 8;
		int cols = 5;
		int cardW = (w - gap * (cols - 1)) / cols;
		int top = contentTop() + 6;
		int cardH = Math.min(66, (height - top - 40 - gap) / 2);

		for (int i = 0; i < Modes.MODES.size(); i++) {
			Modes.Mode mode = Modes.MODES.get(i);
			int cx = x + (i % cols) * (cardW + gap);
			int cy = top + (i / cols) * (cardH + gap);
			String tag = usable(mode) ? (practice ? "kit + dummy" : mode.tag()) : "coming soon";
			SButton card = new SButton(cx, cy, cardW, cardH, mode.name(), style(mode), 250 + i * 70L, () -> {
				if (usable(mode)) {
					picked = mode.id();
					refresh();
				}
			}).card(mode.skills(), tag);
			if (!usable(mode)) card.active = false;
			cards.add(card);
			cardModes.add(mode);
			addRenderableWidget(card);
		}

		int by = height - 28;
		addRenderableWidget(new SButton(x, by, 70, 20, "Back", SButton.Style.NORMAL, 900, this::back));
		if (!usable(Modes.mode(picked))) picked = "sword";
		refresh();
		if (practice) {
			addRenderableWidget(new SButton(right() - 130, by, 130, 20, "Start practice", SButton.Style.FIRE, 950,
				() -> runAndClose("spt practice " + picked)));
		} else {
			addRenderableWidget(new SButton(right() - 130, by, 130, 20, "Next: pick a level", SButton.Style.HERO, 950,
				() -> go(new LevelScreen(this, Modes.mode(picked)))));
		}
	}

	private SButton.Style style(Modes.Mode mode) {
		if (!usable(mode)) return SButton.Style.SOON;
		return mode.id().equals(picked) ? SButton.Style.SELECTED : SButton.Style.NORMAL;
	}

	private void refresh() {
		for (int i = 0; i < cards.size(); i++) cards.get(i).setStyle(style(cardModes.get(i)));
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		header(g, practice ? "Free Practice / a kit and a dummy, no score" : "Fight a Bot / pick your weapon", "Pick a mode");
		Modes.Mode mode = Modes.mode(picked);
		String sel = "Selected: " + (mode == null ? "" : mode.name());
		Theme.centered(g, font, sel, width / 2f, height - 22, 1f, Theme.PEN, false);
	}
}

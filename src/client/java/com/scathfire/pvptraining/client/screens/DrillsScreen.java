package com.scathfire.pvptraining.client.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import com.scathfire.pvptraining.Modes;
import com.scathfire.pvptraining.client.ui.BaseScreen;
import com.scathfire.pvptraining.client.ui.SButton;

/** The drills: no fight, just one skill at a time. */
public class DrillsScreen extends BaseScreen {
	public DrillsScreen(Screen parent) {
		super("Drills", parent);
	}

	@Override
	protected void init() {
		super.init();
		int x = left();
		int w = right() - x;
		int gap = 8;
		int n = Modes.DRILLS.size();
		int cardW = (w - gap * (n - 1)) / n;
		int cardH = Math.min(100, height - contentTop() - 60);
		int y = contentTop() + 12;
		for (int i = 0; i < n; i++) {
			Modes.Drill drill = Modes.DRILLS.get(i);
			addRenderableWidget(new SButton(x + i * (cardW + gap), y, cardW, cardH, drill.name(), SButton.Style.NORMAL, 250 + i * 80L,
				() -> runAndClose("spt drill " + drill.id())).card(drill.about(), "start"));
		}
		int by = height - 28;
		addRenderableWidget(new SButton(x, by, 70, 20, "Back", SButton.Style.NORMAL, 800, this::back));
		addRenderableWidget(new SButton(right() - 100, by, 100, 20, "Stop drill", SButton.Style.NORMAL, 850,
			() -> runAndClose("spt stop")));
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		header(g, "no bot to beat, just you", "Drills");
	}
}

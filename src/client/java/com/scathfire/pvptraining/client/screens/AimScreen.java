package com.scathfire.pvptraining.client.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import com.scathfire.pvptraining.client.Config;
import com.scathfire.pvptraining.client.ui.BaseScreen;
import com.scathfire.pvptraining.client.ui.SButton;

/** Pick an Aim Trainer speed. */
public class AimScreen extends BaseScreen {
	public AimScreen(Screen parent) {
		super("Aim Trainer", parent);
	}

	@Override
	protected void init() {
		super.init();
		int x = left();
		int w = right() - x;
		int gap = 12;
		int cardW = (w - gap * 2) / 3;
		int cardH = Math.min(100, height - contentTop() - 60);
		int y = contentTop() + 12;
		String[][] speeds = {
			{"easy", "Easy", "Slow strafes, rare jumps. Learn to track."},
			{"medium", "Medium", "Faster strafes, jumps and sprints like a real fight."},
			{"pro", "Pro", "Constant direction changes and jumps. Tier test aim."}
		};
		for (int i = 0; i < speeds.length; i++) {
			String id = speeds[i][0];
			Integer best = Config.get().aimBest.get(id);
			String tag = best == null ? "no best yet" : "best: " + best + " hits";
			addRenderableWidget(new SButton(x + i * (cardW + gap), y, cardW, cardH, speeds[i][1],
				i == 2 ? SButton.Style.NORMAL : SButton.Style.NORMAL, 250 + i * 90L,
				() -> runAndClose("spt aim " + id)).card(speeds[i][2], tag));
		}
		int by = height - 28;
		addRenderableWidget(new SButton(x, by, 70, 20, "Back", SButton.Style.NORMAL, 700, this::back));
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		header(g, "60 seconds / the bot moves like a player but never hits back", "Aim Trainer");
	}
}

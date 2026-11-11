package com.scathfire.pvptraining.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import com.scathfire.pvptraining.client.ScathfirePvpClient;
import com.scathfire.pvptraining.client.ui.SButton;
import com.scathfire.pvptraining.client.ui.Theme;

/**
 * Plays once when the game starts: the loading bar, then the name writes in and
 * "PvP Training" slams down, then it fades to the title screen and its Minecraft logo.
 */
public class IntroScreen extends Screen {
	private static final long LOADING = 3000;
	private static final long INTRO = 4600;
	private long start;
	private boolean done;

	public IntroScreen() {
		super(Component.literal("Scathfire's PvP Training"));
	}

	@Override
	protected void init() {
		start = Util.getMillis();
		addRenderableWidget(new SButton(width - 70, 8, 60, 16, "Skip", SButton.Style.NORMAL, 400, this::finish));
	}

	private void finish() {
		if (done) return;
		done = true;
		Minecraft.getInstance().gui.setScreen(new TitleScreen());
	}

	@Override
	public void tick() {
		if (Util.getMillis() - start > LOADING + INTRO) ScathfirePvpClient.runAfterTick(this::finish);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	@Override
	public void onClose() {
		finish();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Theme.paper(g, width, height);
		long el = Util.getMillis() - start;
		float cx = width / 2f;
		float cy = height / 2f;

		if (el < LOADING) {
			Theme.fadeUp(g, font, "Scathfire's", cx - font.width("Scathfire's") * 1.6f, cy - 48, 3.2f, Theme.FIRE, el, 100);
			Theme.riseCentered(g, font, "PvP Training", cx, cy - 18, 3.6f, Theme.INK, el, 250);

			int barW = Math.min(320, width - 80);
			int bx = (int) (cx - barW / 2f);
			int by = (int) cy + 26;
			float appear = Theme.easeOut(Theme.progress(el, 600, 400));
			Theme.box(g, bx, by, barW, 10, Theme.withAlpha(Theme.PAPER, appear), Theme.withAlpha(Theme.PEN, appear));
			float fill = Theme.easeOut(Theme.progress(el, 700, 2200));
			g.fill(bx + 2, by + 2, bx + 2 + (int) ((barW - 4) * fill), by + 8, Theme.withAlpha(Theme.PEN, appear));
			Theme.text(g, font, "Loading arenas and bots", bx, by + 16, 0.8f, Theme.withAlpha(Theme.MUTED, appear), false);
			String fun = "sharpening swords...";
			Theme.text(g, font, fun, bx + barW - font.width(fun), by + 15, 1f, Theme.withAlpha(Theme.PEN, appear), false);
		} else {
			long t = el - LOADING;
			float fade = 1 - Theme.progress(t, 3700, 600);

			float nameScale = 3.4f;
			Theme.rise(g, font, "Scathfire's", cx - font.width("Scathfire's") * nameScale / 2f, cy - 52, nameScale,
				Theme.withAlpha(Theme.FIRE, fade), t, 200);

			float slam = Theme.progress(t, 1400, 450);
			if (slam > 0) {
				float k = Theme.easeOut(slam);
				float scale = 4.2f * (1.6f - 0.6f * k);
				String s = "PvP Training";
				Theme.text(g, font, s, cx - font.width(s) * scale / 2f, cy - 14 - (scale - 4.2f) * 4, scale,
					Theme.withAlpha(Theme.INK, Math.min(1, slam * 2) * fade), true);
			}

			float line = Theme.easeOut(Theme.progress(t, 1900, 700));
			int lineW = (int) (Math.min(300, width - 80) * line);
			g.fill((int) (cx - lineW / 2f), (int) cy + 26, (int) (cx + lineW / 2f), (int) cy + 29, Theme.withAlpha(Theme.PEN, fade));

			float spark = Theme.progress(t, 1450, 1000);
			if (spark > 0 && spark < 1) {
				float sx = cx + font.width("Scathfire's") * nameScale / 2f + 6;
				float sy = cy - 54 - spark * 30;
				float a = (1 - spark) * fade;
				g.fill((int) sx, (int) sy, (int) sx + 6, (int) sy + 6, Theme.withAlpha(Theme.FIRE, a));
				g.fill((int) sx + 9, (int) sy - 8, (int) sx + 12, (int) sy - 5, Theme.withAlpha(Theme.MARGIN, a));
			}
		}
		super.extractRenderState(g, mouseX, mouseY, delta);
	}
}

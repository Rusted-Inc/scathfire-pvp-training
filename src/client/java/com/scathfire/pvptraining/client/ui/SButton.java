package com.scathfire.pvptraining.client.ui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** A button in the Scathfire style that pops up into place when its screen opens. */
public class SButton extends AbstractButton {
	public enum Style { NORMAL, HERO, FIRE, SELECTED, SOON }

	private Style style;
	private final Runnable action;
	private final long appearAt;
	private String subtitle;
	private String tag;

	public SButton(int x, int y, int width, int height, String label, Style style, long appearDelayMs, Runnable action) {
		super(x, y, width, height, Component.literal(label));
		this.style = style;
		this.action = action;
		this.appearAt = Util.getMillis() + appearDelayMs;
	}

	/** Makes this a card: a title, a wrapped line of text and a small tag at the bottom. */
	public SButton card(String subtitle, String tag) {
		this.subtitle = subtitle;
		this.tag = tag;
		return this;
	}

	public void setStyle(Style style) {
		this.style = style;
	}

	public Style style() {
		return style;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		if (Util.getMillis() < appearAt) return;
		action.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Font font = Minecraft.getInstance().font;
		float[] pop = Theme.pop(Theme.progress(Util.getMillis(), appearAt, 640));
		float alpha = pop[2];
		if (alpha <= 0.01f) return;

		boolean hover = this.isHovered() && style != Style.SOON;
		int fill;
		int border;
		int textColor;
		int subColor;
		int tagColor;
		switch (style) {
			case HERO -> { fill = hover ? Theme.PEN_HOVER : Theme.PEN; border = fill; textColor = Theme.NAVY_TEXT; subColor = Theme.NAVY_TEXT; tagColor = Theme.NAVY_TEXT; }
			case FIRE -> { fill = hover ? Theme.FIRE_HOVER : Theme.FIRE; border = fill; textColor = 0xFF2A1400; subColor = 0xFF2A1400; tagColor = 0xFF2A1400; }
			case SELECTED -> { fill = Theme.PEN; border = Theme.PEN; textColor = Theme.NAVY_TEXT; subColor = Theme.NAVY_TEXT; tagColor = Theme.NAVY_TEXT; }
			case SOON -> { fill = Theme.PAPER; border = Theme.DASH; textColor = Theme.MUTED; subColor = Theme.MUTED; tagColor = Theme.MUTED; }
			default -> { fill = Theme.CARD; border = hover ? Theme.PEN : Theme.LINE; textColor = Theme.INK; subColor = Theme.SOFT; tagColor = Theme.PEN; }
		}

		float lift = hover ? -1.5f : 0;
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();

		g.pose().pushMatrix();
		g.pose().translate(x + w / 2f, y + h / 2f + pop[0] + lift);
		g.pose().scale(pop[1], pop[1]);
		g.pose().translate(-w / 2f, -h / 2f);

		Theme.box(g, 0, 0, w, h, Theme.withAlpha(fill, alpha), Theme.withAlpha(border, alpha));
		if (style == Style.SOON) {
			for (int i = 0; i < w; i += 6) g.fill(i, 0, Math.min(i + 3, w), 1, Theme.withAlpha(Theme.PAPER, alpha));
		}

		String label = getMessage().getString();
		if (subtitle == null) {
			Theme.centered(g, font, label, w / 2f, (h - 8) / 2f, 1f, Theme.withAlpha(textColor, alpha), false);
		} else {
			int pad = 6;
			Theme.text(g, font, label, pad, pad, 1.2f, Theme.withAlpha(textColor, alpha), false);
			List<String> lines = Theme.wrap(font, subtitle, w - pad * 2, 0.8f);
			float ly = pad + 15;
			for (String line : lines) {
				if (ly > h - 20) break;
				Theme.text(g, font, line, pad, ly, 0.8f, Theme.withAlpha(subColor, alpha), false);
				ly += 9;
			}
			if (tag != null) Theme.text(g, font, tag, pad, h - pad - 8, 0.9f, Theme.withAlpha(tagColor, alpha), false);
		}
		g.pose().popMatrix();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}

package com.scathfire.pvptraining.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** Every menu of the mod: grid paper behind, a header that rises in, and a way back. */
public abstract class BaseScreen extends Screen {
	protected final Screen parent;
	protected long openedAt;

	protected BaseScreen(String title, Screen parent) {
		super(Component.literal(title));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.openedAt = Util.getMillis();
	}

	protected long elapsed() {
		return Util.getMillis() - openedAt;
	}

	protected int left() {
		return Theme.marginX(width) + 18;
	}

	protected int right() {
		return width - Math.max(18, width / 16);
	}

	/** Small kicker line in fire orange, then a big title that rises in letter by letter. */
	protected void header(GuiGraphicsExtractor g, String kicker, String title) {
		int x = left();
		int y = Math.max(10, height / 16);
		Theme.fadeUp(g, font, kicker, x, y, 1f, Theme.FIRE, elapsed(), 150);
		Theme.rise(g, font, title, x, y + 12, 2.4f, Theme.INK, elapsed(), 120);
	}

	protected int contentTop() {
		return Math.max(10, height / 16) + 12 + 30;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Theme.paper(g, width, height);
		drawContent(g, mouseX, mouseY, delta);
		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	protected abstract void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta);

	protected void go(Screen next) {
		Minecraft.getInstance().gui.setScreen(next);
	}

	protected void back() {
		go(parent);
	}

	@Override
	public void onClose() {
		back();
	}

	/** Runs a command as the player and closes the menu, so the fight can start. */
	protected void runAndClose(String command) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.connection.sendCommand(command);
		}
		mc.gui.setScreen(null);
	}
}

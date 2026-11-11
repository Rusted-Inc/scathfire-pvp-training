package com.scathfire.pvptraining.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import com.scathfire.pvptraining.ScathfirePvp;
import com.scathfire.pvptraining.client.screens.EnteringScreen;
import com.scathfire.pvptraining.client.screens.IntroScreen;
import com.scathfire.pvptraining.client.screens.ResultsScreen;
import com.scathfire.pvptraining.client.screens.TrainingScreen;
import com.scathfire.pvptraining.client.ui.SButton;

public class ScathfirePvpClient implements ClientModInitializer {
	/** Set when PvP World was clicked, so the training menu opens once the world has loaded. */
	public static boolean openMenuAfterJoin;

	private static boolean introShown;
	private static boolean introPending;
	private static int menuDelay = -1;
	private static int resultsDelay = -1;
	private static KeyMapping menuKey;
	private static Runnable afterTick;

	/** Runs at the end of this client tick, outside any screen's own tick. */
	public static void runAfterTick(Runnable task) {
		afterTick = task;
	}

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ScathfirePvp.MOD_ID, "main"));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.scathfirepvp.menu", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_P, category));

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof TitleScreen)) return;
			if (!introShown && Config.get().intro) {
				introShown = true;
				introPending = true;
			}
			introShown = true;
			Screens.getWidgets(screen).add(new SButton(width / 2 - 100, height / 4 + 24, 200, 20, "PvP World", SButton.Style.HERO, 250,
				() -> client.gui.setScreen(new EnteringScreen(screen))));
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (openMenuAfterJoin) {
				openMenuAfterJoin = false;
				menuDelay = 50;
			}
		});

		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			if (ScathfirePvp.MATCH_OVER.equals(message.getString())) {
				resultsDelay = 40;
				return false;
			}
			return true;
		});

		ClientTickEvents.END_CLIENT_TICK.register(ScathfirePvpClient::tick);
	}

	private static void tick(Minecraft client) {
		if (afterTick != null) {
			Runnable task = afterTick;
			afterTick = null;
			task.run();
		}
		if (introPending) {
			introPending = false;
			client.gui.setScreen(new IntroScreen());
		}
		if (menuDelay > 0 && --menuDelay == 0) {
			menuDelay = -1;
			if (client.player != null && client.gui.screen() == null) {
				client.gui.setScreen(new TrainingScreen(null));
			}
		}
		if (resultsDelay > 0 && --resultsDelay == 0) {
			resultsDelay = -1;
			if (client.player != null) {
				client.gui.setScreen(new ResultsScreen(null));
			}
		}
		while (menuKey.consumeClick()) {
			if (client.player != null && client.gui.screen() == null) {
				client.gui.setScreen(new TrainingScreen(null));
			}
		}
	}
}

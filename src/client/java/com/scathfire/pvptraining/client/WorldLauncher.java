package com.scathfire.pvptraining.client;

import java.nio.file.Files;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.screen.v1.Screens;

import com.scathfire.pvptraining.ScathfirePvp;

/** Opens the PvP World, making it first if it doesn't exist yet. */
public final class WorldLauncher {
	private WorldLauncher() {}

	public static void launch(Minecraft mc, Screen backTo) {
		Config cfg = Config.get();
		ScathfirePvpClient.openMenuAfterJoin = true;

		if (!cfg.worldFolder.isBlank() && Files.isDirectory(mc.getLevelSource().getBaseDir().resolve(cfg.worldFolder))) {
			mc.createWorldOpenFlows().openWorld(cfg.worldFolder, () -> mc.gui.setScreen(backTo));
			return;
		}

		CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(backTo));
		if (!(mc.gui.screen() instanceof CreateWorldScreen createScreen)) {
			ScathfirePvp.LOGGER.warn("The create world screen did not open");
			return;
		}

		WorldCreationUiState state = createScreen.getUiState();
		state.setName(ScathfirePvp.PVP_WORLD_NAME);
		state.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
		Holder<WorldPreset> flat = state.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT);
		state.setWorldType(new WorldCreationUiState.WorldTypeEntry(flat));
		state.setGenerateStructures(false);
		state.getGameRules().set(GameRules.ADVANCE_TIME, false, null);
		state.getGameRules().set(GameRules.ADVANCE_WEATHER, false, null);
		state.getGameRules().set(GameRules.SPAWN_MOBS, false, null);
		state.getGameRules().set(GameRules.RESPAWN_RADIUS, 0, null);

		cfg.worldFolder = state.getTargetFolder();
		cfg.save();

		String create = Component.translatable("selectWorld.create").getString();
		for (AbstractWidget widget : Screens.getWidgets(createScreen)) {
			if (widget instanceof Button button && create.equals(button.getMessage().getString())) {
				button.onPress(new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
				return;
			}
		}
		ScathfirePvp.LOGGER.warn("Couldn't find the Create button; the player can press it");
	}
}

package com.scathfire.pvptraining.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Performance mode for the PvP World. The world is flat and fenced in, so far chunks add nothing
 * but lag. On a laptop with built-in graphics this lowers render distance to 8, simulation
 * distance to 6 and turns off entity shadows while you're in the PvP World, then puts your own
 * settings back when you leave.
 */
public final class Performance {
	private static boolean applied;
	private static int oldRender;
	private static int oldSimulation;
	private static boolean oldShadows;

	private Performance() {}

	public static void enterPvpWorld(Minecraft mc) {
		if (applied || !Config.get().performanceMode) return;
		Options o = mc.options;
		oldRender = o.renderDistance().get();
		oldSimulation = o.simulationDistance().get();
		oldShadows = o.entityShadows().get();
		if (oldRender > 8) o.renderDistance().set(8);
		if (oldSimulation > 6) o.simulationDistance().set(6);
		o.entityShadows().set(false);
		applied = true;
	}

	public static void leave(Minecraft mc) {
		if (!applied) return;
		Options o = mc.options;
		o.renderDistance().set(oldRender);
		o.simulationDistance().set(oldSimulation);
		o.entityShadows().set(oldShadows);
		applied = false;
	}
}

package com.scathfire.pvptraining.client.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.scathfire.pvptraining.client.Config;
import com.scathfire.pvptraining.client.ui.BaseScreen;
import com.scathfire.pvptraining.client.ui.SButton;
import com.scathfire.pvptraining.client.ui.Theme;

/** Where the optional OpenRouter key and model go, plus the intro switch. */
public class CoachSettingsScreen extends BaseScreen {
	private EditBox key;
	private EditBox model;
	private SButton introToggle;
	private SButton perfToggle;

	public CoachSettingsScreen(Screen parent) {
		super("Coach settings", parent);
	}

	@Override
	protected void init() {
		super.init();
		Config cfg = Config.get();
		int x = left();
		int w = Math.min(300, right() - x);
		int y = contentTop() + 20;

		key = new EditBox(font, x, y, w, 18, Component.literal("OpenRouter key"));
		key.setMaxLength(200);
		key.setValue(cfg.openRouterKey);
		addRenderableWidget(key);

		model = new EditBox(font, x, y + 40, w, 18, Component.literal("Model"));
		model.setMaxLength(120);
		model.setValue(cfg.coachModel);
		addRenderableWidget(model);

		introToggle = new SButton(x, y + 72, 160, 20, introLabel(cfg.intro), SButton.Style.NORMAL, 300, () -> {
			cfg.intro = !cfg.intro;
			cfg.save();
			introToggle.setMessage(Component.literal(introLabel(cfg.intro)));
		});
		addRenderableWidget(introToggle);

		perfToggle = new SButton(x + 166, y + 72, 200, 20, perfLabel(cfg.performanceMode), SButton.Style.NORMAL, 350, () -> {
			cfg.performanceMode = !cfg.performanceMode;
			cfg.save();
			perfToggle.setMessage(Component.literal(perfLabel(cfg.performanceMode)));
		});
		addRenderableWidget(perfToggle);

		int by = height - 28;
		addRenderableWidget(new SButton(x, by, 70, 20, "Back", SButton.Style.NORMAL, 400, this::back));
		addRenderableWidget(new SButton(right() - 80, by, 80, 20, "Save", SButton.Style.HERO, 450, () -> {
			cfg.openRouterKey = key.getValue().trim();
			cfg.coachModel = model.getValue().trim().isEmpty() ? new Config().coachModel : model.getValue().trim();
			cfg.save();
			back();
		}));
	}

	private static String perfLabel(boolean on) {
		return "Performance mode in PvP World: " + (on ? "on" : "off");
	}

	private static String introLabel(boolean on) {
		return "Intro animation: " + (on ? "on" : "off");
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		header(g, "coach key, intro and performance", "Settings");
		int x = left();
		int y = contentTop() + 20;
		Theme.text(g, font, "OpenRouter key (free at openrouter.ai/keys). Stays on this computer.", x, y - 11, 0.8f, Theme.MUTED, false);
		Theme.text(g, font, "Model (free ones end in :free)", x, y + 29, 0.8f, Theme.MUTED, false);
	}
}

# Scathfire's PvP Training: notes for Claude Code

A Fabric mod for **Minecraft Java 26.3** (Fabric Loader 0.20.x, Fabric API 0.165+, Java 25) that turns the game into a PvP training game. Made for Scathfire. It is free, and it is not an official Minecraft product.

## How it fits together

- **Bots, fight arenas, kits and Carpet's drills come from Carpet PvP** (AndrewCTF/Carpet-PvP, Apache 2.0, mod id `carpet`). We don't compile against it. We run its commands:
  - `auto-setup <sword|smp|mace|crystal|ranged> <beginner|casual|average|skilled|expert>` and `auto-setup stop`
  - `bot drill <crystaltiming|retotem|stunslam|pearlcatch>` and `bot drill stop`
  - `bot kit give @s <kit>`, and `player <name> spawn|kill|move|look|jump|sprint`
  - Carpet's auto-setup builds its own 17×17 arena about 20 blocks in front of the player.
- **Our server commands are `/spt ...`**, in `src/main/java/.../ScathfirePvp.java`. They cover `fight`, `practice`, `drill`, `aim`, `go`, `stop` and `rebuild`. The menus and the world's command blocks call these.
- **The pre-made PvP World** is built by `WorldBuilder` using `fill`/`setblock`/`summon` commands, one area per server tick. The areas are listed in `Zones`. The hub is at 0,0. The fight row has its spawn pads at z=-60, and the drill row at z=110. Zones face north. A marker file `scathfirepvp-world-v2.txt` in the world folder means the world is already built.
- **The Aim Trainer** (`AimTrainer`) steers a Carpet fake player called `AimBot` with `/player` commands and cancels all damage to it. It sends hidden chat lines starting with `[scathfirepvp:aim]` that the client reads for its HUD (`AimHud`) and results screen.
- **Match end and results:** when a best-of match ends, the server sends the hidden line `[scathfirepvp:match-over]` and the client opens `ResultsScreen`.
- **Stats:** saved in `config/scathfirepvp-stats.json` (`Stats`). Client settings are in `config/scathfirepvp.json` (`Config`).

## The client side (`src/client`)

- Style: navy grid paper, pen blue `#8FB4FF`, fire orange `#F3A034`, red margin line. Buttons pop up the way the Fieldwork website's do (`ui/Theme`, `ui/SButton`).
- 26.3 API names: screens use `extractRenderState(GuiGraphicsExtractor ...)`, `Minecraft.gui.setScreen(...)` / `gui.screen()`, and SDL scancodes for key mappings. Colored blocks are `Blocks.CONCRETE.blue()` and similar.
- **Never change screens inside a screen's `tick()`.** Fabric's screen events crash on it. Use `ScathfirePvpClient.runAfterTick(...)`.

## Building

This workspace usually can't reach Fabric's or Mojang's servers. **GitHub Actions builds every push to `main`** and publishes a Release with the mod jar, the Carpet PvP 26.3 jar and an install zip. To check a build: `gh run list`, `gh run view <id> --log-failed`.

## Ideas for later

- Bots for Axe+Shield, Neth Pot, UHC, Cart and Spear. These need Carpet PvP styles or our own bot.
- A built-in Scathfire resource pack: low fire, clean swords, small totems.
- Better looking hub builds, and an in-world scoreboard.
- An AI Coach that reads Carpet PvP's combat traces.

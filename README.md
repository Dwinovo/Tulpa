<div align="center">

# Tulpa

### LLM-powered AI companions for Minecraft

*Summon a companion, then just **talk to it**. A real player's body — driven by a mind that runs on your machine, on your own API key.*

![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-62B47A?style=flat-square)
![Loaders](https://img.shields.io/badge/Loaders-Fabric%20%7C%20NeoForge-DE7C36?style=flat-square)
![Java](https://img.shields.io/badge/Java-25-007396?style=flat-square&logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/License-PolyForm%20NC-4B6BFB?style=flat-square)
![Status](https://img.shields.io/badge/status-WIP-A8731E?style=flat-square)

[**Features**](#features) · [**Quick start**](#quick-start) · [**Usage**](#usage) · [**How it works**](#how-it-works) · [**Tools**](#what-it-can-do) · [**Config**](#configuration) · [**Build**](#building-from-source)

</div>

<!-- Tip: drop a gameplay GIF/screenshot here once you have one — it sells the mod faster than any paragraph. -->

---

Tell your companion *"go mine me a stack of iron,"* *"build a hut where I'm standing,"* *"follow me and kill anything hostile,"* or *"smelt these and put the bars in that chest"* — a large language model plans the work and drives the companion through it: pathfinding across terrain, mining, building, fighting, crafting, and using containers, entirely on its own. Talk to it in **any language your model speaks**.

## Features

- 🗣️ **Just talk to it.** Natural-language requests become real, multi-step work in the world — no scripts, no GUIs full of toggles.
- 🧠 **The brain runs on *your* machine.** The agent loop calls the LLM from the owner's client with the owner's API key. No server-side LLM, no shared bill — each player pays only for their own companion's thinking.
- 🧍 **The body is a real player, not a custom mob.** Companions are server-side *fake players*, so they interact with the world through genuine player code — they "just work" with other mods, redstone, mob AI, and containers instead of needing per-feature adapters.
- 🧭 **Baritone-grade pathfinding, from scratch.** A self-contained A\* pathfinder reimplements Baritone's cost model and movement set 1:1 (walk, diagonal, ascend/descend, jump, parkour, pillar-up, dig-down, scaffold-bridging) — **no Baritone dependency**.
- 🛠️ **27 tools.** Mine, build, fight (native melee + bow), craft by hand-placing into the grid, use any container, scan the world, locate structures/biomes, manage inventory, and plan with a live to-do list.
- 🔌 **Bring your own model.** OpenAI, DeepSeek, Kimi/Moonshot, MiniMax, Doubao, and Qwen out of the box — any OpenAI-compatible endpoint via a custom base URL.
- 🪶 **No third-party SDKs.** LLM transport is the JDK's built-in `HttpClient` + Gson. That's it.
- 🧩 **One codebase, both loaders.** Ships for **Fabric** and **NeoForge** (MC 26.1.2, Java 25).

## Quick start

1. **Install** the mod (plus [Fabric API](https://modrinth.com/mod/fabric-api) if you're on Fabric) and launch the game once to generate the config.
2. **Add your API key.** Press **`G`** → **Settings** (or run `/tulpa settings`), pick your provider, and paste your key.
3. **Summon a companion:** `/tulpa player summon <name>`
4. **Press `G`** to open the chat and tell it what to do.

```
You:   挖一组铁矿回来 / go grab me a stack of iron
Tulpa: On it. Heading underground to find iron.
       ▸ 4 steps · locate_biome · move_to · auto_mine · collect_items   ✔
Tulpa: Got 64 raw iron — want me to smelt it?
```

## Usage

Open a companion with **`G`** (it goes straight to chat if you only have one; otherwise it shows the roster). The panel has three tabs:

| Tab | What you get |
|---|---|
| **Chat** | A scrollable transcript + a **live plan panel** (the model's current to-do list). Tool runs stream a spinner and fold into a tidy summary when finished — click to expand. |
| **Items** | A read-only "character sheet" styled like the vanilla inventory: a live mouse-following 3D portrait, armor + offhand, the 2×2 crafting grid, heart/hunger vitals, and the full backpack. |
| **Settings** | Provider, API key (masked, with a reveal toggle), model, and base URL. |

A left-edge **HUD** shows a small avatar per companion: idle ones tuck away to a thin gold sliver; when one speaks, its avatar and a speech bubble slide out together.

### Commands

| Command | Description |
|---|---|
| `/tulpa player summon <name>` | Summon (or wake) a companion |
| `/tulpa player despawn <name>` | Permanently dismiss a companion |
| `/tulpa settings` | Open the settings screen |
| `/tulpa reset` | Reset the client-side agent loops |

## How it works

```
   You (owner's client)                          Server
   ─────────────────────                         ──────────────────────────
   chat box ─▶ agent loop ──▶ LLM API
                   │  (your key, your model)
                   │  tool calls
                   ▼
             ExecuteToolPayload ───────────▶  task queue on the fake player
                   ▲                               │  per-tick dispatcher
                   │   results                     ▼
             TaskResultPayload ◀──────────   pathfinding · mining · combat ·
                                             crafting · container use …
```

1. You type a message; the **client-side agent loop** sends the conversation + tool schemas to your chosen LLM and streams the reply.
2. Read-only tools (status, scans, recipe lookups) answer instantly; **world-action tools** are shipped to the server, queued on the companion's body, and run by a per-tick task dispatcher.
3. Results flow back into the conversation, so the model sees what happened and decides the next step. It can chain many actions per request — and you can hit **Stop** anytime.
4. History is **auto-compacted** (summarized) as it approaches the context window, so long sessions keep working.

### The companion

- **Persists like a player** — state lives in a vanilla `playerdata/*.dat` (position, inventory, health, owner); companions return on login.
- **Owned by you, across dimensions** — ownership is stored and checked by UUID (vanilla `getOwner()` is level-scoped and breaks across dimensions, so Tulpa avoids it).
- **Recoverable death** — vanilla death runs normally (drops, keepInventory, grave mods all work); the agent pauses and the body respawns at the owner after a short delay.
- **Shows up as a player** in the tab list and renders with its own skin.

## What it can do

The model expresses **intent** — the companion figures out *how* (pathing, tool choice, reach) on its own.

<details>
<summary><b>All 27 tools</b> (click to expand)</summary>

**Movement & navigation**
- `move_to` — pathfind to coordinates (bridges, digs, jumps as needed)
- `locate_structure` — find the nearest structure (village, stronghold, …)
- `locate_biome` — find the nearest biome

**Mining & building**
- `auto_mine` — "gather N of these blocks" — finds, paths to, mines, repeats
- `break_block` — remove one exact block
- `place_block` — place a block at an exact spot (optional facing)

**Combat** (native player combat — real cooldowns, weapon mods, crits)
- `hunt` — melee N mobs of given types
- `shoot` — kill N mobs with a bow (charges, leads moving targets)

**Inventory & items**
- `collect_items` — pick up nearby drops
- `drop_items` — drop items (hand off to you / shed junk)
- `equip_item` — wear/wield gear
- `eat_item` — eat food to heal

**Containers & interaction**
- `interact_at` — aim at and use a block (open chests, furnaces, doors, levers…)
- `interact_entity` — follow and use a moving entity (villagers, animals…)
- `transfer` — move items between slots of an open GUI by intent
- `inspect_gui` — read the open container's slots & machine progress
- `close_gui` — close the container

**Crafting & recipes**
- `lookup_recipe` — JEI-style recipe lookup (crafting / smelting / stonecutter / smithing)
- *(crafting itself is done by hand-placing items into the grid via `transfer` + `interact_at`)*

**Perception & status**
- `get_self_status` · `get_owner_status` · `get_world_info`
- `scan_blocks` · `scan_nearby_entities` · `inspect_block`

**Planning & meta**
- `todowrite` — the model's own to-do list (shown live in the plan panel)
- `load_skill` — load a Markdown "skill" (a saved workflow) on demand
- `wait` — idle deliberately (smelting, nightfall, timers) instead of burning tokens

</details>

**Skills** are Markdown workflows in `config/tulpa/skills/<name>/SKILL.md`. They're advertised to the model as a table of contents and loaded full-text only when relevant, keeping the prompt lean.

## Configuration

Set everything in-game (**`G`** → Settings), or edit the config file directly.

| Field | Notes |
|---|---|
| `provider` | `openai` *(default)*, `deepseek`, `moonshot`/`kimi`, `minimax`, `volcengine`/`doubao`, `dashscope`/`qwen` |
| `apiKey` | your key — stored locally, per game directory |
| `model` | model id (default `gpt-5-2-mini`) |
| `baseUrl` | optional override for any OpenAI-compatible endpoint |
| `systemPrompt` | base persona prepended to the agent prompt |

Config lives in **`config/tulpa.json`** (Fabric) or **`config/tulpa-common.toml`** (NeoForge).

## Building from source

A standard Gradle **multiloader** project: shared code in `common/`, thin loader modules in `fabric/` and `neoforge/`. The `common` module reaches loader-specific functionality through a small `Services` abstraction (config, networking, platform) resolved via `ServiceLoader`.

```bash
./gradlew build                 # build both loaders
./gradlew :fabric:runClient     # run the Fabric client
./gradlew :neoforge:runClient   # run the NeoForge client
```

**Stack:** Java 25 · Minecraft 26.1.2 · Fabric Loader 0.19.2 / Fabric API 0.148.2 · NeoForge 26.1.2.50-beta · Mixin + MixinExtras · LLM transport via `java.net.http.HttpClient` + Gson (no third-party HTTP/LLM libraries). Only three small mixins are used — the design leans on the fake-player approach instead of invasive patches.

## License

Source-available, **non-commercial**.

- **Code** → [PolyForm Noncommercial 1.0.0](LICENSE) — use, modify, and redistribute for any noncommercial purpose, with attribution.
- **Art** (textures, sprites, sounds — anything under `assets/` that isn't source code) → [CC BY-NC 4.0](LICENSE-ART) — same terms.
- **Commercial use of either requires a separate license from the author.**

This is *source-available*, not OSI "open source" (that definition forbids non-commercial restrictions) — please describe it as **source-available**.

<div align="center">
<sub>Built on the <a href="https://github.com/jaredlll08/MultiLoader-Template">MultiLoader Template</a>.</sub>
</div>

# Changelog

All notable changes to Blueth Core will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.0] — 2026-03-13

### Added

#### Core modules
- **`config.ConfigManager`** — Thread-safe YAML config wrapper with versioned step-by-step
  migration (`migrate(targetVersion, BiConsumer)`), typed getters with defaults, nested path
  helpers, and synchronized `reload()`/`save()`.
- **`gui.GuiBuilder`** — Fluent single-page inventory GUI with click handlers, `border()`,
  `fill()`, `onClose()` callback, static factory `create()`, and auto-registration of
  Bukkit listeners via `open(Player, JavaPlugin)`.
- **`menu.PaginatedMenu`** — Higher-level paginated inventory menu built on `GuiBuilder`;
  handles prev/next navigation, configurable button items, and per-item click handlers.
- **`hook.VaultHook`** — Safe Vault economy integration with `deposit()`, `withdraw()`,
  `getBalance()`, `has()`, and `isAvailable()` convenience methods.
- **`hook.PlaceholderHook`** — Abstract `PlaceholderExpansion` base with `tryRegister()`,
  `tryUnregister()`, and static `isAvailable()` helpers.
- **`scheduler.TaskScheduler`** — Paper/Folia-aware scheduler; detects Folia at runtime via
  class-loading, delegates to region/async schedulers on Folia and BukkitScheduler on Paper;
  `cancelAll()` cancels every tracked task; `runAtEntity()` / `runAtLocation()` for
  Folia region scheduling.
- **`persistence.FlatFileStore`** — Thread-safe YAML flatfile store guarded by a
  `ReadWriteLock`; `saveAsync()` via `CompletableFuture`; `loadAll()` key discovery.
- **`webhook.WebhookEmitter`** — Async Discord webhook client with `Embed` builder
  (title, description, color, fields, thumbnail, footer), automatic HTTP 429 /
  `Retry-After` handling, configurable `Executor`, and `isConfigured()` guard.
- **`text.TextUtil`** — MiniMessage helpers: `parse()`, `parse(Map)`, `format(%key%)`,
  `stripTags()`, `colorize()` (legacy `&` codes), `toLegacy()`, `serialize()`.
- **`item.ItemBuilder`** — Fluent `ItemStack` builder: `name()`, `lore()`, `enchant()`,
  `unbreakable()`, `customModelData()`, `hideFlags()`, `meta()`, `copy()`.
- **`cooldown.CooldownManager`** — UUID/key cooldown tracker with `isActive()`,
  `remainingMillis()`, `remainingSeconds()`, `clear()`, `clearAll()`, `cleanup()`.
- **`update.UpdateChecker`** — Async semver version checker against any HTTP endpoint;
  parses first `X.Y.Z` triplet from plain-text or JSON responses.

#### Supporting files
- `LICENSE` — All Rights Reserved, 2026 LaunchDay Studio
- `.editorconfig` — Consistent code style (Java 4-space indent, YAML 2-space, LF line endings)

[0.1.0]: https://github.com/LaunchDayStudio/blueth-core/releases/tag/v0.1.0

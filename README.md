# Blueth Core

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Paper](https://img.shields.io/badge/Paper-1.21%2B-blue?logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZD0iTTEyIDJMMyA3bDkgNSA5LTV6Ii8+PC9zdmc+)
![License](https://img.shields.io/badge/license-All%20Rights%20Reserved-red)
![JitPack](https://img.shields.io/badge/JitPack-0.2.0-brightgreen)

**Blueth Core** is the shared utility library powering the Blueth plugin ecosystem for
Paper 1.21+ servers. It provides production-ready building blocks — command framework,
GUI management, i18n, player data, config versioning, scheduling, persistence, webhooks,
and more — so every downstream plugin starts from a solid foundation.

---

## Why Blueth Core over EssentialsX?

| Feature | Blueth Core | EssentialsX |
|---------|-------------|-------------|
| Folia Support | ✅ Native | ❌ None |
| MiniMessage | ✅ First-class | ⚠️ Legacy ChatColor |
| GUI Framework | ✅ Built-in + pagination | ❌ None |
| Command Framework | ✅ Annotation-based | ❌ Manual registration |
| Config Migration | ✅ Versioned steps | ⚠️ Shades Configurate |
| i18n | ✅ MiniMessage + reload | ⚠️ Legacy properties |
| Player Data | ✅ Async + atomic | ⚠️ Synchronous YAML |
| Event Bus | ✅ Lightweight internal | ❌ None |
| Time/Number/Location Utils | ✅ Clean, static | ⚠️ 600+ lines of legacy |
| Jar Size | ~60KB | ~4.7MB |
| Classes | ~25 focused | 362 monolithic |

---

## Modules

### `command` — Annotation-Based Command Framework

Register commands with annotations — no plugin.yml entries needed. Automatic argument
parsing, tab completion, permission checks, cooldowns, and subcommand support.

```java
@BluethCommand(name = "kit", permission = "myplugin.kit", usage = "/kit <name>",
               cooldown = "kit", cooldownSeconds = 300)
public void onKit(Player player, @Arg("name") String kitName) {
    // give kit
}

@Subcommand(parent = "kit", name = "list")
public void onKitList(Player player) {
    // list kits
}

// Registration (in onEnable):
CommandManager mgr = new CommandManager(plugin, scheduler, cooldownManager);
mgr.register(new MyCommands());
```

---

### `i18n` — Message Manager

Full i18n system with MiniMessage, `%placeholder%` and `<tag>` support, auto-prefix,
titles, and ActionBar helpers. Reload-safe.

```java
MessageManager messages = new MessageManager(plugin, "messages.yml");
messages.setPrefix("<gray>[<gold>MyPlugin</gold>]</gray> ");

messages.sendMessage(player, "welcome", "player", player.getName());
messages.sendTitle(player, "level-up", "level", "10");
messages.sendActionBar(player, "xp-gained", "amount", "50");
```

---

### `data` — Player Data Store

Typed per-player data with async I/O, dirty-flag batch saves, and atomic file writes.

```java
PlayerDataStore store = new PlayerDataStore(plugin, "playerdata");
store.set(uuid, "kills", 42);
int kills = store.get(uuid, "kills", Integer.class);

Map<UUID, Integer> allKills = store.getAll("kills", Integer.class);
store.saveAllAsync();
```

---

### `menu` — Menu Manager

Centralized GUI lifecycle with exploit protection (drag, shift-click, number keys),
confirmation dialogs, animated slots, and click sounds.

```java
MenuManager menus = new MenuManager(plugin, scheduler);
menus.setClickSound(Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

menus.confirm(player, "Delete this kit?",
    p -> deleteKit(p),
    p -> p.sendMessage("Cancelled"));
```

---

### `event` — Internal Event Bus

Lightweight pub/sub for inter-module communication with priority ordering,
async listeners, cancellable events, and one-shot listeners.

```java
EventBus bus = new EventBus();
bus.on(ContractCompleteEvent.class, event -> { ... });
bus.once(RewardEvent.class, event -> { ... });
bus.fire(new ContractCompleteEvent(player, contract));
```

---

### `util` — Version, Time, Number, Location Utilities

**VersionUtil** — cached server version detection:
```java
VersionUtil.isAtLeast(1, 21)   // true if 1.21+
VersionUtil.isFolia()          // true if Folia
VersionUtil.isPaper()          // true if Paper
```

**TimeUtil** — human-friendly duration parsing and formatting:
```java
Duration d = TimeUtil.parse("2h30m");
String s = TimeUtil.format(d);        // "2 hours 30 minutes"
String r = TimeUtil.formatRelative(instant); // "3 hours ago"
```

**NumberUtil** — formatting, compact notation, safe parsing:
```java
NumberUtil.format(1234567.89)  // "1,234,567.89"
NumberUtil.compact(1500000)    // "1.5M"
NumberUtil.parseOr("abc", 0)  // 0
```

**LocationUtil** — serialization, safety checks, distance:
```java
String s = LocationUtil.serialize(location);      // "world,100,64,200,0.0,0.0"
Location loc = LocationUtil.deserialize(s);
boolean safe = LocationUtil.isSafe(location);
Location nearby = LocationUtil.findSafeNearby(location, 5);
```

---

### `config` — Versioned YAML Config

Thread-safe YAML wrapper with step-by-step migration support.

```java
ConfigManager cfg = new ConfigManager(plugin, "config.yml");

// Migrate from version 1 → 3
cfg.migrate(3, (config, fromVersion) -> {
    if (fromVersion == 1) config.set("new-key", config.getString("old-key"));
    if (fromVersion == 2) config.set("renamed", config.getString("old-name"));
});

String host = cfg.getString("database.host", "localhost");
int    port = cfg.getInt("database.port", 3306);
```

---

### `gui` — Inventory GUI Builder

Fluent single-page inventory GUI with click handlers, border fills, and close callbacks.

```java
GuiBuilder.create("My Menu", 3)
    .border(glassPane)
    .setItem(13, myItem, player -> player.sendMessage("Clicked!"))
    .onClose(player -> player.sendMessage("Bye!"))
    .open(player, plugin);
```

---

### `menu` — Paginated Menu

Higher-level paginated inventory built on `GuiBuilder`. Automatically handles page
navigation buttons and per-item click handlers.

```java
PaginatedMenu menu = new PaginatedMenu(plugin, Component.text("Shop"), 4)
    .prevButton(prevItem)
    .nextButton(nextItem)
    .closeButton(closeItem);

shopEntries.forEach(e -> menu.addItem(e.icon(), p -> purchase(p, e)));
menu.open(player);
```

---

### `hook` — Vault & PlaceholderAPI

**VaultHook** — safe economy integration with deposit/withdraw helpers.

```java
VaultHook vault = new VaultHook();
if (vault.setup()) {
    vault.deposit(player, 500.0);
    double bal = vault.getBalance(player);
}
```

**PlaceholderHook** — extend to register a custom PAPI expansion.

```java
public class MyExpansion extends PlaceholderHook {
    public MyExpansion(JavaPlugin plugin) { super(plugin); }

    @Override public @NotNull String getIdentifier() { return "myplugin"; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        return switch (params) {
            case "name" -> plugin.getName();
            default     -> null;
        };
    }
}

// In onEnable:
new MyExpansion(this).tryRegister();
```

---

### `scheduler` — Paper/Folia Scheduler

Folia is detected at runtime; all scheduling falls back gracefully on standard Paper.
All tasks are tracked and cancellable via `cancelAll()`.

```java
TaskScheduler scheduler = new TaskScheduler(plugin);
scheduler.runTimer(() -> tick(), 0L, 20L);     // every second
scheduler.runAsync(() -> heavyWork());          // off main thread
scheduler.runAtEntity(entity, () -> foo());     // Folia-ready
// In onDisable:
scheduler.cancelAll();
```

---

### `persistence` — Thread-Safe Flat File Store

YAML/JSON-backed key/value store with async saves, atomic writes (tmp → rename),
and thread-safe I/O.

```java
FlatFileStore store = new FlatFileStore(plugin, "playerdata");
FlatFileStore jsonStore = new FlatFileStore(plugin, "data", FlatFileStore.Backend.JSON);

YamlConfiguration data = store.load(player.getUniqueId().toString());
data.set("coins", 500);
store.saveAsync(player.getUniqueId().toString(), data);

List<String> allKeys = store.loadAll();
Map<String, YamlConfiguration> allEntries = store.loadAllEntries();
```

---

### `webhook` — Discord Webhook Emitter

Async Discord webhook client with embed support, automatic 429 rate-limit handling,
exponential backoff retry, and client-side rate limiting.

```java
WebhookEmitter emitter = new WebhookEmitter(config.getString("webhook-url", ""));
emitter.setRateLimit(30); // max 30 messages per minute

emitter.sendEmbed(WebhookEmitter.Embed.builder()
    .title("Player Joined")
    .description(player.getName() + " joined the server!")
    .color(0x00FF00)
    .footer("Blueth Network")
    .build());
```

---

### `text` — MiniMessage Utilities

Parse, format, strip, and serialize Adventure components with convenience helpers.

```java
Component title  = TextUtil.parse("<gold><bold>Welcome!</bold></gold>");
Component msg    = TextUtil.parse("<red>Hello, <player>!", Map.of("player", name));
String    plain  = TextUtil.stripTags("<red>Hello</red>"); // → "Hello"
Component legacy = TextUtil.colorize("&aGreen text");
```

---

### `item` — Fluent Item Builder

Build `ItemStack` instances fluently without verbose meta boilerplate.

```java
ItemStack sword = ItemBuilder.of(Material.DIAMOND_SWORD)
    .name(Component.text("Legendary Blade").color(NamedTextColor.AQUA))
    .lore(Component.text("A very sharp blade."))
    .enchant(Enchantment.SHARPNESS, 5)
    .unbreakable(true)
    .hideAllFlags()
    .build();
```

---

### `cooldown` — Cooldown Manager

UUID-keyed in-memory cooldown tracker with automatic expiry cleanup.

```java
CooldownManager cd = new CooldownManager();
cd.set(player.getUniqueId(), "kit", 1, TimeUnit.HOURS);

if (cd.isActive(player.getUniqueId(), "kit")) {
    long secs = cd.remainingSeconds(player.getUniqueId(), "kit");
    player.sendMessage("Wait " + secs + "s!");
}
```

---

### `update` — Update Checker

Async semver version checker against any HTTP endpoint (Modrinth, Hangar, custom).

```java
new UpdateChecker(plugin, "https://api.modrinth.com/v2/project/MY_ID/version?limit=1")
    .check()
    .thenAccept(result -> {
        if (result.updateAvailable()) {
            plugin.getLogger().info("Update available: " + result.latestVersion());
        }
    });
```

---

## Installation

### JitPack (recommended)

Add the JitPack repository and the dependency to your plugin's `build.gradle.kts`:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.LaunchDayStudio:blueth-core:0.2.0")
}
```

### Local Maven

Build and publish to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

Then in your plugin:

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    compileOnly("online.blueth:blueth-core:0.2.0-SNAPSHOT")
}
```

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21+ |
| Paper / Folia | 1.21+ |
| Vault *(optional)* | 1.7.1 |
| PlaceholderAPI *(optional)* | 2.11.6+ |

---

## License

Copyright © 2026 LaunchDay Studio. All Rights Reserved.

This software and its source code are proprietary. Unauthorized copying, modification,
distribution, or use is strictly prohibited without explicit written permission from
LaunchDay Studio.

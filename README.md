# Blueth Core

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Paper](https://img.shields.io/badge/Paper-1.21%2B-blue?logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZD0iTTEyIDJMMyA3bDkgNSA5LTV6Ii8+PC9zdmc+)
![License](https://img.shields.io/badge/license-All%20Rights%20Reserved-red)
![JitPack](https://img.shields.io/badge/JitPack-0.1.0-brightgreen)

**Blueth Core** is the shared utility library powering the Blueth plugin ecosystem for
Paper 1.21+ servers. It provides production-ready building blocks — GUI management,
config versioning, scheduling, persistence, webhooks, and more — so every downstream
plugin starts from a solid foundation.

---

## Modules

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

YAML-backed key/value store with async saves and thread-safe I/O.

```java
FlatFileStore store = new FlatFileStore(plugin, "playerdata");
YamlConfiguration data = store.load(player.getUniqueId().toString());
data.set("coins", 500);
store.saveAsync(player.getUniqueId().toString(), data);

List<String> allKeys = store.loadAll();
```

---

### `webhook` — Discord Webhook Emitter

Async Discord webhook client with embed support and automatic 429 rate-limit handling.

```java
WebhookEmitter emitter = new WebhookEmitter(config.getString("webhook-url", ""));

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
    compileOnly("com.github.LaunchDayStudio:blueth-core:0.1.0")
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
    compileOnly("online.blueth:blueth-core:0.1.0-SNAPSHOT")
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

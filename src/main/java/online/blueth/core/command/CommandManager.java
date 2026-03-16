package online.blueth.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import online.blueth.core.cooldown.CooldownManager;
import online.blueth.core.scheduler.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Annotation-based command framework with automatic argument parsing,
 * tab-completion, permission checks, cooldown integration, and subcommand support.
 *
 * <p>Commands are registered directly onto the server's {@link CommandMap},
 * so no {@code plugin.yml} command entries are needed.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * CommandManager mgr = new CommandManager(plugin, scheduler, cooldownManager);
 * mgr.setNoPermissionMessage("<red>You don't have permission!");
 *
 * mgr.register(new MyCommands());
 * // MyCommands class has methods annotated with @BluethCommand / @Subcommand
 * }</pre>
 */
public final class CommandManager {

    private final JavaPlugin plugin;
    private final TaskScheduler scheduler;
    private final @Nullable CooldownManager cooldownManager;
    private final Map<String, RegisteredCommand> commands = new ConcurrentHashMap<>();

    private Component noPermissionMessage = Component.text("You don't have permission.", NamedTextColor.RED);
    private Component playerOnlyMessage = Component.text("This command can only be used by players.", NamedTextColor.RED);
    private Component cooldownMessage = Component.text("Please wait before using this again.", NamedTextColor.RED);

    /**
     * @param plugin          the owning plugin
     * @param scheduler       the task scheduler (for async commands)
     * @param cooldownManager optional cooldown manager (may be {@code null})
     */
    public CommandManager(JavaPlugin plugin, TaskScheduler scheduler, @Nullable CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.cooldownManager = cooldownManager;
    }

    public CommandManager(JavaPlugin plugin, TaskScheduler scheduler) {
        this(plugin, scheduler, null);
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    /** Sets the message shown when a player lacks permission (MiniMessage-parsed via TextUtil). */
    public void setNoPermissionMessage(Component message) { this.noPermissionMessage = message; }

    /** Sets the message shown when console runs a player-only command. */
    public void setPlayerOnlyMessage(Component message) { this.playerOnlyMessage = message; }

    /** Sets the message shown when a player is on cooldown. */
    public void setCooldownMessage(Component message) { this.cooldownMessage = message; }

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Scans {@code handler} for methods annotated with {@link BluethCommand} and
     * {@link Subcommand}, then registers them on the server's command map.
     *
     * @param handler the object containing annotated command methods
     */
    public void register(Object handler) {
        Map<String, List<SubcommandEntry>> subcommands = new HashMap<>();

        // First pass: collect subcommands
        for (Method method : handler.getClass().getDeclaredMethods()) {
            Subcommand sub = method.getAnnotation(Subcommand.class);
            if (sub == null) continue;
            method.setAccessible(true);
            subcommands.computeIfAbsent(sub.parent(), k -> new ArrayList<>())
                    .add(new SubcommandEntry(sub, method, handler));
        }

        // Second pass: register main commands
        for (Method method : handler.getClass().getDeclaredMethods()) {
            BluethCommand cmd = method.getAnnotation(BluethCommand.class);
            if (cmd == null) continue;
            method.setAccessible(true);

            List<SubcommandEntry> subs = subcommands.getOrDefault(cmd.name(), Collections.emptyList());
            RegisteredCommand reg = new RegisteredCommand(cmd, method, handler, subs);
            commands.put(cmd.name(), reg);

            BukkitCommand bukkitCmd = createBukkitCommand(reg);
            Bukkit.getCommandMap().register(plugin.getName().toLowerCase(), bukkitCmd);
        }
    }

    // ── Command creation ──────────────────────────────────────────────────────

    private BukkitCommand createBukkitCommand(RegisteredCommand reg) {
        BluethCommand ann = reg.annotation;

        return new BukkitCommand(ann.name(), ann.description(), ann.usage(), List.of(ann.aliases())) {

            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                // Subcommand dispatch
                if (args.length > 0) {
                    for (SubcommandEntry sub : reg.subcommands) {
                        if (sub.annotation.name().equalsIgnoreCase(args[0])) {
                            executeSubcommand(sender, sub, Arrays.copyOfRange(args, 1, args.length));
                            return true;
                        }
                    }
                }

                // Main command
                executeCommand(sender, reg.annotation.permission(), reg.annotation.playerOnly(),
                        reg.annotation.async(), reg.annotation.cooldown(), reg.annotation.cooldownSeconds(),
                        reg.annotation.usage(), reg.method, reg.handler, args);
                return true;
            }

            @Override
            public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
                // Subcommand names at position 0
                if (args.length == 1 && !reg.subcommands.isEmpty()) {
                    List<String> completions = new ArrayList<>();
                    for (SubcommandEntry sub : reg.subcommands) {
                        if (sub.annotation.name().toLowerCase().startsWith(args[0].toLowerCase())) {
                            completions.add(sub.annotation.name());
                        }
                    }
                    if (!completions.isEmpty()) return completions;
                }

                // Subcommand argument completion
                if (args.length > 1) {
                    for (SubcommandEntry sub : reg.subcommands) {
                        if (sub.annotation.name().equalsIgnoreCase(args[0])) {
                            return generateCompletions(sub.method, Arrays.copyOfRange(args, 1, args.length));
                        }
                    }
                }

                return generateCompletions(reg.method, args);
            }
        };
    }

    private void executeSubcommand(CommandSender sender, SubcommandEntry sub, String[] args) {
        executeCommand(sender, sub.annotation.permission(), sub.annotation.playerOnly(),
                sub.annotation.async(), "", 0, "", sub.method, sub.handler, args);
    }

    private void executeCommand(
            CommandSender sender, String permission, boolean playerOnly, boolean async,
            String cooldown, int cooldownSeconds, String usage,
            Method method, Object handler, String[] args
    ) {
        // Permission check
        if (!permission.isEmpty() && !sender.hasPermission(permission)) {
            sender.sendMessage(noPermissionMessage);
            return;
        }

        // Player-only check
        if (playerOnly && !(sender instanceof Player)) {
            sender.sendMessage(playerOnlyMessage);
            return;
        }

        // Cooldown check
        if (!cooldown.isEmpty() && cooldownSeconds > 0 && sender instanceof Player player && cooldownManager != null) {
            if (cooldownManager.isActive(player.getUniqueId(), cooldown)) {
                long remaining = cooldownManager.remainingSeconds(player.getUniqueId(), cooldown);
                sender.sendMessage(cooldownMessage.append(
                        Component.text(" (" + remaining + "s)", NamedTextColor.GRAY)));
                return;
            }
            cooldownManager.set(player.getUniqueId(), cooldown, cooldownSeconds, TimeUnit.SECONDS);
        }

        // Parse arguments and invoke
        Runnable invocation = () -> {
            try {
                Object[] parsed = parseArguments(method, sender, args);
                if (parsed == null) {
                    if (!usage.isEmpty()) {
                        sender.sendMessage(Component.text("Usage: " + usage, NamedTextColor.YELLOW));
                    }
                    return;
                }
                method.invoke(handler, parsed);
            } catch (Exception e) {
                sender.sendMessage(Component.text("An error occurred while executing this command.", NamedTextColor.RED));
                plugin.getLogger().log(Level.SEVERE, "Error executing command", e);
            }
        };

        if (async) {
            scheduler.runAsync(invocation);
        } else {
            invocation.run();
        }
    }

    // ── Argument parsing ──────────────────────────────────────────────────────

    private @Nullable Object[] parseArguments(Method method, CommandSender sender, String[] args) {
        Parameter[] params = method.getParameters();
        Object[] result = new Object[params.length];
        int argIndex = 0;

        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i].getType();

            // CommandSender / Player injection
            if (CommandSender.class.isAssignableFrom(type)) {
                result[i] = sender;
                continue;
            }

            Arg argAnn = params[i].getAnnotation(Arg.class);
            boolean optional = argAnn != null && argAnn.optional();
            String defaultValue = argAnn != null && !argAnn.defaultValue().isEmpty() ? argAnn.defaultValue() : null;

            if (argIndex >= args.length) {
                if (optional && defaultValue != null) {
                    result[i] = convertArg(defaultValue, type);
                } else if (optional) {
                    result[i] = getDefaultForType(type);
                } else {
                    return null; // missing required argument
                }
            } else {
                // If this is the last parameter and it's a String, join remaining args
                if (type == String.class && i == params.length - 1 && argIndex < args.length) {
                    result[i] = String.join(" ", Arrays.copyOfRange(args, argIndex, args.length));
                    argIndex = args.length;
                } else {
                    Object converted = convertArg(args[argIndex], type);
                    if (converted == null) return null;
                    result[i] = converted;
                    argIndex++;
                }
            }
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable Object convertArg(String input, Class<?> type) {
        try {
            if (type == String.class) return input;
            if (type == int.class || type == Integer.class) return Integer.parseInt(input);
            if (type == double.class || type == Double.class) return Double.parseDouble(input);
            if (type == float.class || type == Float.class) return Float.parseFloat(input);
            if (type == long.class || type == Long.class) return Long.parseLong(input);
            if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(input);
            if (type == Player.class) return Bukkit.getPlayerExact(input);
            if (type == Material.class) {
                Material mat = Material.matchMaterial(input);
                return mat;
            }
            if (type.isEnum()) return Enum.valueOf((Class<Enum>) type, input.toUpperCase());
            return input;
        } catch (Exception e) {
            return null;
        }
    }

    private Object getDefaultForType(Class<?> type) {
        if (type == int.class) return 0;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == long.class) return 0L;
        if (type == boolean.class) return false;
        return null;
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    private List<String> generateCompletions(Method method, String[] args) {
        Parameter[] params = method.getParameters();
        int argIndex = 0;

        for (Parameter param : params) {
            if (CommandSender.class.isAssignableFrom(param.getType())) continue;
            argIndex++;
        }

        // Determine which parameter the user is currently typing
        int currentParam = 0;
        int paramArgIndex = 0;
        for (Parameter param : params) {
            if (CommandSender.class.isAssignableFrom(param.getType())) continue;
            paramArgIndex++;
            if (paramArgIndex >= args.length) {
                currentParam = paramArgIndex - 1;
                break;
            }
            currentParam = paramArgIndex;
        }

        // Find the parameter at the current arg position
        int nonSenderIdx = 0;
        for (Parameter param : params) {
            if (CommandSender.class.isAssignableFrom(param.getType())) continue;
            if (nonSenderIdx == currentParam) {
                return completionsForType(param.getType(), args.length > 0 ? args[args.length - 1] : "");
            }
            nonSenderIdx++;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<String> completionsForType(Class<?> type, String partial) {
        String lower = partial.toLowerCase();
        List<String> results = new ArrayList<>();

        if (type == Player.class) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(lower)) results.add(p.getName());
            }
        } else if (type == Material.class) {
            for (Material mat : Material.values()) {
                if (mat.isItem() && mat.name().toLowerCase().startsWith(lower)) {
                    results.add(mat.name().toLowerCase());
                }
                if (results.size() > 30) break;
            }
        } else if (type == boolean.class || type == Boolean.class) {
            if ("true".startsWith(lower)) results.add("true");
            if ("false".startsWith(lower)) results.add("false");
        } else if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                String name = ((Enum<?>) constant).name().toLowerCase();
                if (name.startsWith(lower)) results.add(name);
            }
        }
        return results;
    }

    // ── Internal records ──────────────────────────────────────────────────────

    private record RegisteredCommand(
            BluethCommand annotation,
            Method method,
            Object handler,
            List<SubcommandEntry> subcommands
    ) {}

    private record SubcommandEntry(
            Subcommand annotation,
            Method method,
            Object handler
    ) {}
}

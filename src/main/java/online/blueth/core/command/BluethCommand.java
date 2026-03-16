package online.blueth.core.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a Blueth command handler.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @BluethCommand(name = "kit", permission = "myplugin.kit", usage = "/kit <name>")
 * public void onKit(Player player, @Arg("name") String kitName) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BluethCommand {

    /** The command name (e.g. {@code "kit"}). */
    String name();

    /** Aliases for this command. */
    String[] aliases() default {};

    /** Permission required to execute (empty = no permission required). */
    String permission() default "";

    /** Usage message shown on incorrect syntax. */
    String usage() default "";

    /** Description of the command. */
    String description() default "";

    /** If {@code true}, only players can execute this command (not console). */
    boolean playerOnly() default false;

    /** If {@code true}, the command handler runs asynchronously. */
    boolean async() default false;

    /** Cooldown key for automatic cooldown integration (empty = no cooldown). */
    String cooldown() default "";

    /** Cooldown duration in seconds (used when {@link #cooldown()} is set). */
    int cooldownSeconds() default 0;
}

package online.blueth.core.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a subcommand handler within a parent command class.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @BluethCommand(name = "kit")
 * public void onKit(Player player) { ... }
 *
 * @Subcommand(parent = "kit", name = "list")
 * public void onKitList(Player player) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Subcommand {

    /** The parent command name. */
    String parent();

    /** The subcommand name (e.g. {@code "list"}). */
    String name();

    /** Permission required (empty = inherits parent permission). */
    String permission() default "";

    /** If {@code true}, only players can execute this subcommand. */
    boolean playerOnly() default false;

    /** If {@code true}, the handler runs asynchronously. */
    boolean async() default false;
}

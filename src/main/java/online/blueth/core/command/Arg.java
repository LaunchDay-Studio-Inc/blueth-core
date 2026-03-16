package online.blueth.core.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a command handler parameter for automatic argument parsing and
 * tab-completion generation.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * public void onKit(Player player, @Arg("name") String kitName) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Arg {

    /** The argument name, used in usage messages and tab completion hints. */
    String value();

    /** Default value if the argument is not provided (empty = required). */
    String defaultValue() default "";

    /** Whether this argument is optional. */
    boolean optional() default false;
}

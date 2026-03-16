package online.blueth.core.event;

/**
 * Base class for events dispatched through the {@link EventBus}.
 * Extend this class to define custom events.
 *
 * <h3>Example</h3>
 * <pre>{@code
 * public class ContractCompleteEvent extends BluethEvent {
 *     private final Player player;
 *     private final String contract;
 *
 *     public ContractCompleteEvent(Player player, String contract) {
 *         this.player = player;
 *         this.contract = contract;
 *     }
 *
 *     public Player getPlayer() { return player; }
 *     public String getContract() { return contract; }
 * }
 * }</pre>
 */
public abstract class BluethEvent {

    private boolean cancelled = false;

    /** Returns {@code true} if this event has been cancelled by a listener. */
    public boolean isCancelled() {
        return cancelled;
    }

    /** Sets the cancellation state of this event. */
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}

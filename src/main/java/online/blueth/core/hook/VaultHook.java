package online.blueth.core.hook;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

/**
 * Safe Vault economy hook with convenience deposit/withdraw/balance methods.
 *
 * <p>Call {@link #setup()} in your plugin's {@code onEnable} and check the return
 * value before using economy methods. All economy methods guard against
 * {@code isAvailable()} being false and return safe fallback values.
 */
public class VaultHook {

    private Economy economy;

    /**
     * Attempts to hook into Vault's Economy service.
     *
     * @return true if Vault is present and an economy provider is registered
     */
    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    /** Returns true if Vault economy is hooked and ready. */
    public boolean isAvailable() {
        return economy != null;
    }

    /** Returns the raw Vault {@link Economy} instance, if available. */
    public Optional<Economy> getEconomy() {
        return Optional.ofNullable(economy);
    }

    // ── Convenience methods ───────────────────────────────────────────────────

    /**
     * Returns the player's balance, or {@code -1} if Vault is unavailable.
     */
    public double getBalance(OfflinePlayer player) {
        if (!isAvailable()) return -1;
        return economy.getBalance(player);
    }

    /**
     * Deposits {@code amount} into the player's account.
     *
     * @return true if the transaction succeeded
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        if (!isAvailable() || amount <= 0) return false;
        EconomyResponse resp = economy.depositPlayer(player, amount);
        return resp.transactionSuccess();
    }

    /**
     * Withdraws {@code amount} from the player's account.
     *
     * @return true if the transaction succeeded (sufficient funds)
     */
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!isAvailable() || amount <= 0) return false;
        EconomyResponse resp = economy.withdrawPlayer(player, amount);
        return resp.transactionSuccess();
    }

    /**
     * Returns true if the player has at least {@code amount} in their account.
     */
    public boolean has(OfflinePlayer player, double amount) {
        if (!isAvailable()) return false;
        return economy.has(player, amount);
    }
}

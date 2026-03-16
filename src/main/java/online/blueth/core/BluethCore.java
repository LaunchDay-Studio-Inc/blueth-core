package online.blueth.core;

import online.blueth.core.promo.BluethPromo;
import org.bukkit.plugin.java.JavaPlugin;

public final class BluethCore extends JavaPlugin {

    private static BluethCore instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Initialize Discord promo system (freemium feature)
        boolean promoEnabled = getConfig().getBoolean("blueth.promo-enabled", true);
        if (promoEnabled) {
            String discordUrl = getConfig().getString("blueth.discord-url", "https://discord.gg/bJDGXc4DvW");
            int delayMinutes = getConfig().getInt("blueth.promo-delay-minutes", 5);
            BluethPromo.init(this, discordUrl, delayMinutes);
        }

        getLogger().info("Blueth Core v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        instance = null;
    }

    public static BluethCore getInstance() {
        return instance;
    }
}

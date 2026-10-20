package duckcatmine.test;

import org.bukkit.plugin.java.JavaPlugin;

/** Harmless original fixture, loaded only in the isolated test installation. */
public final class DCTestPlugin extends JavaPlugin {
    @Override public void onEnable() {
        getLogger().info("DUCKCATMINE_PLUGIN_FIXTURE_ENABLED");
    }
}

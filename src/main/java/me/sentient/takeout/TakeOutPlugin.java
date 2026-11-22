package me.sentient.takeout;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class TakeOutPlugin extends JavaPlugin {
    private Messages messages;
    private volatile RuleSet rules;
    private PunishmentRunner runner;
    private PackManager pack;
    private FilterListener filter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        messages.reload();
        runner = new PunishmentRunner(this);
        pack = new PackManager(this);
        filter = new FilterListener(this);
        reloadInternals();

        getServer().getPluginManager().registerEvents(filter, this);
        getServer().getPluginManager().registerEvents(pack, this);
        PluginCommand cmd = getCommand("takeout");
        if (cmd != null) {
            TakeOutCommand exec = new TakeOutCommand(this);
            cmd.setExecutor(exec);
            cmd.setTabCompleter(exec);
        }
        getLogger().info("TakeOutFilter enabled.");
    }

    private void reloadInternals() {
        rules = new RuleSet(getConfig(), getLogger());
        filter.reload();
        pack.load();
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        reloadInternals();
    }

    public Messages messages() { return messages; }
    public RuleSet rules() { return rules; }
    public PunishmentRunner runner() { return runner; }
    public PackManager pack() { return pack; }
}

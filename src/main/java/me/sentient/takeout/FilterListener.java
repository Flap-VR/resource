package me.sentient.takeout;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class FilterListener implements Listener {
    private final TakeOutPlugin plugin;
    private final Set<String> filterCommands = new HashSet<>();

    public FilterListener(TakeOutPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        filterCommands.clear();
        for (String s : plugin.getConfig().getStringList("filter-commands")) filterCommands.add(s.toLowerCase(Locale.ROOT).replace("/", ""));
    }

    /** Chat is scanned on the async chat thread, the punishment itself runs on the main thread. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("takeout.bypass")) return;
        if (plugin.runner().isActive(p.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        String text = Text.plain(e.message());
        RuleSet.Match m = plugin.rules().scan(text);
        if (m == null) return;
        if (plugin.getConfig().getBoolean("dry-run", false)) {
            Bukkit.getScheduler().runTask(plugin, () -> notifyStaff(p, m, text, true));
            return;
        }
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> trigger(p, m, text));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("takeout.bypass")) return;
        String msg = e.getMessage();
        if (msg.length() < 2) return;
        String[] parts = msg.substring(1).split("\\s+", 2);
        String label = parts[0].toLowerCase(Locale.ROOT);
        int colon = label.lastIndexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (parts.length < 2 || !filterCommands.contains(label)) return;

        if (plugin.runner().isActive(p.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        RuleSet.Match m = plugin.rules().scan(parts[1]);
        if (m == null) return;
        if (plugin.getConfig().getBoolean("dry-run", false)) {
            notifyStaff(p, m, parts[1], true);
            return;
        }
        e.setCancelled(true);
        trigger(p, m, parts[1]);
    }

    private void trigger(Player p, RuleSet.Match m, String text) {
        RuleSet.Info info = plugin.rules().info(m.category());
        if (info == null) return;
        notifyStaff(p, m, text, false);
        plugin.runner().run(p, info.duration(), info.reason(), true);
    }

    private void notifyStaff(Player p, RuleSet.Match m, String text, boolean dry) {
        var msg = plugin.messages().get(dry ? "notify-dry" : "notify",
                "player", p.getName(), "category", m.category(), "match", m.match(), "message", text);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("takeout.notify")) staff.sendMessage(msg);
        }
        plugin.getLogger().info((dry ? "[dry-run] " : "") + p.getName() + " triggered " + m.category() + " (" + m.match() + "): " + text);
    }
}

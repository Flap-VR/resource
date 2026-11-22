package me.sentient.takeout;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class TakeOutCommand implements CommandExecutor, TabCompleter {
    private final TakeOutPlugin plugin;

    public TakeOutCommand(TakeOutPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!s.hasPermission("takeout.admin")) {
            plugin.messages().send(s, "no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.messages().send(s, "usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadAll();
                plugin.messages().send(s, "reloaded");
            }
            case "test" -> {
                if (!(s instanceof Player p)) {
                    plugin.messages().send(s, "players-only");
                    return true;
                }
                if (args.length >= 2 && args[1].equalsIgnoreCase("ban")) {
                    String duration = plugin.getConfig().getString("test.ban-duration", "1m");
                    String reason = plugin.getConfig().getString("test.ban-reason", "TakeOutFilter test");
                    if (args.length >= 3) {
                        RuleSet.Info info = plugin.rules().info(args[2]);
                        if (info == null) {
                            plugin.messages().send(s, "category-missing", "name", args[2]);
                            return true;
                        }
                        duration = info.duration();
                        reason = info.reason();
                    }
                    plugin.messages().send(p, "test-ban", "duration", duration, "reason", reason, "name", p.getName());
                    plugin.runner().run(p, duration, reason, true);
                } else {
                    plugin.messages().send(p, "test-effect");
                    plugin.runner().run(p, "", "", false);
                }
            }
            case "unban" -> {
                if (args.length < 2) {
                    plugin.messages().send(s, "usage");
                    return true;
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        plugin.getConfig().getString("punishment.unban-command", "sentient unban %player%").replace("%player%", args[1]));
                plugin.messages().send(s, "unbanned", "player", args[1]);
            }
            case "check" -> {
                if (args.length < 2) {
                    plugin.messages().send(s, "usage");
                    return true;
                }
                RuleSet.Match m = plugin.rules().scan(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                if (m == null) {
                    plugin.messages().send(s, "check-clean");
                } else {
                    RuleSet.Info info = plugin.rules().info(m.category());
                    plugin.messages().send(s, "check-hit", "category", m.category(), "match", m.match(),
                            "duration", info.duration(), "reason", info.reason());
                }
            }
            case "pack" -> {
                if (!plugin.pack().ready()) {
                    plugin.messages().send(s, "pack-disabled");
                    return true;
                }
                Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (s instanceof Player p ? p : null);
                if (target == null) {
                    plugin.messages().send(s, "player-not-found", "player", args.length >= 2 ? args[1] : "?");
                    return true;
                }
                plugin.pack().send(target);
                plugin.messages().send(s, "pack-sent", "player", target.getName());
            }
            default -> plugin.messages().send(s, "usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) return List.of("test", "unban", "check", "pack", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("test")) return List.of("ban");
        if (args.length == 3 && args[0].equalsIgnoreCase("test")) return List.of("slurs", "bad_words", "promotion");
        if (args.length == 2 && (args[0].equalsIgnoreCase("unban") || args[0].equalsIgnoreCase("pack"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}

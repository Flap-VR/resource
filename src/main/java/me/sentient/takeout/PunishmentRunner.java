package me.sentient.takeout;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The sequence: sound + image  ->  image disappears  ->  ban. */
public final class PunishmentRunner {
    private final TakeOutPlugin plugin;
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();

    public PunishmentRunner(TakeOutPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isActive(UUID id) {
        return active.contains(id);
    }

    /** Runs on the main thread. With ban=false only the sound + image are shown (used by /takeout test). */
    public void run(Player p, String duration, String reason, boolean ban) {
        UUID id = p.getUniqueId();
        if (!active.add(id)) return;
        FileConfiguration c = plugin.getConfig();
        String name = p.getName();
        boolean effect = c.getBoolean("effect.enabled", true);
        long ticks = effect ? Math.max(1, c.getLong("effect.duration-ticks", 74)) : 1;
        String sound = c.getString("effect.sound", "takeout:disappointment");

        if (effect) {
            p.playSound(p.getLocation(), sound, SoundCategory.MASTER, (float) c.getDouble("effect.volume", 1.0), 1.0f);
            Component image = Component.text(c.getString("effect.glyph", "\uE000"))
                    .font(Key.key(c.getString("effect.font", "takeout:image")));
            p.showTitle(Title.title(image, Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(ticks * 50L + 1000L), Duration.ZERO)));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(id);
            if (online != null && effect) {
                online.clearTitle();
                online.stopSound(sound, SoundCategory.MASTER);
            }
            if (ban) {
                String cmd = c.getString("punishment.ban-command", "sentient ban %player% %duration% %reason%")
                        .replace("%player%", name)
                        .replace("%duration%", duration)
                        .replace("%reason%", reason);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                plugin.getLogger().info("Banned " + name + " for " + duration + " (" + reason + ")");
            }
            active.remove(id);
        }, ticks);
    }
}

package me.sentient.takeout;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class Messages {
    private final TakeOutPlugin plugin;
    private YamlConfiguration yaml = new YamlConfiguration();

    public Messages(TakeOutPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        yaml = YamlConfiguration.loadConfiguration(f);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
        }
    }

    public String raw(String key) {
        String s = yaml.getString(key);
        return s == null ? "<red>Missing message: " + key : s;
    }

    public Component get(String key, Object... kv) {
        return Text.parse(raw(key), kv);
    }

    /** Command feedback with the prefix. */
    public void send(CommandSender to, String key, Object... kv) {
        to.sendMessage(Text.parse(raw("prefix") + raw(key), kv));
    }
}

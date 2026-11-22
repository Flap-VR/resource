package me.sentient.takeout;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Sends the resource pack on join (forced) and kicks players who refuse it. */
public final class PackManager implements Listener {
    private final TakeOutPlugin plugin;
    private final UUID packId = UUID.nameUUIDFromBytes("takeoutfilter-resource-pack".getBytes(StandardCharsets.UTF_8));
    private volatile byte[] hash;

    public PackManager(TakeOutPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean ready() {
        FileConfiguration c = plugin.getConfig();
        String url = c.getString("resource-pack.url", "");
        return c.getBoolean("resource-pack.enabled", true) && url != null && !url.isBlank();
    }

    /** Reads the sha1 from config, or downloads the pack once (async) to work it out. */
    public void load() {
        hash = null;
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("resource-pack.enabled", true)) return;
        String url = c.getString("resource-pack.url", "");
        if (url == null || url.isBlank()) {
            plugin.getLogger().warning("resource-pack.url is empty: players will NOT get the sound/image until you set it.");
            return;
        }
        String sha = c.getString("resource-pack.sha1", "").trim();
        if (sha.length() == 40) {
            hash = hex(sha);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
                HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + res.statusCode());
                hash = MessageDigest.getInstance("SHA-1").digest(res.body());
                plugin.getLogger().info("Resource pack downloaded for hashing (" + res.body().length + " bytes).");
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not download the resource pack to compute its hash: " + ex.getMessage()
                        + " (check resource-pack.url, it must be a direct download link)");
            }
        });
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    public void send(Player p) {
        FileConfiguration c = plugin.getConfig();
        p.addResourcePack(packId, c.getString("resource-pack.url", ""), hash,
                Text.parse(c.getString("resource-pack.prompt", "")), c.getBoolean("resource-pack.force", true));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (ready()) send(e.getPlayer());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        if (!ready() || !e.getID().equals(packId)) return;
        Set<String> kickOn = new HashSet<>();
        for (String s : plugin.getConfig().getStringList("resource-pack.kick-on")) kickOn.add(s.toUpperCase(Locale.ROOT));
        if (!kickOn.contains(e.getStatus().name())) return;
        Player p = e.getPlayer();
        if (p.hasPermission("takeout.bypass")) return;
        p.kick(Text.parse(plugin.getConfig().getString("resource-pack.kick-message", "<red>You must accept the resource pack.")));
    }
}

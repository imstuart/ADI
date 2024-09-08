package re.mxc.adi;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Adi extends JavaPlugin implements TabCompleter, Listener {

    private String webhookUrl;
    private boolean logJoins;
    private boolean logQuits;
    private boolean logBlockPlace;
    private boolean logBlockBreak;
    private boolean logServerStartStop;
    private boolean debugLog;
    private String lang;
    private FileConfiguration langConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        copyLangFiles();
        loadLangFile();

        if (debugLog) {
            getLogger().info("Webhook URL loaded: " + webhookUrl);
        }

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            String errorMessage = getLangMessage("webhook-missing");
            getLogger().severe(errorMessage);

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("adi.admin")) {
                    player.sendMessage(errorMessage);
                }
            }

            throw new IllegalStateException("Webhook URL is missing in the configuration.");
        }

        getCommand("adi").setExecutor(this);
        getCommand("adi").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);

        if (logServerStartStop) {
            String color = getLangMessage("server-start-color");
            sendDiscordMessage(getLangMessage("server-start-message"), color);
        }

        getLogger().info("Adi plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        if (logServerStartStop) {
            String color = getLangMessage("server-stop-color");
            sendDiscordMessage(getLangMessage("server-stop-message"), color);
        }
        getLogger().info("Adi plugin has been disabled!");
    }

    private void loadConfigValues() {
        FileConfiguration config = getConfig();
        webhookUrl = config.getString("discord-webhook-url");
        logJoins = config.getBoolean("log-joins", true);
        logQuits = config.getBoolean("log-quits", true);
        logBlockPlace = config.getBoolean("log-block-place", true);
        logBlockBreak = config.getBoolean("log-block-break", true);
        logServerStartStop = config.getBoolean("log-server-start-stop", true);
        debugLog = config.getBoolean("debug-log", false);
        lang = config.getString("lang", "en");
    }

    private void copyLangFiles() {
        File langFolder = new File(getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        String[] langFiles = {"lang_en.yml", "lang_fr.yml"};

        for (String langFile : langFiles) {
            File file = new File(langFolder, langFile);
            if (!file.exists()) {
                try (InputStream in = getResource("lang/" + langFile);
                     OutputStream out = new FileOutputStream(file)) {
                    if (in == null) {
                        getLogger().warning("Resource not found: " + "lang/" + langFile);
                        continue;
                    }
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = in.read(buffer)) > 0) {
                        out.write(buffer, 0, length);
                    }
                    getLogger().info("Copied language file: " + langFile);

                    try (InputStream checkIn = new FileInputStream(file)) {
                        byte[] checkBuffer = new byte[1024];
                        int checkLength;
                        StringBuilder fileContents = new StringBuilder();
                        while ((checkLength = checkIn.read(checkBuffer)) > 0) {
                            fileContents.append(new String(checkBuffer, 0, checkLength, StandardCharsets.UTF_8));
                        }
                        getLogger().info("Contents of " + langFile + ": " + fileContents.toString());
                    }
                } catch (Exception e) {
                    getLogger().severe("Failed to copy language file: " + langFile);
                    e.printStackTrace();
                }
            }
        }
    }

    private void loadLangFile() {
        File langFolder = new File(getDataFolder(), "lang");
        File langFile = new File(langFolder, "lang_" + lang + ".yml");

        if (langFile.exists()) {
            langConfig = YamlConfiguration.loadConfiguration(langFile);
        } else {
            getLogger().severe("Language file not found: " + langFile.getName());
            langConfig = new YamlConfiguration();
        }
    }

    private String getLangMessage(String key) {
        return langConfig.getString("messages." + key, "Message not found.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("adi") || command.getName().equalsIgnoreCase("advanceddiscordintegration")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("adi.admin")) {
                    reloadConfig();
                    loadConfigValues();
                    loadLangFile();
                    sender.sendMessage(getLangMessage("reload-success"));

                    if (webhookUrl == null || webhookUrl.isEmpty()) {
                        sender.sendMessage(getLangMessage("webhook-missing"));
                        getLogger().severe(getLangMessage("webhook-missing"));
                    }
                    return true;
                } else {
                    sender.sendMessage(getLangMessage("reload-permission-error"));
                    return true;
                }
            } else {
                sender.sendMessage(getLangMessage("usage-error"));
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("adi") || command.getName().equalsIgnoreCase("advanceddiscordintegration")) {
            if (args.length == 1) {
                if ("reload".startsWith(args[0].toLowerCase())) {
                    completions.add("reload");
                }
            }
        }
        return completions;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (logJoins && webhookUrl != null && !webhookUrl.isEmpty()) {
            String playerName = event.getPlayer().getName();
            String color = getLangMessage("join-color");
            sendDiscordMessage(getLangMessage("join-message").replace("{player}", playerName), color);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (logQuits && webhookUrl != null && !webhookUrl.isEmpty()) {
            String playerName = event.getPlayer().getName();
            String color = getLangMessage("quit-color");
            sendDiscordMessage(getLangMessage("quit-message").replace("{player}", playerName), color);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (logBlockPlace && webhookUrl != null && !webhookUrl.isEmpty()) {
            String playerName = event.getPlayer().getName();
            String blockType = event.getBlock().getType().toString();
            String color = getLangMessage("block-place-color");
            sendDiscordMessage(getLangMessage("block-place-message").replace("{player}", playerName).replace("{block}", blockType), color);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (logBlockBreak && webhookUrl != null && !webhookUrl.isEmpty()) {
            String playerName = event.getPlayer().getName();
            String blockType = event.getBlock().getType().toString();
            String color = getLangMessage("block-break-color");
            sendDiscordMessage(getLangMessage("block-break-message").replace("{player}", playerName).replace("{block}", blockType), color);
        }
    }

    private void sendDiscordMessage(String message, String color) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (debugLog) {
                        getLogger().info("Attempting to send embed to Discord: " + message);
                    }

                    String jsonPayload = "{"
                            + "\"embeds\": ["
                            + "{"
                            + "\"title\": \"" + getLangMessage("embed-title") + "\","
                            + "\"description\": \"" + message + "\","
                            + "\"color\": " + color
                            + "}"
                            + "]"
                            + "}";

                    getLogger().info("JSON Payload: " + jsonPayload);

                    URL url = new URL(webhookUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    connection.setDoOutput(true);

                    try (OutputStream os = connection.getOutputStream()) {
                        byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        if (debugLog) {
                            getLogger().info("Message successfully sent to Discord!");
                        }
                    } else {
                        if (debugLog) {
                            getLogger().warning("Failed to send message to Discord: " + responseCode);
                        }
                    }
                } catch (Exception e) {
                    getLogger().severe("An error occurred while sending message to Discord.");
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(this);
    }

    @EventHandler
    public void onServerStart(ServerCommandEvent event) {
        if (logServerStartStop && webhookUrl != null && !webhookUrl.isEmpty()) {
            String color = getLangMessage("server-start-color");
            sendDiscordMessage(getLangMessage("server-start-message"), color);
        }
    }
}

package org.blazepxly.antiFreeHit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AntiFreeHit extends JavaPlugin implements Listener {

    private final Map<UUID, Long> combatPlayers = new HashMap<>();
    private final Map<UUID, BukkitTask> actionBarTasks = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        combatPlayers.clear();
        actionBarTasks.values().forEach(BukkitTask::cancel);
        actionBarTasks.clear();
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player attacker = (Player) event.getDamager();
        Player defender = (Player) event.getEntity();

        setCombat(attacker);
        setCombat(defender);
    }

    private void setCombat(Player player) {
        UUID playerId = player.getUniqueId();

        // Bersihkan task sebelumnya jika ada
        BukkitTask oldTask = actionBarTasks.get(playerId);
        if (oldTask != null) {
            oldTask.cancel();
        }

        long startTime = System.currentTimeMillis();
        combatPlayers.put(playerId, startTime);
        player.sendMessage("§cYou are now in combat!");

        // Buat task baru untuk action bar
        BukkitTask newTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            long remainingTime = 15000 - (System.currentTimeMillis() - startTime);

            if (remainingTime <= 0) {
                removeFromCombat(player);
                return;
            }

            sendActionBar(player, "§cCombat: §f" + (remainingTime / 1000) + "s remaining");
        }, 0L, 5L); // Update setiap 5 tick (0.25 detik)

        actionBarTasks.put(playerId, newTask);

        // Schedule untuk keluar otomatis setelah 15 detik
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (isInCombat(player)) {
                removeFromCombat(player);
            }
        }, 15 * 20L);
    }

    private void removeFromCombat(Player player) {
        UUID playerId = player.getUniqueId();

        combatPlayers.remove(playerId);

        BukkitTask task = actionBarTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }

        sendActionBar(player, "");
        player.sendMessage("§aYou are no longer in combat!");
    }

    private void sendActionBar(Player player, String message) {
        // Metode untuk mengirim ActionBar (kompatibel dengan berbagai versi)
        try {
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message));
        } catch (NoSuchMethodError e) {
            // Fallback untuk versi lama
            player.sendMessage(message);
        }
    }

    public boolean isInCombat(Player player) {
        return combatPlayers.containsKey(player.getUniqueId());
    }
}

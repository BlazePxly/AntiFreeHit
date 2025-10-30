package org.blazepxly.antiFreeHit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AntiFreeHit extends JavaPlugin implements Listener {

    private final Map<UUID, Set<UUID>> combatPairs = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> combatTasks = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getLogger().info("[DEBUG] Plugin enabled");
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        Bukkit.getLogger().info("[DEBUG] Plugin disabled, cancelling all tasks");
        combatTasks.values().forEach(BukkitTask::cancel);
        combatPairs.clear();
        combatTasks.clear();
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        Bukkit.getLogger().info("[DEBUG] onPlayerDamage triggered");
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            Bukkit.getLogger().info("[DEBUG] Event ignored, either entity or damager is not player");
            return;
        }

        Player attacker = (Player) event.getDamager();
        Player defender = (Player) event.getEntity();
        UUID attackerId = attacker.getUniqueId();
        UUID defenderId = defender.getUniqueId();

        Bukkit.getLogger().info("[DEBUG] Attacker: " + attacker.getName() + ", Defender: " + defender.getName());

        Set<UUID> attackerOpponents = combatPairs.getOrDefault(attackerId, Collections.emptySet());
        Set<UUID> defenderOpponents = combatPairs.getOrDefault(defenderId, Collections.emptySet());

        if (!attackerOpponents.contains(defenderId) && combatTasks.containsKey(attackerId)) {
            String names = getOpponentNames(attackerOpponents);
            attacker.sendMessage("§cYou are still in combat" + (names.isEmpty() ? "." : " with " + names + "."));
            event.setCancelled(true);
            Bukkit.getLogger().info("[DEBUG] Attack blocked: attacker still in combat with " + names);
            return;
        }

        if (!defenderOpponents.contains(attackerId) && combatTasks.containsKey(defenderId)) {
            defender.sendMessage("§cYou are still in combat with someone else.");
            attacker.sendMessage("§cThat player is currently in combat.");
            event.setCancelled(true);
            Bukkit.getLogger().info("[DEBUG] Attack blocked: defender still in combat with others");
            return;
        }

        tagCombatPair(attacker, defender);

        attacker.sendMessage("§eYou are fighting with " + defender.getName());
        defender.sendMessage("§eYou are fighting with " + attacker.getName());
        Bukkit.getLogger().info("[DEBUG] Combat tagged between " + attacker.getName() + " and " + defender.getName());
    }

    private String getOpponentNames(Set<UUID> opponents) {
        if (opponents == null || opponents.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (UUID id : opponents) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(p.getName());
            }
        }
        return sb.toString();
    }

    private void tagCombatPair(Player p1, Player p2) {
        UUID uuid1 = p1.getUniqueId();
        UUID uuid2 = p2.getUniqueId();

        Bukkit.getLogger().info("[DEBUG] tagCombatPair called for " + p1.getName() + " and " + p2.getName());

        combatPairs.computeIfAbsent(uuid1, k -> new HashSet<>()).add(uuid2);
        combatPairs.computeIfAbsent(uuid2, k -> new HashSet<>()).add(uuid1);

        resetCombatTimer(uuid1);
        resetCombatTimer(uuid2);

        Bukkit.getLogger().info("[DEBUG] Combat pairs updated: " + p1.getName() + " -> " + getOpponentNames(combatPairs.get(uuid1)) +
                ", " + p2.getName() + " -> " + getOpponentNames(combatPairs.get(uuid2)));
    }

    private void resetCombatTimer(UUID playerId) {
        Bukkit.getLogger().info("[DEBUG] resetCombatTimer called for " + playerId);
        if (combatTasks.containsKey(playerId)) {
            combatTasks.get(playerId).cancel();
            Bukkit.getLogger().info("[DEBUG] Old timer cancelled for " + playerId);
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            Bukkit.getLogger().info("[DEBUG] Timer expired for " + playerId);
            Set<UUID> opponents = combatPairs.remove(playerId);

            if (opponents != null) {
                for (UUID oppId : opponents) {
                    Set<UUID> oppSet = combatPairs.get(oppId);
                    if (oppSet != null) {
                        oppSet.remove(playerId);
                        if (oppSet.isEmpty()) combatPairs.remove(oppId);
                    }
                }
            }

            combatTasks.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage("§aYou are no longer in combat.");
            }
            Bukkit.getLogger().info("[DEBUG] Player " + playerId + " removed from combatPairs");
        }, 20 * 15);

        combatTasks.put(playerId, task);
        Bukkit.getLogger().info("[DEBUG] Timer scheduled for " + playerId + " (15s)");
    }
}

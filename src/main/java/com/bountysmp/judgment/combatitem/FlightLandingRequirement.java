package com.bountysmp.judgment.combatitem;

import com.bountysmp.judgment.damage.DamageAttribution;
import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Flight history is independent of combat tags: an opening attack must pass this gate too. */
public final class FlightLandingRequirement implements Listener, AutoCloseable {
    private final Plugin plugin;
    private final Supplier<CombatItemSettings> settings;
    private final DamageAttribution attribution;
    private final Predicate<UUID> executingPunishment;
    private final Predicate<Player> grounded;
    private final FlightLandingStore store;
    private final Map<UUID, EnumSet<CombatItemAction>> pending = new HashMap<>();
    private final Map<UUID, Long> notices = new HashMap<>();
    private final BukkitTask task;
    private boolean storageHealthy = true;

    public FlightLandingRequirement(Plugin plugin, Supplier<CombatItemSettings> settings,
                                    DamageAttribution attribution, Predicate<UUID> executingPunishment,
                                    FlightLandingStore store) {
        this(plugin, settings, attribution, executingPunishment, store, FlightLandingRequirement::confirmedGrounded);
    }

    FlightLandingRequirement(Plugin plugin, Supplier<CombatItemSettings> settings,
                             DamageAttribution attribution, Predicate<UUID> executingPunishment,
                             FlightLandingStore store, Predicate<Player> grounded) {
        this.plugin = plugin;
        this.settings = settings;
        this.attribution = attribution;
        this.executingPunishment = executingPunishment;
        this.store = store;
        this.grounded = grounded;
        try {
            pending.putAll(store.load());
        } catch (IOException exception) {
            storageHealthy = false;
            plugin.getLogger().log(Level.SEVERE, "Cannot load flight restrictions; affected PvP is blocked until storage is repaired and the server restarted.", exception);
        }
        settingsChanged();
        task = Bukkit.getScheduler().runTaskTimer(plugin, (Runnable) this::refresh, 1L, 1L);
    }

    private boolean banned(CombatItemAction action) { return settings.get().seconds(action) == -1.0; }

    private boolean active() {
        return settings.get().requireLanding() && (banned(CombatItemAction.ELYTRA) || banned(CombatItemAction.FIREWORKS));
    }

    public void settingsChanged() {
        boolean changed = pending.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(action -> !settings.get().requireLanding() || !banned(action));
            return entry.getValue().isEmpty();
        });
        // A surviving cause can still have changed, even when its player entry remains.
        if (storageHealthy && (changed || !pending.isEmpty())) save();
        refresh();
    }

    void refresh() {
        for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
    }

    void refresh(Player player) {
        if (!active()) return;
        if (!pending.containsKey(player.getUniqueId()) && !(banned(CombatItemAction.ELYTRA) && player.isGliding())) return;
        if (player.isDead() || grounded.test(player)) {
            clear(player.getUniqueId());
        } else if (banned(CombatItemAction.ELYTRA) && player.isGliding()) {
            mark(player.getUniqueId(), CombatItemAction.ELYTRA);
        }
    }

    private void mark(UUID id, CombatItemAction action) {
        if (pending.computeIfAbsent(id, ignored -> EnumSet.noneOf(CombatItemAction.class)).add(action)) save();
    }

    private void clear(UUID id) {
        if (pending.remove(id) != null) save();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player player
            && settings.get().requireLanding() && banned(CombatItemAction.ELYTRA))
            mark(player.getUniqueId(), CombatItemAction.ELYTRA);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBoost(PlayerElytraBoostEvent event) {
        if (!active()) return;
        mark(event.getPlayer().getUniqueId(), banned(CombatItemAction.ELYTRA)
            ? CombatItemAction.ELYTRA : CombatItemAction.FIREWORKS);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> refresh(event.getPlayer()));
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { notices.remove(event.getPlayer().getUniqueId()); }

    @EventHandler public void onDeath(PlayerDeathEvent event) { clear(event.getEntity().getUniqueId()); }

    boolean blocks(UUID attacker, Player victim) {
        if (attacker == null || attacker.equals(victim.getUniqueId()) || !active()
            || executingPunishment.test(victim.getUniqueId())) return false;
        Player player = Bukkit.getPlayer(attacker);
        if (player != null) refresh(player);
        if (storageHealthy && !pending.containsKey(attacker)) return false;
        long now = System.currentTimeMillis();
        if (player != null && now - notices.getOrDefault(attacker, 0L) >= 500L) {
            player.sendActionBar(Component.text("Land before damaging other players.", NamedTextColor.RED));
            notices.put(attacker, now);
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void protectDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && blocks(attribution.attacker(event), victim))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void protectIgnition(EntityCombustByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && blocks(attribution.responsiblePlayer(event.getCombuster()), victim))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void protectSplash(PotionSplashEvent event) {
        if (!DamageAttribution.harmful(event.getPotion().getEffects())) return;
        UUID attacker = attribution.responsiblePlayer(event.getPotion());
        for (var entity : java.util.List.copyOf(event.getAffectedEntities())) {
            if (entity instanceof Player victim && blocks(attacker, victim)) event.setIntensity(victim, 0.0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void protectCloud(AreaEffectCloudApplyEvent event) {
        if (!DamageAttribution.harmfulCloud(event.getEntity())) return;
        UUID attacker = attribution.responsiblePlayer(event.getEntity());
        event.getAffectedEntities().removeIf(entity -> entity instanceof Player victim && blocks(attacker, victim));
    }

    /** Check actual block support as well as the on-ground flag; water or stopping glide is insufficient. */
    static boolean confirmedGrounded(Player player) {
        if (!player.isOnGround() || player.isGliding() || player.isInWater() || player.getVelocity().getY() > 0.08) return false;
        BoundingBox body = player.getBoundingBox();
        double feetY = body.getMinY();
        BoundingBox feet = new BoundingBox(body.getMinX() + 0.001, feetY - 0.06, body.getMinZ() + 0.001,
            body.getMaxX() - 0.001, feetY + 0.001, body.getMaxZ() - 0.001);
        for (int x = (int) Math.floor(feet.getMinX()); x <= Math.floor(feet.getMaxX()); x++) {
            for (int y = (int) Math.floor(feetY) - 1; y <= Math.floor(feetY); y++) {
                for (int z = (int) Math.floor(feet.getMinZ()); z <= Math.floor(feet.getMaxZ()); z++) {
                    var block = player.getWorld().getBlockAt(x, y, z);
                    for (BoundingBox local : block.getCollisionShape().getBoundingBoxes()) {
                        BoundingBox support = local.clone().shift(x, y, z);
                        if (support.getMaxY() <= feetY + 0.001 && support.overlaps(feet)) return true;
                    }
                }
            }
        }
        return false;
    }

    private void save() {
        // Never overwrite unreadable persisted state with an incomplete in-memory snapshot.
        if (!storageHealthy) return;
        try {
            store.save(pending);
        } catch (IOException exception) {
            storageHealthy = false;
            plugin.getLogger().log(Level.SEVERE, "Cannot save flight restrictions; affected PvP is blocked until storage is repaired and the server restarted.", exception);
        }
    }

    @Override public void close() { task.cancel(); }
}

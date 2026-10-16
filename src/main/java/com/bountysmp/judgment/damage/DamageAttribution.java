package com.bountysmp.judgment.damage;

import com.bountysmp.judgment.combatitem.CombatItemAction;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shared attribution for PvP protection, combat tags, and combat item rules. */
public final class DamageAttribution {
    private final Plugin plugin;
    private final Map<UUID, UUID> explosiveAttackers = new HashMap<>();
    private final Map<BlockPosition, UUID> blockExplosionAttackers = new HashMap<>();
    private final Map<BlockPosition, CombatItemAction> blockExplosions = new HashMap<>();

    public DamageAttribution(Plugin plugin) { this.plugin = plugin; }

    public UUID responsiblePlayer(Entity entity) {
        return responsiblePlayer(entity, 0);
    }

    private UUID responsiblePlayer(Entity entity, int depth) {
        if (entity == null || depth > 8) return null;
        if (entity instanceof Player player) return player.getUniqueId();
        if (entity instanceof Tameable pet && pet.getOwner() != null) return pet.getOwner().getUniqueId();
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter)
            return responsiblePlayer(shooter, depth + 1);
        if (entity instanceof TNTPrimed tnt) return responsiblePlayer(tnt.getSource(), depth + 1);
        UUID explosiveAttacker = explosiveAttackers.get(entity.getUniqueId());
        if (explosiveAttacker != null) return explosiveAttacker;
        if (entity instanceof AreaEffectCloud cloud) {
            if (cloud.getSource() instanceof Entity source) return responsiblePlayer(source, depth + 1);
            return cloud.getOwnerUniqueId();
        }
        return null;
    }

    public UUID attacker(EntityDamageEvent event) {
        UUID id = responsiblePlayer(event.getDamageSource().getCausingEntity());
        if (id == null) id = responsiblePlayer(event.getDamageSource().getDirectEntity());
        if (id == null && event instanceof EntityDamageByEntityEvent byEntity) id = responsiblePlayer(byEntity.getDamager());
        if (id == null && event.getDamageSource().getSourceLocation() != null)
            id = blockExplosionAttackers.get(BlockPosition.from(event.getDamageSource().getSourceLocation(), event.getEntity().getWorld()));
        return id;
    }

    public void trackExplosiveAttacker(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal || event.getEntity() instanceof ExplosiveMinecart)) return;
        UUID attacker = attacker(event);
        if (attacker == null) return;
        UUID explosive = event.getEntity().getUniqueId();
        explosiveAttackers.put(explosive, attacker);
        Bukkit.getScheduler().runTask(plugin, () -> explosiveAttackers.remove(explosive, attacker));
    }

    public void trackBlockExplosionAttacker(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        var block = event.getClickedBlock();
        boolean bed = org.bukkit.Tag.BEDS.isTagged(block.getType())
            && event.getPlayer().getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL;
        boolean anchor = block.getType() == org.bukkit.Material.RESPAWN_ANCHOR
            && event.getPlayer().getWorld().getEnvironment() != org.bukkit.World.Environment.NETHER;
        if (!bed && !anchor) return;
        BlockPosition position = BlockPosition.from(block.getLocation(), block.getWorld());
        UUID attacker = event.getPlayer().getUniqueId();
        blockExplosionAttackers.put(position, attacker);
        rememberBlockExplosion(block, bed ? CombatItemAction.BEDS : CombatItemAction.RESPAWN_ANCHORS);
        Bukkit.getScheduler().runTask(plugin, () -> blockExplosionAttackers.remove(position, attacker));
    }

    public CombatItemAction explosiveAction(EntityDamageEvent event) {
        CombatItemAction action = explosiveEntityAction(event.getDamageSource().getDirectEntity());
        if (action == null) action = explosiveEntityAction(event.getDamageSource().getCausingEntity());
        if (action == null && event instanceof EntityDamageByEntityEvent byEntity)
            action = explosiveEntityAction(byEntity.getDamager());
        if (action != null) return action;
        if (!event.getDamageSource().getDamageType().equals(DamageType.BAD_RESPAWN_POINT)) return null;
        Location source = event.getDamageSource().getSourceLocation();
        return source == null ? null : blockExplosions.get(BlockPosition.from(source, event.getEntity().getWorld()));
    }

    private static CombatItemAction explosiveEntityAction(Entity entity) {
        if (entity instanceof TNTPrimed) return CombatItemAction.TNT;
        if (entity instanceof ExplosiveMinecart) return CombatItemAction.TNT_MINECARTS;
        if (entity instanceof EnderCrystal) return CombatItemAction.END_CRYSTALS;
        return null;
    }

    public void trackBadRespawnExplosion(BlockExplodeEvent event) {
        Material type = event.getExplodedBlockState().getType();
        CombatItemAction action = Tag.BEDS.isTagged(type) ? CombatItemAction.BEDS
            : type == Material.RESPAWN_ANCHOR ? CombatItemAction.RESPAWN_ANCHORS : null;
        if (action != null) rememberBlockExplosion(event.getBlock(), action);
    }

    private void rememberBlockExplosion(Block block, CombatItemAction action) {
        BlockPosition position = BlockPosition.from(block.getLocation(), block.getWorld());
        blockExplosions.put(position, action);
        Bukkit.getScheduler().runTask(plugin, () -> blockExplosions.remove(position, action));
    }

    public static boolean harmful(java.util.Collection<org.bukkit.potion.PotionEffect> effects) {
        return effects.stream().anyMatch(effect -> effect.getType().getEffectCategory()
            != org.bukkit.potion.PotionEffectType.Category.BENEFICIAL);
    }

    public static boolean harmfulCloud(AreaEffectCloud cloud) {
        return harmful(cloud.getCustomEffects()) || (cloud.getBasePotionType() != null
            && harmful(cloud.getBasePotionType().getPotionEffects()));
    }

    private record BlockPosition(UUID world, int x, int y, int z) {
        static BlockPosition from(Location location, World fallbackWorld) {
            World world = location.getWorld() == null ? fallbackWorld : location.getWorld();
            return new BlockPosition(world.getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}

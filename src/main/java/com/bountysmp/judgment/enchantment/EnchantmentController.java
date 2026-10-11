package com.bountysmp.judgment.enchantment;

import com.bountysmp.judgment.gui.SettingsGui;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import io.papermc.paper.event.player.PlayerPurchaseEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Container;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.loot.Lootable;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.function.Supplier;

/** Event enforcement and bounded reconciliation of items already in the world. */
public final class EnchantmentController implements Listener, AutoCloseable {
    private final Plugin plugin;
    private final Supplier<EnchantmentSettings> settings;
    private final EnchantmentSanitizer sanitizer;
    private final Set<ChunkKey> chunks = new LinkedHashSet<>();
    private final Set<UUID> pendingPlayers = new HashSet<>();
    private final Set<BukkitTask> deferredTasks = new HashSet<>();
    private final BukkitTask inventoryTask;
    private final BukkitTask chunkTask;
    private boolean cleaning;
    private boolean closed;

    public EnchantmentController(Plugin plugin, Supplier<EnchantmentSettings> settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.sanitizer = new EnchantmentSanitizer(settings);
        inventoryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::reconcilePlayers, 1L, 20L);
        chunkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanNextChunk, 1L, 1L);
    }

    public void rulesChanged() {
        chunks.clear();
        if (!sanitizer.active()) return;
        reconcilePlayers();
        for (World world : Bukkit.getWorlds())
            for (Chunk chunk : world.getLoadedChunks()) enqueue(chunk);
    }

    private void guarded(Runnable action) {
        if (closed || cleaning || !sanitizer.active()) return;
        cleaning = true;
        try { action.run(); } finally { cleaning = false; }
    }

    private void later(Runnable action) {
        if (closed) return;
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTask(plugin, () -> {
            deferredTasks.remove(task[0]);
            if (!closed) action.run();
        });
        deferredTasks.add(task[0]);
    }

    private void schedulePlayer(Player player) {
        if (closed || !sanitizer.active() || !pendingPlayers.add(player.getUniqueId())) return;
        later(() -> {
            pendingPlayers.remove(player.getUniqueId());
            if (player.isOnline()) cleanPlayer(player);
        });
    }

    private void reconcilePlayers() {
        if (!sanitizer.active()) return;
        for (Player player : Bukkit.getOnlinePlayers()) cleanPlayer(player);
    }

    public void cleanPlayer(Player player) {
        guarded(() -> {
            sanitizer.sanitizeInventory(player.getInventory());
            sanitizer.sanitizeInventory(player.getEnderChest());
            ItemStack cursor = player.getItemOnCursor();
            if (sanitizer.sanitize(cursor)) player.setItemOnCursor(cursor);
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top != null && !SettingsGui.isSettingsHolder(top.getHolder())) {
                sanitizer.sanitizeInventory(top);
                if (top instanceof MerchantInventory merchant) cleanMerchant(merchant.getMerchant());
                if (top instanceof AnvilInventory) cleanAnvilResult(top);
            }
        });
    }

    private void cleanAnvilResult(Inventory inventory) {
        ItemStack result = inventory.getItem(2);
        if (sanitizer.sanitize(result)) inventory.setItem(2, result);
        if (EnchantmentSanitizer.equivalentAnvilResult(inventory.getItem(0), result)) inventory.setItem(2, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOffers(PrepareItemEnchantEvent event) {
        if (!sanitizer.active() || EnchantmentSanitizer.isBook(event.getItem())) return;
        guarded(() -> sanitizer.sanitizeInventory(event.getInventory()));
        var offers = event.getOffers();
        for (int i = 0; i < offers.length; i++) {
            if (offers[i] == null) continue;
            int level = settings.get().allowedLevel(offers[i].getEnchantment(), offers[i].getEnchantmentLevel());
            if (level == 0) offers[i] = null;
            else offers[i].setEnchantmentLevel(level);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (!sanitizer.active() || EnchantmentSanitizer.isBook(event.getItem())) return;
        sanitizer.sanitize(event.getItem());
        var iterator = event.getEnchantsToAdd().entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            int level = settings.get().allowedLevel(entry.getKey(), entry.getValue());
            if (level == 0) iterator.remove();
            else entry.setValue(level);
        }
        if (event.getEnchantsToAdd().isEmpty()) event.setCancelled(true);
        else schedulePlayer(event.getEnchanter());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        guarded(() -> {
            ItemStack result = event.getResult();
            sanitizer.sanitize(result);
            // Inputs can trigger a recalculation; apply the sanitized result last.
            for (int slot = 0; slot < 2; slot++) {
                ItemStack input = event.getInventory().getItem(slot);
                if (sanitizer.sanitize(input)) event.getInventory().setItem(slot, input);
            }
            if (EnchantmentSanitizer.equivalentAnvilResult(event.getInventory().getItem(0), result)) result = null;
            event.setResult(result);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithing(PrepareSmithingEvent event) {
        guarded(() -> {
            ItemStack result = event.getResult();
            sanitizer.sanitize(result);
            event.setResult(result);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGrindstone(PrepareGrindstoneEvent event) {
        guarded(() -> {
            ItemStack result = event.getResult();
            sanitizer.sanitize(result);
            event.setResult(result);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        guarded(() -> {
            ItemStack result = event.getInventory().getResult();
            if (sanitizer.sanitize(result)) event.getInventory().setResult(result);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAutoCraft(CrafterCraftEvent event) {
        guarded(() -> {
            ItemStack result = event.getResult();
            if (sanitizer.sanitize(result)) event.setResult(result);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!sanitizer.active() || !(event.getWhoClicked() instanceof Player player)
            || SettingsGui.isSettingsHolder(event.getInventory().getHolder())) return;
        // Creative packets supply a new stack rather than the current server cursor.
        if (event instanceof InventoryCreativeEvent creative) {
            ItemStack cursor = creative.getCursor();
            if (sanitizer.sanitize(cursor)) creative.setCursor(cursor);
        }
        if (event.getInventory() instanceof AnvilInventory && event.getRawSlot() == 2) {
            guarded(() -> {
                ItemStack input = event.getInventory().getItem(0);
                sanitizer.sanitize(input);
                ItemStack result = event.getCurrentItem();
                sanitizer.sanitize(result);
                if (EnchantmentSanitizer.equivalentAnvilResult(input, result)) {
                    event.setCancelled(true);
                    event.setCurrentItem(null);
                } else event.setCurrentItem(result);
            });
        } else {
            ItemStack current = event.getCurrentItem();
            if (sanitizer.sanitize(current)) event.setCurrentItem(current);
        }
        // Reread the final inventory next tick, never replay a stale event stack.
        schedulePlayer(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!sanitizer.active() || !(event.getWhoClicked() instanceof Player player)
            || SettingsGui.isSettingsHolder(event.getInventory().getHolder())) return;
        boolean illegal = false;
        for (ItemStack stack : event.getNewItems().values()) illegal |= sanitizer.sanitize(stack.clone());
        if (illegal) {
            // Cancel this distribution and clean the actual cursor; a retry moves legal items.
            event.setCancelled(true);
            ItemStack cursor = event.getOldCursor().clone();
            sanitizer.sanitize(cursor);
            event.setCursor(cursor);
        }
        schedulePlayer(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        guarded(() -> {
            sanitizer.sanitizeInventory(event.getInventory());
            if (event.getInventory() instanceof MerchantInventory merchant) cleanMerchant(merchant.getMerchant());
        });
        if (event.getPlayer() instanceof Player player) schedulePlayer(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) schedulePlayer(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (!sanitizer.active()) return;
        ItemStack item = event.getItem().clone();
        if (sanitizer.sanitize(item)) {
            // setItem with changed metadata suppresses vanilla source removal and duplicates items.
            // Cancel, clean the source in place, and let the hopper retry normally.
            event.setCancelled(true);
            later(() -> guarded(() -> sanitizer.sanitizeInventory(event.getSource())));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) { guarded(() -> cleanEntity(event.getItem())); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        guarded(() -> cleanEntity(event.getItem()));
        if (event.getEntity() instanceof Player player) schedulePlayer(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) { guarded(() -> cleanEntity(event.getItem())); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) { guarded(() -> cleanEntity(event.getItemDrop())); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) { guarded(() -> cleanEntity(event.getEntity())); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        guarded(() -> {
            List<ItemStack> loot = new ArrayList<>();
            for (ItemStack item : event.getLoot()) {
                ItemStack copy = item.clone();
                sanitizer.sanitize(copy);
                loot.add(copy);
            }
            event.setLoot(loot);
        });
    }

    private MerchantRecipe cleanRecipe(MerchantRecipe recipe) {
        ItemStack result = recipe.getResult().clone();
        if (!sanitizer.sanitize(result)) return recipe;
        MerchantRecipe clean = new MerchantRecipe(result, recipe.getUses(), recipe.getMaxUses(),
            recipe.hasExperienceReward(), recipe.getVillagerExperience(), recipe.getPriceMultiplier(),
            recipe.getDemand(), recipe.getSpecialPrice(), recipe.shouldIgnoreDiscounts());
        clean.setIngredients(recipe.getIngredients());
        return clean;
    }

    private void cleanMerchant(Merchant merchant) {
        for (int i = 0; i < merchant.getRecipeCount(); i++) {
            MerchantRecipe original = merchant.getRecipe(i);
            MerchantRecipe clean = cleanRecipe(original);
            if (clean != original) merchant.setRecipe(i, clean);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPurchase(PlayerPurchaseEvent event) {
        guarded(() -> event.setTrade(cleanRecipe(event.getTrade())));
        schedulePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        guarded(() -> event.setRecipe(cleanRecipe(event.getRecipe())));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTradeSelect(TradeSelectEvent event) {
        guarded(() -> cleanMerchant(event.getInventory().getMerchant()));
        if (event.getWhoClicked() instanceof Player player) schedulePlayer(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSlot(PlayerInventorySlotChangeEvent event) {
        // A view's raw slot mapping changes when another inventory is opened.
        // Reconcile the live player inventory instead of relying on that mapping.
        cleanPlayer(event.getPlayer());
        schedulePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) { cleanPlayer(event.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) { cleanPlayer(event.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHeld(PlayerItemHeldEvent event) { cleanPlayer(event.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        cleanPlayer(event.getPlayer());
        guarded(() -> cleanEntity(event.getAttacked()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        guarded(() -> {
            cleanEntity(event.getEntity());
            if (event instanceof EntityDamageByEntityEvent attack) cleanEntity(attack.getDamager());
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) { guarded(() -> cleanEntity(event.getEntity())); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        guarded(() -> event.getDrops().forEach(sanitizer::sanitize));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (sanitizer.active()) later(() -> guarded(() -> cleanEntity(event.getEntity())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEquipment(EntityEquipmentChangedEvent event) {
        if (!cleaning && sanitizer.active()) later(() -> guarded(() -> cleanEntity(event.getEntity())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunk(ChunkLoadEvent event) { if (sanitizer.active()) enqueue(event.getChunk()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntities(EntitiesLoadEvent event) { if (sanitizer.active()) enqueue(event.getChunk()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(WorldLoadEvent event) {
        if (sanitizer.active()) for (Chunk chunk : event.getWorld().getLoadedChunks()) enqueue(chunk);
    }

    private void enqueue(Chunk chunk) { chunks.add(new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ())); }

    private void cleanNextChunk() {
        if (closed || !sanitizer.active() || chunks.isEmpty()) return;
        Iterator<ChunkKey> iterator = chunks.iterator();
        ChunkKey key = iterator.next();
        iterator.remove();
        World world = Bukkit.getWorld(key.world());
        if (world == null || !world.isChunkLoaded(key.x(), key.z())) return;
        guarded(() -> {
            Chunk chunk = world.getChunkAt(key.x(), key.z());
            for (var state : chunk.getTileEntities()) {
                // Do not generate unopened loot just to inspect storage.
                if (state instanceof Lootable loot && loot.getLootTable() != null) continue;
                if (state instanceof Container container) sanitizer.sanitizeInventory(container.getInventory());
            }
            for (Entity entity : chunk.getEntities()) cleanEntity(entity);
        });
    }

    private void cleanEntity(Entity entity) {
        if (entity instanceof Item drop) {
            ItemStack item = drop.getItemStack();
            if (sanitizer.sanitize(item)) drop.setItemStack(item);
        }
        if (entity instanceof InventoryHolder holder
            && (!(entity instanceof Lootable loot) || loot.getLootTable() == null))
            sanitizer.sanitizeInventory(holder.getInventory());
        // PlayerInventory already covers player armor/hands and rejects BODY/SADDLE slots.
        if (!(entity instanceof Player) && entity instanceof LivingEntity living && living.getEquipment() != null) {
            EntityEquipment equipment = living.getEquipment();
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack item = equipment.getItem(slot);
                if (sanitizer.sanitize(item)) equipment.setItem(slot, item);
            }
        }
        if (entity instanceof ItemFrame frame) {
            ItemStack item = frame.getItem();
            if (sanitizer.sanitize(item)) frame.setItem(item, false);
        }
    }

    @Override public void close() {
        closed = true;
        inventoryTask.cancel();
        chunkTask.cancel();
        deferredTasks.forEach(BukkitTask::cancel);
        deferredTasks.clear();
        pendingPlayers.clear();
        chunks.clear();
    }

    private record ChunkKey(UUID world, int x, int z) {}
}

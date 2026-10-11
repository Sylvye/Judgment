package com.bountysmp.judgment.enchantment;

import me.easyenchants.event.EnchantedBookApplyEvent;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.function.Supplier;

/** Enforces gear rules before EasyEnchants writes an item or consumes a book. */
public final class EasyEnchantsIntegration implements Listener {
    private final Supplier<EnchantmentSettings> settings;
    private final EnchantmentSanitizer sanitizer;

    public EasyEnchantsIntegration(Supplier<EnchantmentSettings> settings) {
        this.settings = settings;
        this.sanitizer = new EnchantmentSanitizer(settings);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBookApply(EnchantedBookApplyEvent event) {
        EnchantmentSettings rules = settings.get();
        if (!rules.active()) return;
        sanitizer.sanitize(event.getTarget());
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        rules.limits().forEach((key, limit) -> {
            var enchantment = registry.get(key);
            if (enchantment != null && limit != 0) event.setMaximumLevel(enchantment, Math.max(0, limit));
        });
    }
}

package com.bountysmp.judgment.combatitem;

import org.bukkit.Material;

import java.util.Set;

public enum CombatItemAction {
    ELYTRA("elytra", "Elytra", Material.ELYTRA, false),
    FIREWORKS("fireworks", "Fireworks", Material.FIREWORK_ROCKET, false),
    ENDER_PEARLS("ender-pearls", "Ender Pearls", Material.ENDER_PEARL, false),
    MACE_SMASH("mace-smash", "Mace Smash", Material.MACE, false),
    RIPTIDE("riptide", "Riptide", Material.TRIDENT, false),
    LUNGE("lunge", "Lunge", Material.IRON_SPEAR, false),
    FIREWORK_CROSSBOWS("firework-crossbows", "Firework Crossbows", Material.CROSSBOW, false),
    TNT("tnt", "TNT", Material.TNT, true),
    TNT_MINECARTS("tnt-minecarts", "TNT Minecarts", Material.TNT_MINECART, true),
    BEDS("beds", "Beds", Material.RED_BED, true),
    RESPAWN_ANCHORS("respawn-anchors", "Respawn Anchors", Material.RESPAWN_ANCHOR, true),
    END_CRYSTALS("end-crystals", "End Crystals", Material.END_CRYSTAL, true);

    private final String configKey;
    private final String displayName;
    private final Material icon;
    private final boolean explosive;

    CombatItemAction(String configKey, String displayName, Material icon, boolean explosive) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.icon = icon;
        this.explosive = explosive;
    }

    public String configKey() { return configKey; }
    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public boolean explosive() { return explosive; }

    public Set<Material> nativeCooldownMaterials() {
        return switch (this) {
            case MACE_SMASH -> Set.of(Material.MACE);
            case RIPTIDE -> Set.of(Material.TRIDENT);
            case FIREWORK_CROSSBOWS -> Set.of(Material.CROSSBOW);
            default -> Set.of();
        };
    }

    public boolean usesNativeCooldown() { return !nativeCooldownMaterials().isEmpty(); }

    public boolean requiresEventCooldownEnforcement() {
        return this == MACE_SMASH;
    }
}

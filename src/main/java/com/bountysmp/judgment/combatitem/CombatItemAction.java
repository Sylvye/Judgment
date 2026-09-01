package com.bountysmp.judgment.combatitem;

import org.bukkit.Material;

public enum CombatItemAction {
    ELYTRA("elytra", "Elytra", Material.ELYTRA),
    FIREWORKS("fireworks", "Fireworks", Material.FIREWORK_ROCKET),
    ENDER_PEARLS("ender-pearls", "Ender Pearls", Material.ENDER_PEARL),
    MACE_SMASH("mace-smash", "Mace Smash", Material.MACE),
    RIPTIDE("riptide", "Riptide", Material.TRIDENT),
    LUNGE("lunge", "Lunge", Material.IRON_SPEAR),
    END_CRYSTALS("end-crystals", "End Crystals", Material.END_CRYSTAL),
    RESPAWN_ANCHORS("respawn-anchors", "Respawn Anchors", Material.RESPAWN_ANCHOR),
    TNT_MINECARTS("tnt-minecarts", "TNT Minecarts", Material.TNT_MINECART);

    private final String configKey;
    private final String displayName;
    private final Material icon;

    CombatItemAction(String configKey, String displayName, Material icon) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.icon = icon;
    }

    public String configKey() { return configKey; }
    public String displayName() { return displayName; }
    public Material icon() { return icon; }
}

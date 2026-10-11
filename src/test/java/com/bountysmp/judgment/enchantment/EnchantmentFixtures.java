package com.bountysmp.judgment.enchantment;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.mockbukkit.mockbukkit.enchantments.EnchantmentMock;
import org.mockbukkit.mockbukkit.registry.RegistryMock;
import org.mockbukkit.mockbukkit.world.ChunkMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class EnchantmentFixtures {
    private EnchantmentFixtures() {}

    /** MockBukkit does not expose a public custom registry registration API. */
    @SuppressWarnings("unchecked")
    public static Enchantment register(NamespacedKey key) throws ReflectiveOperationException {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        registry.stream().count(); // Initialize MockBukkit's lazy vanilla registry first.
        var field = RegistryMock.class.getDeclaredField("keyedMap");
        field.setAccessible(true);
        var enchantment = new EnchantmentMock(key, false, false, 1, 1, key.getKey(),
            new Component[]{Component.text(key.getKey())}, new int[]{1}, new int[]{1},
            true, true, Set.of(), Set.of(), key.toString(), 1);
        ((Map<NamespacedKey, Enchantment>) field.get(registry)).put(key, enchantment);
        return enchantment;
    }

    /** Supply chunk enumeration missing from MockBukkit while retaining real mock block inventories. */
    public static final class StorageWorld extends WorldMock {
        private final Map<String, ChunkMock> chunks = new HashMap<>();
        private final Map<String, org.bukkit.block.BlockState[]> tileStates = new HashMap<>();

        @Override public ChunkMock getChunkAt(int x, int z) {
            return chunks.computeIfAbsent(x + "," + z, ignored -> new ChunkMock(this, x, z) {
                @Override public org.bukkit.block.BlockState[] getTileEntities() {
                    if (tileStates.containsKey(x + "," + z)) return tileStates.get(x + "," + z);
                    var state = getWorld().getBlockAt(x * 16, 70, z * 16).getState();
                    // MockBukkit chest loot APIs are unimplemented. Expose its inventory as a plain container.
                    if (state instanceof org.bukkit.block.Container container) {
                        state = (org.bukkit.block.Container) java.lang.reflect.Proxy.newProxyInstance(
                            org.bukkit.block.Container.class.getClassLoader(), new Class<?>[]{org.bukkit.block.Container.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("getInventory")) return container.getInventory();
                                throw new UnsupportedOperationException(method.getName());
                            });
                    }
                    return new org.bukkit.block.BlockState[]{state};
                }
                @Override public org.bukkit.entity.Entity[] getEntities() { return new org.bukkit.entity.Entity[0]; }
            });
        }
        @Override public boolean isChunkLoaded(int x, int z) { return chunks.containsKey(x + "," + z); }
        @Override public org.bukkit.Chunk[] getLoadedChunks() { return chunks.values().toArray(org.bukkit.Chunk[]::new); }
        public void forgetChunk(int x, int z) { chunks.remove(x + "," + z); }
        public void setTileEntities(int x, int z, org.bukkit.block.BlockState... states) { tileStates.put(x + "," + z, states); }
    }
}

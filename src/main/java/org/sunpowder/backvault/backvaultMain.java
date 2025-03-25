package org.sunpowder.backvault;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.*;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BackVault extends JavaPlugin implements Listener {
    private FileConfiguration config;
    private final Map<Chunk, Long> chunkAccessTimes = new ConcurrentHashMap<>();
    private final Set<Location> activeRedstoneBlocks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private int serverVersion;

    @Override
    public void onEnable() {
        this.serverVersion = detectServerVersion();
        saveDefaultConfig();
        reloadConfiguration();
        registerEventHandlers();
        startOptimizationTasks();
        setupCommands();
    }

    private int detectServerVersion() {
        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        return Integer.parseInt(version.split("_")[1]);
    }

    private void reloadConfiguration() {
        reloadConfig();
        config = getConfig();
    }

    private void registerEventHandlers() {
        getServer().getPluginManager().registerEvents(this, this);
        if(config.getBoolean("optimizations.hopper-optimization", true)) {
            getServer().getPluginManager().registerEvents(new HopperListener(), this);
        }
    }

    private void startOptimizationTasks() {
        startChunkUnloadTask();
        startEntityOptimizationTask();
        startRedstoneCleanupTask();
        startItemStackMergeTask();
        startViewDistanceAdjustmentTask();
        startTileEntityCleanupTask();
    }

    private void setupCommands() {
        getCommand("backvault").setExecutor((sender, cmd, label, args) -> {
            reloadConfiguration();
            sender.sendMessage(ChatColor.GREEN + "BackVault configuration reloaded");
            return true;
        });
    }

    private void startChunkUnloadTask() {
        new BukkitRunnable() {
            public void run() {
                optimizeChunks();
            }
        }.runTaskTimerAsynchronously(this, 6000L, 6000L);
    }

    private void startEntityOptimizationTask() {
        new BukkitRunnable() {
            public void run() {
                optimizeEntities();
            }
        }.runTaskTimer(this, 100L, 200L);
    }

    private void startRedstoneCleanupTask() {
        new BukkitRunnable() {
            public void run() {
                activeRedstoneBlocks.removeIf(loc -> loc.getWorld().getBlockAt(loc).getBlockPower() == 0);
            }
        }.runTaskTimer(this, 6000L, 6000L);
    }

    private void startItemStackMergeTask() {
        new BukkitRunnable() {
            public void run() {
                mergeGroundItems();
            }
        }.runTaskTimer(this, 600L, 600L);
    }

    private void startViewDistanceAdjustmentTask() {
        new BukkitRunnable() {
            public void run() {
                adjustViewDistance();
            }
        }.runTaskTimerAsynchronously(this, 12000L, 12000L);
    }

    private void startTileEntityCleanupTask() {
        new BukkitRunnable() {
            public void run() {
                optimizeTileEntities();
            }
        }.runTaskTimer(this, 1200L, 1200L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkLoad(ChunkLoadEvent event) {
        chunkAccessTimes.put(event.getChunk(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkUnload(ChunkUnloadEvent event) {
        chunkAccessTimes.remove(event.getChunk());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if(event.getEntityType() == EntityType.DROPPED_ITEM) return;
        if(event.getEntity().getChunk().getEntities().length > config.getInt("entity-limits.per-chunk", 25)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if(config.getBoolean("optimizations.redstone-optimization", true)) {
            activeRedstoneBlocks.add(event.getBlock().getLocation());
            if(event.getBlock().getBlockPower() > 0) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        if(config.getBoolean("optimizations.merge-items", true) && serverVersion >= 19) {
            event.getEntity().setMergeDelay(1);
        }
    }

    private void optimizeChunks() {
        long unloadDelay = config.getLong("chunk-settings.unload-delay", 300000);
        Bukkit.getWorlds().parallelStream().forEach(world -> {
            Arrays.stream(world.getLoadedChunks()).forEach(chunk -> {
                if(System.currentTimeMillis() - chunkAccessTimes.getOrDefault(chunk, System.currentTimeMillis()) > unloadDelay) {
                    chunk.unload(true);
                }
            });
        });
    }

    private void optimizeEntities() {
        Bukkit.getWorlds().forEach(world -> {
            world.getEntitiesByClass(Mob.class).forEach(mob -> {
                boolean active = !mob.getLocation().getNearbyPlayers(32).isEmpty();
                mob.setAI(active);
                if(serverVersion >= 19) mob.setAware(active);
            });
        });
    }

    private void mergeGroundItems() {
        Bukkit.getWorlds().forEach(world -> {
            world.getEntitiesByClass(Item.class).forEach(item -> {
                item.getNearbyEntities(1.5, 1.5, 1.5).stream()
                    .filter(e -> e instanceof Item)
                    .map(e -> (Item)e)
                    .filter(other -> other.getItem().isSimilar(item.getItem()))
                    .limit(5)
                    .forEach(other -> {
                        ItemStack stack = item.getItem();
                        stack.setAmount(stack.getAmount() + other.getItem().getAmount());
                        item.setItem(stack);
                        other.remove();
                    });
            });
        });
    }

    private void adjustViewDistance() {
        double tps = Bukkit.getTPS()[0];
        int base = config.getInt("view-distance.base", 6);
        Bukkit.getWorlds().forEach(world -> {
            if(tps < 17.0) world.setViewDistance(Math.max(config.getInt("view-distance.min", 4), world.getViewDistance() - 1));
            else if(tps > 19.5) world.setViewDistance(Math.min(config.getInt("view-distance.max", 8), world.getViewDistance() + 1));
            else world.setViewDistance(base);
        });
    }

    private void optimizeTileEntities() {
        int limit = config.getInt("tile-entity-limits.per-chunk", 25);
        Bukkit.getWorlds().forEach(world -> {
            Arrays.stream(world.getLoadedChunks()).forEach(chunk -> {
                if(chunk.getTileEntities().length > limit) {
                    Arrays.stream(chunk.getTileEntities()).skip(limit).forEach(te -> te.getBlock().breakNaturally());
                }
            });
        });
    }

    @Override
    public void onDisable() {
        Bukkit.getWorlds().forEach(world -> {
            world.getEntitiesByClass(Mob.class).forEach(mob -> {
                mob.setAI(true);
                if(serverVersion >= 19) mob.setAware(true);
            });
        });
    }

    private class HopperListener implements Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        public void onHopperTransfer(InventoryMoveItemEvent event) {
            if(config.getBoolean("optimizations.hopper-optimization", true)) {
                if(event.getDestination().getLocation() != null && !event.getDestination().getLocation().getChunk().isLoaded()) {
                    event.setCancelled(true);
                }
            }
        }
    }
}

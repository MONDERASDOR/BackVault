package org.sunpowder.backvault;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.world.*;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import co.aikar.timings.Timing;
import co.aikar.timings.Timings;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BackVault extends JavaPlugin implements Listener {
    private FileConfiguration config;
    private Map<Chunk, Long> chunkAccessTimes = new ConcurrentHashMap<>();
    private Set<Location> activeRedstoneBlocks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfiguration();
        setupTimings();
        registerEventHandlers();
        startOptimizationTasks();
        getCommand("backvault").setExecutor((sender, cmd, label, args) -> {
            reloadConfiguration();
            sender.sendMessage(ChatColor.GREEN + "BackVault configuration reloaded");
            return true;
        });
    }

    private void reloadConfiguration() {
        reloadConfig();
        config = getConfig();
    }

    private void setupTimings() {
        if(config.getBoolean("advanced.enable-timings", false)) {
            Timings.setTimingsEnabled(true);
            Timings.setVerboseTimingsEnabled(true);
        }
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
                activeRedstoneBlocks.removeIf(loc -> 
                    loc.getWorld().getBlockAt(loc).getBlockPower() == 0
                );
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
    public void onChunkAccess(ChunkUnloadEvent event) {
        chunkAccessTimes.remove(event.getChunk());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if(event.getEntityType() == EntityType.DROPPED_ITEM) return;
        int chunkLimit = config.getInt("entity-limits.per-chunk", 25);
        if(event.getEntity().getChunk().getEntities().length > chunkLimit) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockUpdate(BlockPhysicsEvent event) {
        if(config.getBoolean("optimizations.redstone-optimization", true)) {
            if(activeRedstoneBlocks.size() > 1000) return;
            activeRedstoneBlocks.add(event.getBlock().getLocation());
            if(event.getBlock().getBlockPower() > 0) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        if(config.getBoolean("optimizations.merge-items", true)) {
            event.getEntity().setMergeDelay(1);
        }
    }

    private void optimizeChunks() {
        long unloadDelay = config.getLong("chunk-settings.unload-delay", 300000);
        getServer().getWorlds().parallelStream().forEach(world -> {
            world.getLoadedChunks().forEach(chunk -> {
                long lastAccess = chunkAccessTimes.getOrDefault(chunk, System.currentTimeMillis());
                if(System.currentTimeMillis() - lastAccess > unloadDelay && !chunk.isForceLoaded()) {
                    chunk.unload(true);
                }
            });
        });
    }

    private void optimizeEntities() {
        getServer().getWorlds().forEach(world -> {
            world.getEntitiesByClasses(Mob.class).forEach(mob -> {
                boolean hasNearbyPlayers = !mob.getLocation().getNearbyPlayers(32).isEmpty();
                boolean disableAI = config.getBoolean("entity-settings.disable-mob-ai", true);
                if(disableAI && !hasNearbyPlayers) {
                    mob.setAI(false);
                    mob.setAware(false);
                } else {
                    mob.setAI(true);
                    mob.setAware(true);
                }
            });
        });
    }

    private void mergeGroundItems() {
        getServer().getWorlds().forEach(world -> {
            world.getEntitiesByClass(Item.class).forEach(item -> {
                item.getNearbyEntities(1.5, 1.5, 1.5).stream()
                    .filter(e -> e instanceof Item)
                    .map(e -> (Item) e)
                    .filter(other -> other.canMobPickup() && item.canMobPickup())
                    .filter(other -> other.getItem().isSimilar(item.getItem()))
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
        int baseDistance = config.getInt("view-distance.base", 6);
        int minDistance = config.getInt("view-distance.min", 4);
        int maxDistance = config.getInt("view-distance.max", 8);

        if(tps < 17.0) {
            Bukkit.getWorlds().forEach(world -> world.setViewDistance(Math.max(minDistance, world.getViewDistance() - 1)));
        } else if(tps > 19.5) {
            Bukkit.getWorlds().forEach(world -> world.setViewDistance(Math.min(maxDistance, world.getViewDistance() + 1)));
        } else {
            Bukkit.getWorlds().forEach(world -> world.setViewDistance(baseDistance));
        }
    }

    private void optimizeTileEntities() {
        int tileEntityLimit = config.getInt("tile-entity-limits.per-chunk", 25);
        getServer().getWorlds().forEach(world -> {
            world.getLoadedChunks().forEach(chunk -> {
                if(chunk.getTileEntities().length > tileEntityLimit) {
                    Arrays.stream(chunk.getTileEntities())
                        .skip(tileEntityLimit)
                        .forEach(te -> te.getBlock().breakNaturally());
                }
            });
        });
    }

    @Override
    public void onDisable() {
        getServer().getWorlds().forEach(world -> {
            world.getEntitiesByClasses(Mob.class).forEach(mob -> {
                mob.setAI(true);
                mob.setAware(true);
            });
        });
    }

    private class HopperListener implements Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        public void onHopperTransfer(InventoryMoveItemEvent event) {
            if(config.getBoolean("optimizations.hopper-optimization", true)) {
                if(event.getDestination().getLocation() != null && 
                   !event.getDestination().getLocation().getChunk().isLoaded()) {
                    event.setCancelled(true);
                }
            }
        }
    }
}

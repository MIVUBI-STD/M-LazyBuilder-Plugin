package com.halokaryamedia.lazybuilder.terraformserver;

import com.halokaryamedia.lazybuilder.terraform.BoundedShapeField;
import com.halokaryamedia.lazybuilder.terraform.ShapeBounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.function.BiConsumer;

/** Main-thread bounded world writer with tick budgeting and reversible per-player history. */
final class TerraformApplyQueue {
    private static final int BLOCKS_PER_TICK = 4096;
    private static final int HISTORY_LIMIT = 32;
    private static final int MAX_QUEUE_TOTAL = 32;
    private static final int MAX_QUEUE_PER_PLAYER = 4;
    private static final long MAX_CANDIDATES = 2_500_000L;
    private final JavaPlugin plugin;
    private final Deque<Job> queue = new ArrayDeque<>();
    private final Map<UUID, Deque<UndoRecord>> undo = new HashMap<>();
    private BukkitTask task;

    TerraformApplyQueue(JavaPlugin plugin) { this.plugin = plugin; }

    void start() { if (task == null) task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L); }
    void stop() { if (task != null) task.cancel(); task = null; queue.clear(); undo.clear(); }

    void submit(Player player, String id, BoundedShapeField field, BiConsumer<Integer, Boolean> done) {
        UUID owner=player.getUniqueId();
        if(queue.size()>=MAX_QUEUE_TOTAL)throw new IllegalStateException("terraform queue is busy");
        if(countQueued(owner)>=MAX_QUEUE_PER_PLAYER)throw new IllegalStateException("too many queued terraform operations");
        ShapeBounds b = field.bounds();
        int minX = (int)Math.floor(b.minX()), maxX = (int)Math.ceil(b.maxX());
        int minY = Math.max(player.getWorld().getMinHeight(), (int)Math.floor(b.minY()));
        int maxY = Math.min(player.getWorld().getMaxHeight() - 1, (int)Math.ceil(b.maxY()));
        int minZ = (int)Math.floor(b.minZ()), maxZ = (int)Math.ceil(b.maxZ());
        long candidates = (long)(maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1);
        if (candidates <= 0 || candidates > MAX_CANDIDATES) throw new IllegalArgumentException("terraform operation bounds too large");
        queue.addLast(new Job(owner, player.getWorld(), id, field, minX,maxX,minY,maxY,minZ,maxZ,done));
    }

    boolean submitUndo(Player player, String id, BiConsumer<Integer, Boolean> done) {
        UUID owner=player.getUniqueId();
        if(countQueued(owner)>0)return false;
        Deque<UndoRecord> history = undo.get(owner);
        if (history == null || history.isEmpty()) return false;
        UndoRecord record = history.peekLast();
        if (!record.worldUid.equals(player.getWorld().getUID())) return false;
        history.removeLast();
        queue.addFirst(Job.undo(owner, player.getWorld(), id, record.changes, done));
        return true;
    }

    void clearHistory(UUID owner){undo.remove(owner);}

    private int countQueued(UUID owner){int count=0;for(Job job:queue)if(job.owner.equals(owner))count++;return count;}

    private void tick() {
        Job job = queue.peekFirst(); if (job == null) return;
        int budget = BLOCKS_PER_TICK;
        while (budget-- > 0 && !job.finished()) job.step();
        if (job.finished()) {
            queue.removeFirst();
            Player ownerPlayer=Bukkit.getPlayer(job.owner);
            boolean ownerStillInWorld=ownerPlayer!=null&&ownerPlayer.getWorld().getUID().equals(job.world.getUID());
            if (!job.undoMode && !job.changed.isEmpty() && ownerStillInWorld) {
                Deque<UndoRecord> history = undo.computeIfAbsent(job.owner, k -> new ArrayDeque<>());
                history.addLast(new UndoRecord(job.world.getUID(), List.copyOf(job.changed)));
                while (history.size() > HISTORY_LIMIT) history.removeFirst();
            }
            job.done.accept(job.changed.size(), job.undoMode);
        }
    }

    private record Pos(int x,int y,int z) {}
    private record Change(Pos pos, String previousBlockData) {}
    private record UndoRecord(UUID worldUid, List<Change> changes) {}

    private static final class Job {
        final UUID owner; final World world; final String id; final BoundedShapeField field; final BiConsumer<Integer,Boolean> done;
        final int minX,maxX,minY,maxY,minZ,maxZ; final boolean undoMode; final List<Change> undoChanges; final List<Change> changed = new ArrayList<>();
        int x,y,z,index;
        Job(UUID owner, World world, String id, BoundedShapeField field, int minX,int maxX,int minY,int maxY,int minZ,int maxZ,BiConsumer<Integer,Boolean> done) {
            this.owner=owner;this.world=world;this.id=id;this.field=field;this.minX=minX;this.maxX=maxX;this.minY=minY;this.maxY=maxY;this.minZ=minZ;this.maxZ=maxZ;this.done=done;this.undoMode=false;this.undoChanges=null;
            x=minX;y=minY;z=minZ;
        }
        private Job(UUID owner, World world, String id, List<Change> changes, BiConsumer<Integer,Boolean> done) {
            this.owner=owner;this.world=world;this.id=id;this.field=null;this.minX=this.maxX=this.minY=this.maxY=this.minZ=this.maxZ=0;this.done=done;this.undoMode=true;this.undoChanges=changes;
        }
        static Job undo(UUID owner, World world, String id, List<Change> changes, BiConsumer<Integer,Boolean> done) { return new Job(owner,world,id,changes,done); }
        boolean finished() { return undoMode ? index >= undoChanges.size() : y > maxY; }
        void step() {
            if (undoMode) {
                Change change=undoChanges.get(index++); Pos p=change.pos(); Block b=world.getBlockAt(p.x,p.y,p.z);
                b.setBlockData(Bukkit.createBlockData(change.previousBlockData()), false); changed.add(change); return;
            }
            Block block=world.getBlockAt(x,y,z);
            if (block.getType().isAir() && field.contains(x+0.5,y+0.5,z+0.5)) {
                changed.add(new Change(new Pos(x,y,z), block.getBlockData().getAsString()));
                block.setType(Material.STONE,false);
            }
            if (++x>maxX) { x=minX; if (++z>maxZ) { z=minZ; y++; } }
        }
    }
}

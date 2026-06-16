package com.dwinovo.animus.pathing.cache;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * A {@link BlockGetter} over a {@link SectionCache} — the search-side twin of
 * {@link com.dwinovo.animus.pathing.calc.NavSnapshot} (which reads the live world). Block states come
 * from the immutable {@link CompactSection} copies (race-free, so a worker thread can read them) and
 * are memoized per search — each cell fetched once, exactly like NavSnapshot. Because it implements
 * {@link BlockGetter}, {@link com.dwinovo.animus.pathing.util.BlockHelper} reads it unchanged.
 */
public final class CachedNavView implements BlockGetter {

    private final SectionCache cache;
    private final Level level;
    private final Long2ObjectOpenHashMap<BlockState> memo = new Long2ObjectOpenHashMap<>();

    public CachedNavView(SectionCache cache, Level level) {
        this.cache = cache;
        this.level = level;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        long key = pos.asLong();
        BlockState cached = memo.get(key);
        if (cached != null) {
            return cached;
        }
        BlockState state = cache.get(pos.getX(), pos.getY(), pos.getZ());
        memo.put(key, state);
        return state;
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        // P-B runs the search on the MAIN thread, so a live lookup is safe and keeps the don't-grief
        // check (BlockHelper.shouldAvoidBreaking) exact. P-C TODO: when the search moves off-thread,
        // this must NOT touch the live level — bake a hasBlockEntity bit into CompactSection and read
        // that here instead (see docs/PATHFINDING_ASYNC.md §5.2).
        return level.getBlockEntity(pos);
    }

    @Override
    public int getHeight() {
        return level.getHeight();
    }

    @Override
    public int getMinY() {
        return level.getMinY();
    }
}

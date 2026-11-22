package com.ryouhei.Voice;




import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.common.Mod;

import java.util.PriorityQueue;


@Mod.EventBusSubscriber(modid = Main.MOD_ID)
public class SoundPathfinder {


    private static final int MAX_EXPANSIONS = 30000;

    private record Node(long pos, double g, double f) {}

    private SoundPathfinder() {}

    /** @return ルート距離。maxCost 以内に見つからなければ +∞ */
    public static double find(Level level, Vec3 start, Vec3 goal, double maxCost) {
        BlockPos s = BlockPos.containing(start);
        BlockPos t = BlockPos.containing(goal);
        if (s.equals(t)) return start.distanceTo(goal);

        long startKey = s.asLong();
        long goalKey = t.asLong();

        Long2ByteOpenHashMap passCache = new Long2ByteOpenHashMap(); // 0=未判定 1=可 2=不可
        Long2DoubleOpenHashMap gScore = new Long2DoubleOpenHashMap();
        gScore.defaultReturnValue(Double.POSITIVE_INFINITY);
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));

        gScore.put(startKey, 0.0);
        open.add(new Node(startKey, 0.0, heuristic(s, t)));

        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        int expansions = 0;

        while (!open.isEmpty()) {
            Node node = open.poll();
            if (node.g > gScore.get(node.pos)) continue;
            if (node.pos == goalKey) return node.g;
            if (++expansions > MAX_EXPANSIONS) break;

            cur.set(BlockPos.getX(node.pos), BlockPos.getY(node.pos), BlockPos.getZ(node.pos));

            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        next.set(cur.getX() + dx, cur.getY() + dy, cur.getZ() + dz);
                        long nKey = next.asLong();
                        if (nKey != goalKey && !passable(level, next, passCache)) continue;
                        if (!cornerClear(level, cur, dx, dy, dz, passCache, goalKey)) continue;

                        double ng = node.g + Math.sqrt(dx * dx + dy * dy + dz * dz);
                        double h = heuristic(next, t);
                        if (ng + h > maxCost) continue;

                        if (ng < gScore.get(nKey)) {
                            gScore.put(nKey, ng);
                            open.add(new Node(nKey, ng, ng + h));
                        }
                    }
        }
        return Double.POSITIVE_INFINITY;
    }

    /** 斜め移動で壁の角をすり抜けないようにする */
    private static boolean cornerClear(Level level, BlockPos from, int dx, int dy, int dz,
                                       Long2ByteOpenHashMap cache, long goalKey) {
        int axes = (dx != 0 ? 1 : 0) | (dy != 0 ? 2 : 0) | (dz != 0 ? 4 : 0);
        if (Integer.bitCount(axes) < 2) return true;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int mask = 1; mask < 8; mask++) {
            if ((mask & axes) != mask || mask == axes) continue;
            p.set(from.getX() + ((mask & 1) != 0 ? dx : 0),
                    from.getY() + ((mask & 2) != 0 ? dy : 0),
                    from.getZ() + ((mask & 4) != 0 ? dz : 0));
            if (p.asLong() != goalKey && !passable(level, p, cache)) return false;
        }
        return true;
    }

    private static boolean passable(Level level, BlockPos pos, Long2ByteOpenHashMap cache) {
        long key = pos.asLong();
        byte c = cache.get(key);
        if (c != 0) return c == 1;
        boolean r = computePassable(level, pos);
        cache.put(key, r ? (byte) 1 : (byte) 2);
        return r;
    }

    /** 音が通り抜けられるブロックか */
    private static boolean computePassable(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;

        Block block = state.getBlock();
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
            return state.getValue(BlockStateProperties.OPEN);
        }
        return !state.isCollisionShapeFullBlock(level, pos);
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

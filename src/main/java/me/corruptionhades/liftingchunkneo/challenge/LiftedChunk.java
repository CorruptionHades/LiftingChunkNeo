package me.corruptionhades.liftingchunkneo.challenge;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;

public class LiftedChunk {

    public final ServerSubLevel subLevel;
    public final BlockPos anchor;
    public double speed;
    public boolean hasReachedLimit;

    // bounds
    public final int minX;
    public final int maxX;
    public final int minY;
    public final int maxY;
    public final int minZ;
    public final int maxZ;

    public long lastTimePlayerNearMillis;

    public LiftedChunk(ServerSubLevel subLevel, BlockPos anchor, double speed, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        this.subLevel = subLevel;
        this.anchor = anchor;
        this.speed = speed;
        this.hasReachedLimit = false;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.lastTimePlayerNearMillis = System.currentTimeMillis();
    }
}

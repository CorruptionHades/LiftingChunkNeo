package me.corruptionhades.liftingchunkneo.challenge;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SublevelObserver {

    private final Set<ServerLevel> registered = new HashSet<>();
    private boolean ignoreNextSpawn = false;

    public void ignoreNext() {
        this.ignoreNextSpawn = true;
    }

    public void registerSplitObserver(ServerLevel level) {
        if (registered.contains(level)) return;
        registered.add(level);

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        container.addObserver(new SubLevelObserver() {
            @Override
            public void onSubLevelAdded(SubLevel subLevel) {
                if (!(subLevel instanceof ServerSubLevel serverSubLevel)) return;

                var allLiftedChunks = ChallengeManager.getInstance().getAllLiftedChunks();

                // ignore if its a lifted chunk
                for (LiftedChunk lc : allLiftedChunks) {
                    if (lc.subLevel.getUniqueId().equals(subLevel.getUniqueId())) {
                        return;
                    }
                }

                if(ignoreNextSpawn) {
                    Settings.LOG_DEBUG(level, "Ignoring next sublevel spawn: " + subLevel.getUniqueId());
                    ignoreNextSpawn = false;
                    return;
                }

                Settings.LOG_DEBUG(level, "Detected sublevel spawn: " + subLevel.getUniqueId());

                RigidBodyHandle handle = RigidBodyHandle.of(serverSubLevel);
                if (handle == null) return;

                // 0.5 - 3 magnitude
                double vertMoment = 0.5 + Math.random() * 2.5;

                // choose vertical direction based on the level
                int sign = 1;
                ServerLevel subLevelLevel = serverSubLevel.getLevel();
                if (subLevelLevel != null) {
                    MinecraftServerWrapper msw = new MinecraftServerWrapper(subLevelLevel.getServer());
                    sign = msw.getVerticalSignForLevel(subLevelLevel);
                }

                handle.addLinearAndAngularVelocity(
                        new Vector3d(0, vertMoment * sign, 0),
                        new Vector3d(
                                (Math.random() - 0.5) * 0.4,
                                (Math.random() - 0.5) * 0.4,
                                (Math.random() - 0.5) * 0.4
                        )
                );

                UUID parentId = serverSubLevel.getSplitFromSubLevel();
                LiftedChunk parentChunk = null;
                if (parentId != null) {
                    parentChunk = allLiftedChunks.stream()
                            .filter(lc -> !lc.subLevel.isRemoved() && lc.subLevel.getUniqueId().equals(parentId))
                            .findFirst()
                            .orElse(null);
                }

                int minX = serverSubLevel.logicalPose().position().z() != 0 ? (int) serverSubLevel.logicalPose().position().x() - 8 : level.getMinBuildHeight();
                int maxX = minX + 16;
                int minY = level.getMinBuildHeight();
                int maxY = 310;
                int minZ = (int) serverSubLevel.logicalPose().position().z() - 8;
                int maxZ = minZ + 16;

                if (parentChunk != null) {
                    minX = parentChunk.minX;
                    maxX = parentChunk.maxX;
                    minY = parentChunk.minY;
                    maxY = parentChunk.maxY;
                    minZ = parentChunk.minZ;
                    maxZ = parentChunk.maxZ;
                }

                double splitSpeed = (parentChunk != null ? parentChunk.speed : 0) + vertMoment * sign;
                LiftedChunk splitChunk = new LiftedChunk(
                        serverSubLevel,
                        BlockPos.containing(serverSubLevel.logicalPose().position().x(), serverSubLevel.logicalPose().position().y(), serverSubLevel.logicalPose().position().z()),
                        splitSpeed,
                        minX, maxX, minY, maxY, minZ, maxZ
                );
                if (parentChunk != null) {
                    splitChunk.hasReachedLimit = parentChunk.hasReachedLimit;
                }
                allLiftedChunks.add(splitChunk);

                Settings.LOG_DEBUG(level, "Split chunk added: " + splitChunk.subLevel.getUniqueId() + " with speed " + splitChunk.speed);
            }
        });
    }

    // small helper wrapper to avoid importing MinecraftServer directly in this file everywhere
    private static class MinecraftServerWrapper {
        private final net.minecraft.server.MinecraftServer server;
        MinecraftServerWrapper(net.minecraft.server.MinecraftServer s) { this.server = s; }

        int getVerticalSignForLevel(ServerLevel level) {
            if (level == server.overworld()) return 1;
            if (level == server.getLevel(Level.NETHER)) return -1;
            if (level == server.getLevel(Level.END)) return Math.random() < 0.5 ? 1 : -1;
            return 1;
        }
    }
}

package me.corruptionhades.liftingchunkneo.challenge;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Vector3d;

import java.util.*;

public class ChallengeManager {

    private static ChallengeManager instance;

    public static ChallengeManager getInstance() {
        if (instance == null) instance = new ChallengeManager();
        return instance;
    }

    private ChallengeState state = ChallengeState.IDLE;
    private final SublevelObserver observer = new SublevelObserver();

    private long startTimeMillis = 0;
    private long pausedTimeMillis = 0;
    private long totalPausedDuration = 0;

    private final List<LiftedChunk> allLiftedChunks = new ArrayList<>();

    private final Map<UUID, Integer> chunkTicks = new HashMap<>();
    private final Map<UUID, ChunkPos> playerChunks = new HashMap<>();

    private final Map<UUID, List<LiftedChunk>> activePlayerChunks = new HashMap<>();

    public long getElapsedMillis() {
        if (state == ChallengeState.IDLE) return 0;
        if (state == ChallengeState.ACTIVE)
            return System.currentTimeMillis() - startTimeMillis - totalPausedDuration;
        return pausedTimeMillis - startTimeMillis - totalPausedDuration;
    }

    public void start() {
        state = ChallengeState.ACTIVE;
        startTimeMillis = System.currentTimeMillis();
        totalPausedDuration = 0;
        chunkTicks.clear();
        playerChunks.clear();
        allLiftedChunks.clear();
        activePlayerChunks.clear();
    }

    public void togglePause() {
        if (state == ChallengeState.ACTIVE) {
            state = ChallengeState.PAUSED;
            pausedTimeMillis = System.currentTimeMillis();
        } else if (state == ChallengeState.PAUSED) {
            totalPausedDuration += System.currentTimeMillis() - pausedTimeMillis;
            state = ChallengeState.ACTIVE;
        }
    }

    public void stop() {
        state = ChallengeState.IDLE;
        chunkTicks.clear();
        playerChunks.clear();
        allLiftedChunks.clear();
        activePlayerChunks.clear();
    }

    //region tick

    public void tick(MinecraftServer server) {
        if (state != ChallengeState.ACTIVE) return;

        observer.registerSplitObserver(server.overworld());

        tickLiftedChunks(server);
        tickPlayers(server);
    }

    private void tickLiftedChunks(MinecraftServer server) {
        Iterator<LiftedChunk> iter = allLiftedChunks.iterator();
        while (iter.hasNext()) {
            LiftedChunk lc = iter.next();

            if (lc.subLevel.isRemoved()) {
                iter.remove();
                continue;
            }

            // no players within 128 blocks of the chunk bbox for 1min, delete it
            Vector3d currentPos = lc.subLevel.logicalPose().position();
            double translationX = currentPos.x() - lc.anchor.getX();
            double translationY = currentPos.y() - lc.anchor.getY();
            double translationZ = currentPos.z() - lc.anchor.getZ();

            double transMinX = lc.minX + translationX;
            double transMaxX = lc.maxX + translationX;
            double transMinY = lc.minY + translationY;
            double transMaxY = lc.maxY + translationY;
            double transMinZ = lc.minZ + translationZ;
            double transMaxZ = lc.maxZ + translationZ;

            boolean anyPlayerNear = false;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.isSpectator()) continue;
                double px = player.getX();
                double py = player.getY();
                double pz = player.getZ();

                double dx = Math.max(0.0, Math.max(transMinX - px, px - transMaxX));
                double dy = Math.max(0.0, Math.max(transMinY - py, py - transMaxY));
                double dz = Math.max(0.0, Math.max(transMinZ - pz, pz - transMaxZ));
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (dist <= 128.0) {
                    anyPlayerNear = true;
                    break;
                }
            }

            if (anyPlayerNear) {
                lc.lastTimePlayerNearMillis = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - lc.lastTimePlayerNearMillis >= 60000) {

                Settings.LOG_DEBUG(server.overworld(), "Deleting chunk failsafe: " + lc.subLevel.getUniqueId());

                try {
                    ServerLevel level = lc.subLevel.getLevel();
                    SubLevelContainer.getContainer(level).removeSubLevel(lc.subLevel, SubLevelRemovalReason.REMOVED);
                } catch (Exception e) {
                    Settings.LOG_DEBUG(server.overworld(), "Deleting chunk failsafe: " + lc.subLevel);
                }
                iter.remove();
                continue;
            }

            RigidBodyHandle handle = RigidBodyHandle.of(lc.subLevel);
            if (handle == null) continue;

            double currentY = lc.subLevel.logicalPose().position().y();

            if (!lc.hasReachedLimit) {
                if (currentY >= Settings.LIMIT_Y) {
                    lc.hasReachedLimit = true;
                    Vector3d vel = handle.getLinearVelocity(new Vector3d());
                    double slowSpeed = 0.5 + Math.random();
                    double diffX = -vel.x * 0.9;
                    double diffY = slowSpeed - vel.y;
                    double diffZ = -vel.z * 0.9;

                    handle.addLinearAndAngularVelocity(
                            new Vector3d(diffX, diffY, diffZ),
                            new Vector3d(
                                    0.05 + Math.random() * 0.35,
                                    0.05 + Math.random() * 0.35,
                                    0.05 + Math.random() * 0.35
                            )
                    );

                    Settings.LOG_DEBUG(server.overworld(), "§7[Debug] Chunk reached Y=320, slowed to: " + String.format("%.2f", slowSpeed) + " m/s");

                }
                else {

                    Vector3d vel = handle.getLinearVelocity(new Vector3d());
                    double error = lc.speed - vel.y;
                    if (Math.abs(error) > 0.05) {
                        handle.addLinearAndAngularVelocity(
                                new Vector3d(0, error, 0),
                                new Vector3d(0, 0, 0)
                        );
                    }
                }
            }
        }
    }

    private void tickPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isCreative()) continue;

            ServerLevel level = (ServerLevel) player.getCommandSenderWorld();

            if (player.getY() >= Settings.LIMIT_Y) {
                int newAir = player.getAirSupply() - 5;
                if (newAir < -20) {
                    newAir = 0;
                    player.hurt(player.damageSources().drown(), 2.0F);
                }
                player.setAirSupply(newAir);
            }

            ChunkPos currentPos = player.chunkPosition();
            ChunkPos lastPos = playerChunks.get(player.getUUID());

            SubLevel existing = Sable.HELPER.getContaining(level, currentPos);
            BlockPos below = player.blockPosition().below();
            boolean standingOnBlock = !level.getBlockState(below).isAir();
            boolean insideSubLevel = existing != null;

            if (insideSubLevel || !standingOnBlock) {
                chunkTicks.put(player.getUUID(), 0);
                playerChunks.put(player.getUUID(), currentPos);
            } else if (!currentPos.equals(lastPos)) {
                playerChunks.put(player.getUUID(), currentPos);
                chunkTicks.put(player.getUUID(), 0);
            } else {
                int ticks = chunkTicks.getOrDefault(player.getUUID(), 0) + 1;
                chunkTicks.put(player.getUUID(), ticks);

                if (ticks >= Settings.CHUNK_DELAY_TICKS) {
                    triggerChunkLift(player);
                    chunkTicks.put(player.getUUID(), 0);
                }
            }

            player.sendSystemMessage(makeActionbar(player), true);
        }
    }

    //endregion

    public void triggerChunkLift(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.getCommandSenderWorld();
        ChunkPos chunkPos = player.chunkPosition();

        SubLevel existing = Sable.HELPER.getContaining(level, chunkPos);
        if (existing != null) return;

        BlockPos playerPos = player.blockPosition();
        double speed = 1.0 + Math.random() * 6.0;

        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                if (x == chunkPos.getMinBlockX() || x == chunkPos.getMaxBlockX()
                        || z == chunkPos.getMinBlockZ() || z == chunkPos.getMaxBlockZ()) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, x + 0.5, playerPos.getY() + 0.5, z + 0.5, 2, 0.2, 0.5, 0.2, 0.05);
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x + 0.5, playerPos.getY() + 0.5, z + 0.5, 1, 0.2, 0.5, 0.2, 0.05);
                }
            }
        }
        level.playSound(null, playerPos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 3.0f, 0.5f);
        level.playSound(null, playerPos, SoundEvents.DEEPSLATE_HIT, SoundSource.BLOCKS, 0.5f, 1.2f);
        level.playSound(null, playerPos, SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.BLOCKS, 2.0f, 0.5f);

        Set<BlockPos> blocks = collectBlocks(level, chunkPos);
        if (blocks.isEmpty()) return;

        BoundingBox3i bounds = new BoundingBox3i(
                chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX(), 310, chunkPos.getMaxBlockZ()
        );

        try {
            observer.ignoreNext();

            ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, playerPos, blocks, bounds);

            RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
            if (handle != null) {
                handle.addLinearAndAngularVelocity(
                        new Vector3d(0, speed, 0),
                        new Vector3d(0, 0, 0)
                );
            }

            LiftedChunk lc = new LiftedChunk(
                    subLevel, playerPos, speed,
                    chunkPos.getMinBlockX(), chunkPos.getMaxBlockX(),
                    level.getMinBuildHeight(), 310,
                    chunkPos.getMinBlockZ(), chunkPos.getMaxBlockZ()
            );
            allLiftedChunks.add(lc);
            activePlayerChunks.put(player.getUUID(), List.of(lc));

            Settings.LOG_DEBUG(level, "Player " + player.getName().getString() + " lifted chunk at " + chunkPos + " with speed: " + String.format("%.2f", speed) + " m/s");
        }
        catch (Exception e) {
            Sable.LOGGER.error("LiftingChunks: failed to lift chunk at {}", chunkPos, e);
        }
    }

    private Set<BlockPos> collectBlocks(ServerLevel level, ChunkPos chunkPos) {
        Set<BlockPos> blocks = new HashSet<>();
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        LevelChunkSection[] sections = chunk.getSections();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) continue;
            int sectionY = level.getMinBuildHeight() + i * 16;

            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++)
                    for (int y = 0; y < 16; y++)
                        if (!section.getBlockState(x, y, z).isAir())
                            blocks.add(new BlockPos(
                                    chunkPos.getMinBlockX() + x,
                                    sectionY + y,
                                    chunkPos.getMinBlockZ() + z
                            ));
        }
        return blocks;
    }

    private Component makeActionbar(ServerPlayer player) {
        long elapsed = getElapsedMillis();
        long seconds = elapsed / 1000;
        String timeStr = String.format("%02d:%02d", seconds / 60, seconds % 60);

        int standing = chunkTicks.getOrDefault(player.getUUID(), 0);
        float progress = Math.min(1.0f, standing / (float) Settings.CHUNK_DELAY_TICKS);
        int pct = (int) (progress * 100);

        StringBuilder bar = new StringBuilder("§7[");
        int filled = (int) (progress * 20);
        for (int i = 0; i < 20; i++) {
            if (i < filled) bar.append(progress >= 1.0f ? "§c█" : progress >= 0.7f ? "§6█" : "§e█");
            else bar.append("§8░");
        }
        bar.append("§7]");

        return Component.literal(timeStr + "  " + bar + " " + pct + "%");
    }

    public void applyForceToNearestChunk(ServerPlayer player, double force) {
        if (allLiftedChunks.isEmpty()) {
            player.displayClientMessage(Component.literal("§cNo lifted chunks."), false);
            return;
        }

        BlockPos pp = player.blockPosition();
        allLiftedChunks.stream()
                .filter(lc -> !lc.subLevel.isRemoved())
                .min(Comparator.comparingDouble(lc -> lc.anchor.distSqr(pp)))
                .ifPresent(lc -> {
                    lc.hasReachedLimit = false;
                    lc.speed = force;
                    player.displayClientMessage(
                            Component.literal("§aForced chunk to ascend at " + force + " m/s"), false);
                });
    }


    //region getters

    public ChallengeState getState() { return state; }
    public boolean isDebugEnabled() { return Settings.DEBUG; }
    public void toggleDebug() { Settings.DEBUG = !Settings.DEBUG; }

    public List<LiftedChunk> getAllLiftedChunks() {
        return allLiftedChunks;
    }

    //endregion
}

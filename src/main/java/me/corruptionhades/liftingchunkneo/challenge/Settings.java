package me.corruptionhades.liftingchunkneo.challenge;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class Settings {

    public static final double LIMIT_Y = 280.0;
    public static final int CHUNK_DELAY_TICKS = 3 * 20;

    public static boolean DEBUG = false;

    public static void LOG_DEBUG(ServerLevel level, String msg) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
    }
}

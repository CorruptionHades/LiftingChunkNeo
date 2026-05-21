package me.corruptionhades.liftingchunkneo;

import me.corruptionhades.liftingchunkneo.challenge.ChallengeCommand;
import me.corruptionhades.liftingchunkneo.challenge.ChallengeManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import static com.mojang.text2speech.Narrator.LOGGER;

@EventBusSubscriber(modid = Liftingchunkneo.MODID, bus = EventBusSubscriber.Bus.GAME)
public class EventListener {

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        ChallengeCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void registerTickEvent(ServerTickEvent.Pre event) {
        ChallengeManager.getInstance().tick(event.getServer());
    }

    @SubscribeEvent
    public static void registerServerStoppingEvent(ServerStoppingEvent event) {
        ChallengeManager.getInstance().stop();
    }
}

package com.ryouhei.Voice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Main.MOD_ID, value = Dist.CLIENT)
public final class ClientPathCache {

    public record Entry(double pathDistance, double straightDistance, long time) {}

    private static final int UPDATE_INTERVAL = 10; // tick
    private static final long MAX_AGE = 40;        // tick

    private static final Map<UUID, Entry> CACHE = new ConcurrentHashMap<>();
    private static volatile boolean active = false;
    private static volatile long now = 0;

    /** 探索の上限距離。受信した音声の距離で随時更新する */
    public static volatile double searchRange = 48.0;

    private ClientPathCache() {}

    public static boolean isActive() {
        return active;
    }

    public static Entry get(UUID speaker) {
        Entry e = CACHE.get(speaker);
        if (e == null || now - e.time() > MAX_AGE) return null;
        return e;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        if (self == null || mc.level == null || mc.gameMode == null) {
            active = false;
            CACHE.clear();
            return;
        }

        GameType mode = mc.gameMode.getPlayerMode();
        active = mode == GameType.SURVIVAL || mode == GameType.ADVENTURE;
        if (!active) {
            CACHE.clear();
            return;
        }

        now++;
        if (now % UPDATE_INTERVAL != 0) return;

        double range = searchRange;
        Vec3 selfEye = self.getEyePosition();

        for (AbstractClientPlayer other : mc.level.players()) {
            if (other == self) continue;
            Vec3 otherEye = other.getEyePosition();
            double straight = selfEye.distanceTo(otherEye);
            if (straight > range) continue;

            double path = SoundPathfinder.find(mc.level, selfEye, otherEye, range);
            CACHE.put(other.getUUID(), new Entry(path, straight, now));
        }

        long limit = now - MAX_AGE;
        CACHE.values().removeIf(e -> e.time() < limit);
    }
}

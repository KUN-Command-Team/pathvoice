package com.ryouhei.Voice;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ForgeVoicechatPlugin
public class PathVoiceClientPlugin implements VoicechatPlugin {

    /** 直前のゲイン（音量の急変によるプチノイズ防止用） */
    private final Map<UUID, Float> lastGain = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return Main.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // 近接ボイスはプレイヤーに追従する EntitySound として届く
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, this::onEntitySound);
    }

    private void onEntitySound(ClientReceiveSoundEvent.EntitySound event) {
        UUID speaker = event.getId();
        float range = event.getDistance();
        if (range > ClientPathCache.searchRange) {
            ClientPathCache.searchRange = range;
        }

        if (!ClientPathCache.isActive()) {
            lastGain.remove(speaker);
            return;
        }

        ClientPathCache.Entry entry = ClientPathCache.get(speaker);
        if (entry == null) return; // まだ未計算 → そのまま再生

        short[] audio = event.getRawAudio();
        if (audio == null || audio.length == 0) return;

        float target = computeGain(entry, range);
        float start = lastGain.getOrDefault(speaker, target);
        lastGain.put(speaker, target);

        if (start >= 0.999F && target >= 0.999F) return; // 変化なし

        short[] out = new short[audio.length];
        int len = audio.length;
        for (int i = 0; i < len; i++) {
            float g = start + (target - start) * ((i + 1) / (float) len); // フレーム内で滑らかに変化
            int v = Math.round(audio[i] * g);
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
        }
        event.setRawAudio(out);
    }

    /**
     * SVC は直線距離で減衰させるので、その分を打ち消してルート距離での減衰に置き換える。
     * 減衰カーブは線形（1 - d/range）として近似。
     */
    private static float computeGain(ClientPathCache.Entry e, float range) {
        double path = e.pathDistance();
        if (!Double.isFinite(path) || path >= range) return 0F;

        double straight = Math.min(e.straightDistance(), path);
        double straightVol = 1.0 - straight / range;
        double pathVol = 1.0 - path / range;
        if (straightVol <= 1.0E-4) return 0F;

        return (float) Math.max(0.0, Math.min(1.0, pathVol / straightVol));
    }
}

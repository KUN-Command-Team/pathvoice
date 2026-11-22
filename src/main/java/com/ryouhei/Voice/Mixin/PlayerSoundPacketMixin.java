package com.ryouhei.Voice.Mixin;




import com.ryouhei.Voice.ClientPathCache;
import com.ryouhei.Voice.Main;
import de.maxhenkel.voicechat.voice.client.AudioChannel;
import de.maxhenkel.voicechat.voice.client.ClientVoicechat;
import de.maxhenkel.voicechat.voice.client.InitializationData;
import de.maxhenkel.voicechat.voice.common.PlayerSoundPacket;
import de.maxhenkel.voicechat.voice.common.SoundPacket;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;


@Mixin(value = PlayerSoundPacket.class, remap = false)
public abstract class PlayerSoundPacketMixin {

    @Shadow
    public abstract UUID getSender();

    /** 無音扱いにするときの半径（0 だと SVC 側で割り算が起きる可能性があるので避ける） */
    private static final float MIN_RADIUS = 0.01F;

    @Inject(method = "getDistance", at = @At("RETURN"), cancellable = true)
    private void pathvoice$replaceRadius(CallbackInfoReturnable<Float> cir) {
        if (!ClientPathCache.isActive()) return;

        UUID sender = getSender();
        if (sender == null) return;

        ClientPathCache.Entry entry = ClientPathCache.get(sender);
        if (entry == null) return; // 未計算の間は元の半径のまま

        float radius = cir.getReturnValueF();
        if (radius > ClientPathCache.searchRange) {
            ClientPathCache.searchRange = radius;
        }

        double path = entry.pathDistance();
        if (!Double.isFinite(path) || path >= radius) {
            cir.setReturnValue(MIN_RADIUS); // ルートで届かない → 無音
            return;
        }

        double straight = Math.min(entry.straightDistance(), path);
        if (path <= 1.0E-3) return;

        // R' = R × straight / path
        float newRadius = (float) (radius * straight / path);
        cir.setReturnValue(Math.max(MIN_RADIUS, newRadius));
    }
}




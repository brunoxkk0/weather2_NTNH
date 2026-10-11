package weather2.mixins;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.C03PacketPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import weather2.util.WeatherUtilEntity;

@Mixin(NetHandlerPlayServer.class)
public abstract class NetHandlerPlayServerMixin {

    @Shadow
    private int floatingTickCount;

    @Shadow
    public EntityPlayerMP playerEntity;

    @Inject(method = "processPlayer", at = @At("HEAD"))
    private void weather2$resetFloatingTicksForTornadoPull(C03PacketPlayer packet, CallbackInfo callbackInfo) {
        if (WeatherUtilEntity.shouldIgnoreTornadoFlightCheck(this.playerEntity)) {
            this.floatingTickCount = 0;
        }
    }
}

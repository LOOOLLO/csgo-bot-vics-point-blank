package com.example.csgocompat.client;

import com.example.csgocompat.CsGoCompatMod;
import com.example.csgocompat.client.hud.CsgoHudOverlay;
import com.example.csgocompat.client.renderer.CTEntityRenderer;
import com.example.csgocompat.client.renderer.TEntityRenderer;
import com.example.csgocompat.network.KillFeedPayload;
import com.example.csgocompat.network.MatchStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class CsGoCompatModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(CsGoCompatMod.COUNTER_TERRORIST, CTEntityRenderer::new);
        EntityRendererRegistry.register(CsGoCompatMod.TERRORIST, TEntityRenderer::new);
        CsgoHudOverlay.register();

        ClientPlayNetworking.registerGlobalReceiver(MatchStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientMatchState.apply(payload)));

        ClientPlayNetworking.registerGlobalReceiver(KillFeedPayload.TYPE, (payload, context) ->
                context.client().execute(() -> KillFeed.add(payload)));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientMatchState.clear();
            KillFeed.clear();
        });
    }
}

package com.example.csgocompat.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class CsgoNetworking {

    /** Ogni quanti tick il server rimanda lo stato del match ai client. */
    public static final int SYNC_INTERVAL_TICKS = 10;

    private CsgoNetworking() {
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(MatchStatePayload.TYPE, MatchStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(KillFeedPayload.TYPE, KillFeedPayload.CODEC);
    }

    public static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (player == null || payload == null) return;
        try {
            ServerPlayNetworking.send(player, payload);
        } catch (Throwable ignored) {
            // Il giocatore può disconnettersi fra il tick e l'invio.
        }
    }

    public static void broadcast(net.minecraft.server.MinecraftServer server,
                                 net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (server == null || payload == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, payload);
        }
    }
}

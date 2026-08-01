package com.example.csgocompat.network;

import com.example.csgocompat.CsGoCompatMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Stato del match inviato dal server al client per l'HUD.
 *
 * <p>Prima l'HUD leggeva direttamente i campi statici lato server: funzionava solo in singleplayer,
 * dove client e server condividono la stessa JVM. Su server dedicato punteggio e timer restavano a
 * zero.
 */
public record MatchStatePayload(
        byte phase,
        int roundTimeTicks,
        int scoreT,
        int scoreCT,
        int aliveT,
        int aliveCT,
        boolean bombPlanted,
        int bombTicksRemaining,
        byte team,
        int roundsToWin,
        int bombX,
        int bombY,
        int bombZ
) implements CustomPacketPayload {

    public static final byte PHASE_IDLE = 0;
    public static final byte PHASE_WARMUP = 1;
    public static final byte PHASE_LIVE = 2;
    public static final byte PHASE_ROUND_END = 3;
    public static final byte PHASE_FREEZE = 4;

    public static final byte TEAM_NEUTRAL = 0;
    public static final byte TEAM_T = 1;
    public static final byte TEAM_CT = 2;

    public static final CustomPacketPayload.Type<MatchStatePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CsGoCompatMod.MOD_ID, "match_state"));

    public static final StreamCodec<FriendlyByteBuf, MatchStatePayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.phase);
                buf.writeVarInt(payload.roundTimeTicks);
                buf.writeVarInt(payload.scoreT);
                buf.writeVarInt(payload.scoreCT);
                buf.writeVarInt(payload.aliveT);
                buf.writeVarInt(payload.aliveCT);
                buf.writeBoolean(payload.bombPlanted);
                buf.writeVarInt(payload.bombTicksRemaining);
                buf.writeByte(payload.team);
                buf.writeVarInt(payload.roundsToWin);
                buf.writeInt(payload.bombX);
                buf.writeInt(payload.bombY);
                buf.writeInt(payload.bombZ);
            },
            buf -> new MatchStatePayload(
                    buf.readByte(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readByte(),
                    buf.readVarInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static byte teamId(String team) {
        if ("T".equals(team)) return TEAM_T;
        if ("CT".equals(team)) return TEAM_CT;
        return TEAM_NEUTRAL;
    }
}

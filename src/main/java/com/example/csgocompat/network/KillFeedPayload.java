package com.example.csgocompat.network;

import com.example.csgocompat.CsGoCompatMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Una riga del kill feed. */
public record KillFeedPayload(
        String attacker,
        String victim,
        String weapon,
        boolean headshot,
        byte attackerTeam,
        byte victimTeam
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<KillFeedPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CsGoCompatMod.MOD_ID, "kill_feed"));

    public static final StreamCodec<FriendlyByteBuf, KillFeedPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.attacker, 64);
                buf.writeUtf(payload.victim, 64);
                buf.writeUtf(payload.weapon, 64);
                buf.writeBoolean(payload.headshot);
                buf.writeByte(payload.attackerTeam);
                buf.writeByte(payload.victimTeam);
            },
            buf -> new KillFeedPayload(
                    buf.readUtf(64),
                    buf.readUtf(64),
                    buf.readUtf(64),
                    buf.readBoolean(),
                    buf.readByte(),
                    buf.readByte()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.example.csgocompat.client.hud;

import com.example.csgocompat.client.ClientMatchState;
import com.example.csgocompat.client.KillFeed;
import com.example.csgocompat.network.KillFeedPayload;
import com.example.csgocompat.network.MatchStatePayload;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * HUD del match. Legge esclusivamente {@link ClientMatchState}, alimentato dai pacchetti del server:
 * la versione precedente leggeva i campi statici lato server e quindi mostrava 0-0 con il timer
 * fermo su qualsiasi server dedicato.
 */
public class CsgoHudOverlay {

    public static void register() {
        HudRenderCallback.EVENT.register(CsgoHudOverlay::render);
    }

    private static String formatTime(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (!ClientMatchState.shouldRenderHud()) return;

        MatchStatePayload state = ClientMatchState.get();
        if (state == null) return;

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int centerX = screenWidth / 2;

        // 1. Barra superiore
        int barTop = 6;
        int barBottom = 30;
        int barLeft = centerX - 110;
        int barRight = centerX + 110;

        guiGraphics.fill(barLeft, barTop, barRight, barBottom, 0xCC111116);
        guiGraphics.fill(barLeft - 1, barTop - 1, barRight + 1, barTop, 0xFF2A2A32);
        guiGraphics.fill(barLeft - 1, barBottom, barRight + 1, barBottom + 1, 0xFF2A2A32);
        guiGraphics.fill(barLeft, barTop, centerX - 1, barTop + 2, 0xFFDE3636);
        guiGraphics.fill(centerX + 1, barTop, barRight, barTop + 2, 0xFF368BDE);

        // 2. Punteggio e giocatori vivi
        String tText = "§c§lT §f" + state.scoreT() + " §7(" + state.aliveT() + ")";
        String ctText = "§b§lCT §f" + state.scoreCT() + " §7(" + state.aliveCT() + ")";
        guiGraphics.drawString(client.font, tText, centerX - 102, 14, 0xFFFFFFFF, false);
        guiGraphics.drawString(client.font, ctText, centerX + 18, 14, 0xFFFFFFFF, false);

        // 3. Timer centrale
        String centerLabel = switch (state.phase()) {
            case MatchStatePayload.PHASE_WARMUP -> "§e§l" + formatTime(state.roundTimeTicks());
            case MatchStatePayload.PHASE_FREEZE -> "§b§l" + formatTime(state.roundTimeTicks());
            case MatchStatePayload.PHASE_ROUND_END -> "§7§l--:--";
            default -> "§f§l" + formatTime(state.roundTimeTicks());
        };
        guiGraphics.drawCenteredString(client.font, centerLabel, centerX, 14, 0xFFFFFFFF);

        int nextY = 34;

        if (state.phase() == MatchStatePayload.PHASE_WARMUP) {
            guiGraphics.fill(centerX - 60, nextY, centerX + 60, nextY + 14, 0xDD806000);
            guiGraphics.drawCenteredString(client.font, "§f§lWARMUP", centerX, nextY + 3, 0xFFFFFFFF);
            nextY += 18;
        } else if (state.phase() == MatchStatePayload.PHASE_FREEZE) {
            guiGraphics.fill(centerX - 60, nextY, centerX + 60, nextY + 14, 0xDD1A5C80);
            guiGraphics.drawCenteredString(client.font, "§f§lFREEZE TIME", centerX, nextY + 3, 0xFFFFFFFF);
            nextY += 18;
        }

        // 4. Avviso bomba con countdown reale
        if (state.bombPlanted()) {
            guiGraphics.fill(centerX - 90, nextY, centerX + 90, nextY + 14, 0xDD990000);
            String bombLabel = state.bombTicksRemaining() > 0
                    ? "§f§l⚠ BOMB PLANTED §e" + (state.bombTicksRemaining() / 20) + "s §f§l⚠"
                    : "§f§l⚠ BOMB HAS BEEN PLANTED ⚠";
            guiGraphics.drawCenteredString(client.font, bombLabel, centerX, nextY + 3, 0xFFFFFFFF);
            nextY += 18;

            renderBombCompass(guiGraphics, client, state, centerX, nextY);
        }

        // 5. Badge della squadra
        if (state.team() == MatchStatePayload.TEAM_T) {
            guiGraphics.fill(8, 8, 120, 24, 0xAA220000);
            guiGraphics.drawString(client.font, "§c§l[ TEAM: TERRORIST ]", 12, 12, 0xFFFFFFFF, false);
        } else if (state.team() == MatchStatePayload.TEAM_CT) {
            guiGraphics.fill(8, 8, 140, 24, 0xAA002244);
            guiGraphics.drawString(client.font, "§b§l[ TEAM: COUNTER-TERRORIST ]", 12, 12, 0xFFFFFFFF, false);
        }

        renderKillFeed(guiGraphics, client, screenWidth);

        // 6. Suggerimento spettatore
        if (client.player.isSpectator() && state.team() != MatchStatePayload.TEAM_NEUTRAL) {
            guiGraphics.drawCenteredString(client.font,
                    "§7/csgo spec §f= next teammate   §7/csgo spec free §f= free camera",
                    centerX, client.getWindow().getGuiScaledHeight() - 40, 0xFFFFFFFF);
        }
    }

    /** Bussola verso la C4: freccia relativa alla direzione di sguardo + distanza. */
    private static void renderBombCompass(GuiGraphics guiGraphics, Minecraft client,
                                          MatchStatePayload state, int centerX, int y) {
        if (state.bombX() == 0 && state.bombY() == 0 && state.bombZ() == 0) return;

        double dx = (state.bombX() + 0.5) - client.player.getX();
        double dz = (state.bombZ() + 0.5) - client.player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        double bombYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = Mth.wrapDegrees(bombYaw - client.player.getYRot());

        String arrow;
        if (relative > -22.5 && relative <= 22.5) arrow = "▲";
        else if (relative > 22.5 && relative <= 67.5) arrow = "◤";
        else if (relative > 67.5 && relative <= 112.5) arrow = "◀";
        else if (relative > 112.5 && relative <= 157.5) arrow = "◣";
        else if (relative > -67.5 && relative <= -22.5) arrow = "◥";
        else if (relative > -112.5 && relative <= -67.5) arrow = "▶";
        else if (relative > -157.5 && relative <= -112.5) arrow = "◢";
        else arrow = "▼";

        guiGraphics.drawCenteredString(client.font,
                "§c" + arrow + " §fC4 §7" + (int) distance + "m",
                centerX, y + 2, 0xFFFFFFFF);
    }

    private static void renderKillFeed(GuiGraphics guiGraphics, Minecraft client, int screenWidth) {
        int y = 6;
        for (KillFeed.Entry entry : KillFeed.visible()) {
            KillFeedPayload kill = entry.payload();

            String attacker = teamColor(kill.attackerTeam()) + kill.attacker();
            String victim = teamColor(kill.victimTeam()) + kill.victim();
            String weapon = kill.weapon().isEmpty() ? "" : " §7[" + kill.weapon() + "]";
            String marker = kill.headshot() ? " §c§l✖" : " §7✖";

            String line = attacker + marker + weapon + " §7» " + victim;
            int width = client.font.width(line);
            guiGraphics.fill(screenWidth - width - 12, y - 2, screenWidth - 4, y + 10, 0x99000000);
            guiGraphics.drawString(client.font, line, screenWidth - width - 8, y, 0xFFFFFFFF, false);
            y += 14;
        }
    }

    private static String teamColor(byte team) {
        return switch (team) {
            case MatchStatePayload.TEAM_T -> "§c";
            case MatchStatePayload.TEAM_CT -> "§b";
            default -> "§7";
        };
    }
}

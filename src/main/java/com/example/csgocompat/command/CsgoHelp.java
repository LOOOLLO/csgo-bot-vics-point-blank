package com.example.csgocompat.command;

import com.example.csgocompat.manager.ArenaState;
import com.example.csgocompat.manager.SiteRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Testi di aiuto e messaggio di benvenuto. Tutto l'output è in inglese.
 */
public final class CsgoHelp {

    private CsgoHelp() {
    }

    private static void line(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    /** {@code /csgo help} — elenco completo; le voci da admin compaiono solo a chi ha i permessi. */
    public static int sendHelp(CommandSourceStack source, boolean admin) {
        line(source, "§6§l━━━━━━━━━━ §f§lCS:GO §6§l━━━━━━━━━━");
        line(source, "§7Team deathmatch with bomb sites, C4 plant/defuse and bots.");
        line(source, "");
        line(source, "§e§lQUICK START");
        line(source, "§7 1. §fPlace a §bBomb Site §fblock flush with the floor.");
        line(source, "§7 2. §fTake the §bBomb Site Wand§f: click the site block, then two");
        line(source, "§7    opposite corners to define the plantable area.");
        line(source, "§7 3. §f/csgo match spawnT ~ ~ ~ §7(stand on the T spawn)");
        line(source, "§7 4. §f/csgo match spawnCT ~ ~ ~ §7(stand on the CT spawn)");
        line(source, "§7 5. §f/csgo join T §7or §fCT§7, then §f/csgo start");
        line(source, "");
        line(source, "§e§lEVERYONE");
        line(source, "§f /csgo join <T|CT> §7— pick a team (gives weapon, armour, ammo)");
        line(source, "§f /csgo spec §7— follow the next living teammate while dead");
        line(source, "§f /csgo spec free §7— detach to the free camera");
        line(source, "§f /csgo status §7— phase, score, spawns, sites");
        line(source, "§f /csgo site list §7— list every registered bomb site");
        line(source, "§f /csgo site show §7— outline the site areas with particles");
        line(source, "§f /csgo help §7— this page");

        if (!admin) {
            line(source, "");
            line(source, "§8Admin commands are hidden (they need permission level 2).");
            return 1;
        }

        line(source, "");
        line(source, "§e§lMATCH §8(admin)");
        line(source, "§f /csgo start [T|CT] §7— start the match (joins the team first)");
        line(source, "§f /csgo nextround §7— force the next round");
        line(source, "§f /csgo skipwarmup §7— end the warmup immediately");
        line(source, "§f /csgo reset §7— wipe scores, teams, bots and live C4s");
        line(source, "§f /csgo match difficulty <easy|normal|hard|unfair>");
        line(source, "§f /csgo match spawnT <x y z> §7/ §fspawnCT <x y z>");
        line(source, "§f /csgo numberofT <0-20> §7/ §fnumberofCT <0-20>");
        line(source, "");
        line(source, "§e§lBOMB SITES §8(admin)");
        line(source, "§f /csgo site name <name> §7— rename the selected site");
        line(source, "§f /csgo site pos1 <x y z> §7/ §fpos2 <x y z> §7— set the area by command");
        line(source, "§f /csgo site clear §7— remove every registered site");
        line(source, "");
        line(source, "§e§lMAPS & CONFIG §8(admin)");
        line(source, "§f /csgo map save <name> §7— store spawns, bots and sites");
        line(source, "§f /csgo map load <name> §7/ §fdelete <name> §7/ §flist");
        line(source, "§f /csgo config show §7/ §freload §7/ §fsave");
        line(source, "§8Config file: <world>/csgo_mc/config.json");
        return 1;
    }

    /**
     * Messaggio inviato all'ingresso. Se l'arena non è ancora configurata mostra i passi minimi,
     * altrimenti una sola riga per non intasare la chat ad ogni join.
     */
    public static void sendWelcome(ServerPlayer player) {
        boolean ready = ArenaState.isReady() && !SiteRegistry.isEmpty();

        if (ready) {
            send(player, "§6§l[CS:GO] §fReady to play — §f/csgo join T §7or §fCT§7, then §f/csgo start§7. "
                    + "Type §f/csgo help §7for all commands.");
            return;
        }

        send(player, "§6§l━━━━━━ §f§lCS:GO §7— setup needed §6§l━━━━━━");
        send(player, "§7 1. §fPlace a §bBomb Site §fblock flush with the floor.");
        send(player, "§7 2. §fUse the §bBomb Site Wand§f: click the site block, then two opposite corners.");
        send(player, "§7 3. §f/csgo match spawnT ~ ~ ~ §7and §f/csgo match spawnCT ~ ~ ~");
        send(player, "§7 4. §f/csgo join T §7or §fCT§7, then §f/csgo start");
        send(player, "§7Full command list: §f/csgo help");
    }

    private static void send(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), false);
    }
}

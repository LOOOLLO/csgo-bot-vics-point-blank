package com.example.csgocompat.command;

import com.example.csgocompat.config.CsgoConfig;
import com.example.csgocompat.item.BombSiteWandItem;
import com.example.csgocompat.manager.ArenaState;
import com.example.csgocompat.manager.CsgoMatchState;
import com.example.csgocompat.manager.MapStorage;
import com.example.csgocompat.manager.SiteRegistry;
import com.example.csgocompat.manager.SiteVisualizer;
import com.example.csgocompat.manager.SpectatorManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.example.csgocompat.block.entity.BombSiteBlockEntity;

import java.util.Locale;

/**
 * Comandi della mod.
 *
 * <p>Ogni sottocomando amministrativo richiede il livello permessi 2: prima l'intero albero era
 * aperto a chiunque, quindi qualsiasi giocatore poteva resettare la partita o spawnare 40 bot.
 * Tutti i messaggi rivolti al giocatore sono in inglese.
 */
public class CsgoMatchCommand {

    /** In 1.21.11 i livelli numerici sono stati sostituiti dai Permission set: questo è l'ex livello 2. */
    private static boolean isAdmin(CommandSourceStack src) {
        return src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static final SuggestionProvider<CommandSourceStack> MAP_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(MapStorage.list(ctx.getSource().getServer()), builder);

    private static final SuggestionProvider<CommandSourceStack> TEAM_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(new String[]{"T", "CT"}, builder);

    private static final SuggestionProvider<CommandSourceStack> DIFFICULTY_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(new String[]{"easy", "normal", "hard", "unfair"}, builder);

    // Compatibilità con il vecchio codice che leggeva questi getter.
    public static BlockPos getSpawnT() {
        return ArenaState.getSpawnT();
    }

    public static BlockPos getSpawnCT() {
        return ArenaState.getSpawnCT();
    }

    public static int getNumberOfT() {
        return ArenaState.getBotsT();
    }

    public static int getNumberOfCT() {
        return ArenaState.getBotsCT();
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("csgo")

                .executes(ctx -> CsgoHelp.sendHelp(ctx.getSource(), isAdmin(ctx.getSource())))

                .then(Commands.literal("help")
                        .executes(ctx -> CsgoHelp.sendHelp(ctx.getSource(), isAdmin(ctx.getSource()))))

                .then(Commands.literal("start")
                        .requires(CsgoMatchCommand::isAdmin)
                        .executes(ctx -> startMatch(ctx, null))
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests(TEAM_SUGGESTIONS)
                                .executes(ctx -> startMatch(ctx, StringArgumentType.getString(ctx, "team")))))

                .then(Commands.literal("nextround")
                        .requires(CsgoMatchCommand::isAdmin)
                        .executes(CsgoMatchCommand::nextRound))

                .then(Commands.literal("skipwarmup")
                        .requires(CsgoMatchCommand::isAdmin)
                        .executes(ctx -> {
                            CsgoMatchState.skipWarmup(ctx.getSource().getServer());
                            return 1;
                        }))

                .then(Commands.literal("reset")
                        .requires(CsgoMatchCommand::isAdmin)
                        .executes(CsgoMatchCommand::resetMatch))

                // Aperti a tutti i giocatori.
                .then(Commands.literal("join")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests(TEAM_SUGGESTIONS)
                                .executes(ctx -> joinTeam(ctx, StringArgumentType.getString(ctx, "team")))))

                .then(Commands.literal("spec")
                        .executes(CsgoMatchCommand::spectateNext)
                        .then(Commands.literal("next").executes(CsgoMatchCommand::spectateNext))
                        .then(Commands.literal("free").executes(CsgoMatchCommand::spectateFree)))

                .then(Commands.literal("status")
                        .executes(CsgoMatchCommand::status))

                .then(Commands.literal("match")
                        .requires(CsgoMatchCommand::isAdmin)
                        .then(Commands.literal("difficulty")
                                .then(Commands.argument("level", StringArgumentType.word())
                                        .suggests(DIFFICULTY_SUGGESTIONS)
                                        .executes(ctx -> setDifficulty(ctx, StringArgumentType.getString(ctx, "level")))))
                        .then(Commands.literal("spawnT")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> setSpawn(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"), true))))
                        .then(Commands.literal("spawnCT")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> setSpawn(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"), false)))))

                .then(Commands.literal("numberofT")
                        .requires(CsgoMatchCommand::isAdmin)
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 20))
                                .executes(ctx -> setBotCount(ctx, IntegerArgumentType.getInteger(ctx, "count"), true))))

                .then(Commands.literal("numberofCT")
                        .requires(CsgoMatchCommand::isAdmin)
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 20))
                                .executes(ctx -> setBotCount(ctx, IntegerArgumentType.getInteger(ctx, "count"), false))))

                .then(Commands.literal("site")
                        .then(Commands.literal("list").executes(CsgoMatchCommand::listSites))
                        .then(Commands.literal("show").executes(CsgoMatchCommand::showSites))
                        .then(Commands.literal("name")
                                .requires(CsgoMatchCommand::isAdmin)
                                .then(Commands.argument("siteName", StringArgumentType.word())
                                        .executes(ctx -> setName(ctx, StringArgumentType.getString(ctx, "siteName")))))
                        .then(Commands.literal("pos1")
                                .requires(CsgoMatchCommand::isAdmin)
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> setCorner(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"), true))))
                        .then(Commands.literal("pos2")
                                .requires(CsgoMatchCommand::isAdmin)
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> setCorner(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"), false))))
                        .then(Commands.literal("clear")
                                .requires(CsgoMatchCommand::isAdmin)
                                .executes(CsgoMatchCommand::clearSites)))

                .then(Commands.literal("map")
                        .requires(CsgoMatchCommand::isAdmin)
                        .then(Commands.literal("save")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> saveMap(ctx, StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("load")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(MAP_SUGGESTIONS)
                                        .executes(ctx -> loadMap(ctx, StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(MAP_SUGGESTIONS)
                                        .executes(ctx -> deleteMap(ctx, StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("list").executes(CsgoMatchCommand::listMaps)))

                .then(Commands.literal("config")
                        .requires(CsgoMatchCommand::isAdmin)
                        .then(Commands.literal("reload").executes(CsgoMatchCommand::reloadConfig))
                        .then(Commands.literal("save").executes(CsgoMatchCommand::saveConfig))
                        .then(Commands.literal("show").executes(CsgoMatchCommand::showConfig)))
        );
    }

    // ------------------------------------------------------------------ match

    private static int startMatch(CommandContext<CommandSourceStack> ctx, String team) {
        ServerPlayer player = ctx.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        if (team != null && player != null) {
            CsgoMatchState.joinTeam(player, team);
        }
        return CsgoMatchState.startMatch(ctx.getSource().getServer(), player) ? 1 : 0;
    }

    private static int nextRound(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        return CsgoMatchState.startNextRound(ctx.getSource().getServer(), player) ? 1 : 0;
    }

    private static int resetMatch(CommandContext<CommandSourceStack> ctx) {
        CsgoMatchState.resetMatch(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6[CS:GO] Match reset — scores, teams, bots and live C4s cleared."), true);
        return 1;
    }

    private static int joinTeam(CommandContext<CommandSourceStack> ctx, String team) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            return CsgoMatchState.joinTeam(player, team) ? 1 : 0;
        }
        ctx.getSource().sendFailure(Component.literal("§c[CS:GO] Only a player can join a team."));
        return 0;
    }

    private static int spectateNext(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            return SpectatorManager.cycle(player) ? 1 : 0;
        }
        return 0;
    }

    private static int spectateFree(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            SpectatorManager.detach(player);
            player.displayClientMessage(Component.literal("§7[Spec] Free camera."), true);
            return 1;
        }
        return 0;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("§6§l[CS:GO] Status"), false);
        source.sendSuccess(() -> Component.literal("§7 Phase: §f" + CsgoMatchState.phase
                + " §7| Round: §f" + CsgoMatchState.getRoundNumber()
                + " §7| Score: §c" + CsgoMatchState.scoreT + " §7- §b" + CsgoMatchState.scoreCT), false);
        source.sendSuccess(() -> Component.literal("§7 Difficulty: §f" + CsgoMatchState.currentDifficulty
                + " §7| Bots: §c" + ArenaState.getBotsT() + "T §7/ §b" + ArenaState.getBotsCT() + "CT"), false);
        source.sendSuccess(() -> Component.literal("§7 Map: §f"
                + (ArenaState.getLoadedMapName() == null ? "(unsaved)" : ArenaState.getLoadedMapName())
                + " §7| Sites: §f" + SiteRegistry.size()), false);
        source.sendSuccess(() -> Component.literal("§7 Spawn T: §f" + describe(ArenaState.getSpawnT())
                + " §7| Spawn CT: §f" + describe(ArenaState.getSpawnCT())), false);
        return 1;
    }

    private static String describe(BlockPos pos) {
        return pos == null ? "not set" : pos.toShortString();
    }

    // ------------------------------------------------------------------ impostazioni

    private static int setDifficulty(CommandContext<CommandSourceStack> ctx, String level) {
        CsgoMatchState.MatchDifficulty difficulty;
        try {
            difficulty = CsgoMatchState.MatchDifficulty.valueOf(level.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("§c[CS:GO] Invalid difficulty! Options: easy, normal, hard, unfair"));
            return 0;
        }

        CsgoMatchState.currentDifficulty = difficulty;
        CsgoConfig.DifficultySettings settings = CsgoMatchState.difficultySettings();
        MapStorage.saveActive(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("§6[CS:GO] Difficulty: §f" + difficulty
                + " §7(" + (int) settings.health + " HP, reaction " + settings.reactionTicksMin + "-"
                + settings.reactionTicksMax + " ticks, damage x" + settings.damageMultiplier + ")"), true);
        return 1;
    }

    private static int setSpawn(CommandContext<CommandSourceStack> ctx, BlockPos pos, boolean terrorist) {
        if (terrorist) ArenaState.setSpawnT(pos);
        else ArenaState.setSpawnCT(pos);
        MapStorage.saveActive(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal((terrorist ? "§c[CS:GO] T spawn" : "§b[CS:GO] CT spawn")
                + " set to " + pos.toShortString()), true);
        return 1;
    }

    private static int setBotCount(CommandContext<CommandSourceStack> ctx, int count, boolean terrorist) {
        if (terrorist) ArenaState.setBotsT(count);
        else ArenaState.setBotsCT(count);
        MapStorage.saveActive(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal((terrorist ? "§c[CS:GO] T bots" : "§b[CS:GO] CT bots")
                + ": " + count), true);
        return 1;
    }

    // ------------------------------------------------------------------ site

    public static int listSites(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (SiteRegistry.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    "§c[CS:GO Site] No bomb site registered! Use the Bomb Site Wand or /csgo site pos1/pos2."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "§6§l[CS:GO Bomb Sites] §e(" + SiteRegistry.size() + " registered)"), false);

        int index = 1;
        for (SiteRegistry.Site site : SiteRegistry.all()) {
            final int i = index++;
            final String area = site.hasArea()
                    ? site.corner1.toShortString() + " → " + site.corner2.toShortString()
                    : "§7area not defined";
            source.sendSuccess(() -> Component.literal("§e " + i + ". §bSite " + site.name
                    + " §7@ §f" + site.pos.toShortString() + " §7(" + area + "§7)"), false);
        }
        return SiteRegistry.size();
    }

    /** Evidenzia le aree dei Bomb Site con un contorno di particelle. */
    private static int showSites(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("§c[Site] This command is for players only."));
            return 0;
        }
        if (SiteRegistry.isEmpty()) {
            player.displayClientMessage(Component.literal("§c[Site] No bomb site registered."), false);
            return 0;
        }
        int ticks = Math.max(20, CsgoConfig.get().siteOutlineDurationTicks);
        SiteVisualizer.showFor(player, ticks);
        player.displayClientMessage(Component.literal(
                "§b[Site] Outlining " + SiteRegistry.size() + " bomb site(s) for "
                        + (ticks / 20) + "s."), false);
        return 1;
    }

    private static int setName(CommandContext<CommandSourceStack> ctx, String name) {
        SiteRegistry.Site site = getActiveSite(ctx);
        if (site == null) return 0;
        site.name = name;
        syncBlockEntity(ctx, site);
        MapStorage.saveActive(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("§b[Site] Name set to: " + name), false);
        return 1;
    }

    private static int setCorner(CommandContext<CommandSourceStack> ctx, BlockPos pos, boolean first) {
        SiteRegistry.Site site = getActiveSite(ctx);
        if (site == null) return 0;
        if (first) site.corner1 = pos;
        else site.corner2 = pos;
        syncBlockEntity(ctx, site);
        MapStorage.saveActive(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§b[Site] Pos " + (first ? "1" : "2") + " set to: " + pos.toShortString()), false);
        return 1;
    }

    private static int clearSites(CommandContext<CommandSourceStack> ctx) {
        int count = SiteRegistry.size();
        SiteRegistry.clear();
        MapStorage.saveActive(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§c[Site] Removed all " + count + " registered bomb site(s)."), true);
        return count;
    }

    private static SiteRegistry.Site getActiveSite(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof Player player)) {
            ctx.getSource().sendFailure(Component.literal("§c[Site] This command is for players only."));
            return null;
        }
        BombSiteWandItem.WandSession session = BombSiteWandItem.SESSIONS.get(player.getUUID());
        if (session == null || session.activeSite == null) {
            player.displayClientMessage(Component.literal(
                    "§c[Site] Select a bomb site with the wand first!"), false);
            return null;
        }
        SiteRegistry.Site site = SiteRegistry.byPos(session.activeSite);
        if (site == null) {
            player.displayClientMessage(Component.literal("§c[Site] The selected bomb site is not valid!"), false);
        }
        return site;
    }

    private static void syncBlockEntity(CommandContext<CommandSourceStack> ctx, SiteRegistry.Site site) {
        BlockEntity be = ctx.getSource().getLevel().getBlockEntity(site.pos);
        if (be instanceof BombSiteBlockEntity siteEntity) {
            siteEntity.syncFromRegistry();
        }
    }

    // ------------------------------------------------------------------ mappe

    private static int saveMap(CommandContext<CommandSourceStack> ctx, String name) {
        MinecraftServer server = ctx.getSource().getServer();
        if (MapStorage.save(server, name)) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§a[CS:GO Map] Map §f" + name + " §asaved (spawns, bots, "
                            + SiteRegistry.size() + " site(s))."), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§c[CS:GO Map] Invalid name or write error."));
        return 0;
    }

    private static int loadMap(CommandContext<CommandSourceStack> ctx, String name) {
        MinecraftServer server = ctx.getSource().getServer();
        if (MapStorage.load(server, name)) {
            MapStorage.saveActive(server);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§a[CS:GO Map] Map §f" + name + " §aloaded: "
                            + SiteRegistry.size() + " site(s), spawns restored."), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§c[CS:GO Map] Map §f" + name + " §cnot found."));
        return 0;
    }

    private static int deleteMap(CommandContext<CommandSourceStack> ctx, String name) {
        if (MapStorage.delete(ctx.getSource().getServer(), name)) {
            ctx.getSource().sendSuccess(() -> Component.literal("§c[CS:GO Map] Map §f" + name + " §cdeleted."), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§c[CS:GO Map] Map not found."));
        return 0;
    }

    private static int listMaps(CommandContext<CommandSourceStack> ctx) {
        var maps = MapStorage.list(ctx.getSource().getServer());
        if (maps.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§7[CS:GO Map] No saved maps. Use §f/csgo map save <name>"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6§l[CS:GO Map] §e" + maps.size() + " map(s): §f" + String.join("§7, §f", maps)), false);
        return maps.size();
    }

    // ------------------------------------------------------------------ config

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        CsgoConfig.load(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("§a[CS:GO] config.json reloaded."), true);
        return 1;
    }

    private static int saveConfig(CommandContext<CommandSourceStack> ctx) {
        CsgoConfig.save(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("§a[CS:GO] config.json written to disk."), true);
        return 1;
    }

    private static int showConfig(CommandContext<CommandSourceStack> ctx) {
        CsgoConfig cfg = CsgoConfig.get();
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("§6§l[CS:GO] Config §7(<world>/csgo_mc/config.json)"), false);
        source.sendSuccess(() -> Component.literal("§7 Round: §f" + cfg.roundTimeSeconds + "s §7| Bomb: §f"
                + cfg.bombTimerSeconds + "s §7| First to §f" + cfg.roundsToWin + " §7rounds"), false);
        source.sendSuccess(() -> Component.literal("§7 Warmup: §f" + (cfg.warmupEnabled ? cfg.warmupSeconds + "s" : "off")
                + " §7| Half-time: §f" + (cfg.halfTimeRound > 0 ? "round " + cfg.halfTimeRound : "off")), false);
        source.sendSuccess(() -> Component.literal("§7 Base damage: §f" + cfg.fallbackWeaponDamage
                + " §7| Headshot x§f" + cfg.headshotMultiplier
                + " §7| Friendly fire: §f" + cfg.friendlyFire), false);
        return 1;
    }
}

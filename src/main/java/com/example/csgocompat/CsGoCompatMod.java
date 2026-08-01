package com.example.csgocompat;

import com.example.csgocompat.block.BombSiteBlock;
import com.example.csgocompat.block.C4BombBlock;
import com.example.csgocompat.block.entity.BombSiteBlockEntity;
import com.example.csgocompat.block.entity.C4BombBlockEntity;
import com.example.csgocompat.command.CsgoHelp;
import com.example.csgocompat.command.CsgoMatchCommand;
import com.example.csgocompat.config.CsgoConfig;
import com.example.csgocompat.entity.CounterTerroristEntity;
import com.example.csgocompat.entity.TerroristEntity;
import com.example.csgocompat.item.ModItems;
import com.example.csgocompat.manager.CsgoMatchState;
import com.example.csgocompat.manager.MapStorage;
import com.example.csgocompat.manager.PlantingManager;
import com.example.csgocompat.manager.SiteVisualizer;
import com.example.csgocompat.manager.SpectatorManager;
import com.example.csgocompat.network.CsgoNetworking;
import com.example.csgocompat.util.ModSounds;
import com.example.csgocompat.util.RegistryHelper;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class CsGoCompatMod implements ModInitializer {

    public static final String MOD_ID = "csgo_mc";

    public static Block BOMB_SITE;
    public static Block C4_BOMB;

    public static BlockEntityType<C4BombBlockEntity> C4_BOMB_ENTITY_TYPE;
    public static BlockEntityType<BombSiteBlockEntity> BOMB_SITE_ENTITY_TYPE;

    public static EntityType<CounterTerroristEntity> COUNTER_TERRORIST;
    public static EntityType<TerroristEntity> TERRORIST;

    @Override
    public void onInitialize() {
        registerContent();
        registerEvents();
    }

    private void registerContent() {
        ResourceKey<Block> bombSiteKey = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "bomb_site"));
        BlockBehaviour.Properties bombSiteProps = BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3.0f);
        RegistryHelper.setBlockId(bombSiteProps, bombSiteKey);
        BOMB_SITE = Registry.register(BuiltInRegistries.BLOCK, bombSiteKey, new BombSiteBlock(bombSiteProps));

        ResourceKey<Block> c4BombKey = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, "c4_bomb"));
        BlockBehaviour.Properties c4BombProps = BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5f).noOcclusion();
        RegistryHelper.setBlockId(c4BombProps, c4BombKey);
        C4_BOMB = Registry.register(BuiltInRegistries.BLOCK, c4BombKey, new C4BombBlock(c4BombProps));

        BOMB_SITE_ENTITY_TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "bomb_site"),
                RegistryHelper.createBlockEntityType(BombSiteBlockEntity::new, BOMB_SITE));

        C4_BOMB_ENTITY_TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(MOD_ID, "c4_bomb"),
                RegistryHelper.createBlockEntityType(C4BombBlockEntity::new, C4_BOMB));

        ModItems.registerItems();
        ModSounds.register();
        CsgoNetworking.registerPayloads();

        ResourceKey<CreativeModeTab> csgoTabKey = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath(MOD_ID, "csgo_tab"));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, csgoTabKey,
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(ModItems.C4_BOMB_BLOCK_ITEM))
                        .title(Component.translatable("itemGroup.csgo_mc.csgo_tab"))
                        .displayItems((params, output) -> {
                            output.accept(ModItems.CT_HELMET);
                            output.accept(ModItems.CT_CHESTPLATE);
                            output.accept(ModItems.CT_LEGGINGS);
                            output.accept(ModItems.CT_BOOTS);
                            output.accept(ModItems.T_HELMET);
                            output.accept(ModItems.T_CHESTPLATE);
                            output.accept(ModItems.T_LEGGINGS);
                            output.accept(ModItems.T_BOOTS);
                            output.accept(ModItems.DEFUSAL_KIT);
                            output.accept(ModItems.C4_BOMB_BLOCK_ITEM);
                            output.accept(ModItems.BOMB_SITE_BLOCK_ITEM);
                            output.accept(ModItems.BOMB_SITE_WAND);
                        })
                        .build());

        Identifier ctLocation = Identifier.fromNamespaceAndPath(MOD_ID, "ct");
        ResourceKey<EntityType<?>> ctKey = ResourceKey.create(Registries.ENTITY_TYPE, ctLocation);
        COUNTER_TERRORIST = Registry.register(BuiltInRegistries.ENTITY_TYPE, ctLocation,
                RegistryHelper.buildEntityType(
                        EntityType.Builder.of(CounterTerroristEntity::new, MobCategory.MONSTER).sized(0.6f, 1.95f),
                        ctKey));

        Identifier tLocation = Identifier.fromNamespaceAndPath(MOD_ID, "t");
        ResourceKey<EntityType<?>> tKey = ResourceKey.create(Registries.ENTITY_TYPE, tLocation);
        TERRORIST = Registry.register(BuiltInRegistries.ENTITY_TYPE, tLocation,
                RegistryHelper.buildEntityType(
                        EntityType.Builder.of(TerroristEntity::new, MobCategory.MONSTER).sized(0.6f, 1.95f),
                        tKey));

        FabricDefaultAttributeRegistry.register(COUNTER_TERRORIST, CounterTerroristEntity.createCTAttributes());
        FabricDefaultAttributeRegistry.register(TERRORIST, TerroristEntity.createTAttributes());
    }

    private void registerEvents() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            CsgoMatchCommand.register(dispatcher);
        });

        // Config e arena vengono caricati dal mondo, così spawn e site sopravvivono al riavvio.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CsgoConfig.load(server);
            MapStorage.loadActive(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            MapStorage.saveActive(server);
            CsgoConfig.save(server);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            CsgoMatchState.tickServer(server);
            PlantingManager.tick(server);
            SiteVisualizer.tick(server);
            if (server.getTickCount() % 10 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                        SpectatorManager.tick(player);
                    }
                }
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            CsgoMatchState.syncToClients(server);
            CsgoHelp.sendWelcome(handler.getPlayer());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // Senza questo le mappe di stato per giocatore crescevano all'infinito.
            java.util.UUID uuid = handler.getPlayer().getUUID();
            CsgoMatchState.onPlayerDisconnect(uuid);
            SpectatorManager.onPlayerDisconnect(uuid);
            PlantingManager.onPlayerDisconnect(uuid);
            SiteVisualizer.onPlayerDisconnect(uuid);
            com.example.csgocompat.item.BombSiteWandItem.clearSession(uuid);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                CsgoMatchState.onPlayerDeath(player);
            }
            CsgoMatchState.reportKill(entity, source);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                CsgoMatchState.onPlayerRespawn(newPlayer));

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            Entity attacker = source.getEntity();
            if (attacker == null) {
                return true;
            }

            String attackerTeam = getTeam(attacker);
            String defenderTeam = getTeam(entity);

            if (!CsgoConfig.get().friendlyFire
                    && !attackerTeam.equals("NEUTRAL")
                    && attackerTeam.equals(defenderTeam)) {
                return false;
            }

            if (defenderTeam.equals("CT") && entity.level() instanceof ServerLevel sLevel) {
                if (entity instanceof Mob mob) {
                    CounterTerroristEntity.triggerTeamCombatAlert(sLevel, mob);
                } else if (entity instanceof Player) {
                    for (CounterTerroristEntity ct : sLevel.getEntitiesOfClass(CounterTerroristEntity.class,
                            entity.getBoundingBox().inflate(40.0))) {
                        ct.ctCombatAlertTimer = 60;
                    }
                }
            }

            return true;
        });
    }

    public static String getTeam(Entity entity) {
        if (entity instanceof CounterTerroristEntity) return "CT";
        if (entity instanceof TerroristEntity) return "T";
        if (entity instanceof Player player) return CsgoMatchState.resolveTeam(player);
        return "NEUTRAL";
    }

    /** Squadra del giocatore: prima quella assegnata dal match, poi quella dedotta dall'armatura. */
    public static String getPlayerTeam(Player player) {
        return CsgoMatchState.resolveTeam(player);
    }
}

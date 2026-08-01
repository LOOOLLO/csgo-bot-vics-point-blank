package com.example.csgocompat.item;

import com.example.csgocompat.CsGoCompatMod;
import com.example.csgocompat.util.RegistryHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class ModItems {
    public static Item CT_HELMET;
    public static Item CT_CHESTPLATE;
    public static Item CT_LEGGINGS;
    public static Item CT_BOOTS;

    public static Item T_HELMET;
    public static Item T_CHESTPLATE;
    public static Item T_LEGGINGS;
    public static Item T_BOOTS;

    public static Item DEFUSAL_KIT;
    public static Item BOMB_SITE_WAND;

    public static Item BOMB_SITE_BLOCK_ITEM;
    public static Item C4_BOMB_BLOCK_ITEM;

    private static Item.Properties createProperties(String name) {
        Item.Properties props = new Item.Properties();
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("csgo_mc", name));
        RegistryHelper.setItemId(props, key);
        return props;
    }

    private static Item.Properties createArmorProperties(String name, EquipmentSlot slot, String teamAsset) {
        Item.Properties props = new Item.Properties().stacksTo(1);
        net.minecraft.world.item.equipment.Equippable equippable = net.minecraft.world.item.equipment.Equippable.builder(slot)
                .setAsset(ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("csgo_mc", teamAsset)))
                .build();
        props.component(net.minecraft.core.component.DataComponents.EQUIPPABLE, equippable);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("csgo_mc", name));
        RegistryHelper.setItemId(props, key);
        return props;
    }

    public static void registerItems() {
        CT_HELMET = register("ct_helmet", new TeamArmorItem(EquipmentSlot.HEAD, createArmorProperties("ct_helmet", EquipmentSlot.HEAD, "ct"), "CT"));
        CT_CHESTPLATE = register("ct_chestplate", new TeamArmorItem(EquipmentSlot.CHEST, createArmorProperties("ct_chestplate", EquipmentSlot.CHEST, "ct"), "CT"));
        CT_LEGGINGS = register("ct_leggings", new TeamArmorItem(EquipmentSlot.LEGS, createArmorProperties("ct_leggings", EquipmentSlot.LEGS, "ct"), "CT"));
        CT_BOOTS = register("ct_boots", new TeamArmorItem(EquipmentSlot.FEET, createArmorProperties("ct_boots", EquipmentSlot.FEET, "ct"), "CT"));

        T_HELMET = register("t_helmet", new TeamArmorItem(EquipmentSlot.HEAD, createArmorProperties("t_helmet", EquipmentSlot.HEAD, "t"), "T"));
        T_CHESTPLATE = register("t_chestplate", new TeamArmorItem(EquipmentSlot.CHEST, createArmorProperties("t_chestplate", EquipmentSlot.CHEST, "t"), "T"));
        T_LEGGINGS = register("t_leggings", new TeamArmorItem(EquipmentSlot.LEGS, createArmorProperties("t_leggings", EquipmentSlot.LEGS, "t"), "T"));
        T_BOOTS = register("t_boots", new TeamArmorItem(EquipmentSlot.FEET, createArmorProperties("t_boots", EquipmentSlot.FEET, "t"), "T"));

        DEFUSAL_KIT = register("defusal_kit", new DefusalKitItem(createProperties("defusal_kit").stacksTo(1)));
        BOMB_SITE_WAND = register("bomb_site_wand", new BombSiteWandItem(createProperties("bomb_site_wand").stacksTo(1)));

        BOMB_SITE_BLOCK_ITEM = register("bomb_site", new BlockItem(CsGoCompatMod.BOMB_SITE, createProperties("bomb_site")));
        C4_BOMB_BLOCK_ITEM = register("c4_bomb", new C4BombItem(CsGoCompatMod.C4_BOMB, createProperties("c4_bomb").stacksTo(1)));
    }

    private static Item register(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("csgo_mc", name)), item);
    }
}

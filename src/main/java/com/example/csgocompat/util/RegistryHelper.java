package com.example.csgocompat.util;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Helper di registrazione. Le firme sono tipizzate: la versione precedente usava generici grezzi con
 * cast unchecked, e con una chiave del tipo sbagliato l'{@code instanceof} falliva in silenzio
 * lasciando la proprietà senza id.
 */
public final class RegistryHelper {

    private RegistryHelper() {
    }

    public static BlockBehaviour.Properties setBlockId(BlockBehaviour.Properties properties, ResourceKey<Block> key) {
        return properties.setId(key);
    }

    public static Item.Properties setItemId(Item.Properties properties, ResourceKey<Item> key) {
        return properties.setId(key);
    }

    public static <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(
            FabricBlockEntityTypeBuilder.Factory<T> factory, Block block) {
        return FabricBlockEntityTypeBuilder.create(factory, block).build();
    }

    public static <T extends Entity> EntityType<T> buildEntityType(
            EntityType.Builder<T> builder, ResourceKey<EntityType<?>> key) {
        return builder.build(key);
    }
}

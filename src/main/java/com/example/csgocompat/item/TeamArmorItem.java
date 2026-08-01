package com.example.csgocompat.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.animatable.manager.AnimatableManager;

public class TeamArmorItem extends Item implements GeoItem {
    private final EquipmentSlot slot;
    private final String team;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TeamArmorItem(EquipmentSlot slot, Properties settings, String team) {
        super(settings);
        this.slot = slot;
        this.team = team;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack currentArmor = player.getItemBySlot(this.slot);

        if (!level.isClientSide()) {
            player.setItemSlot(this.slot, held.copy());
            player.setItemInHand(hand, currentArmor.copy());
        }

        return InteractionResult.SUCCESS;
    }

    public EquipmentSlot getEquipmentSlot() {
        return this.slot;
    }

    public String getTeam() {
        return team;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void createGeoRenderer(java.util.function.Consumer<software.bernie.geckolib.animatable.client.GeoRenderProvider> consumer) {
        // Geckolib 1.21.11 typically handles rendering via getRenderProvider(), so we can use a supplier pattern to hide client classes
    }

    private final java.util.function.Supplier<Object> renderProvider = new java.util.function.Supplier<Object>() {
        @Override
        public Object get() {
            return new software.bernie.geckolib.animatable.client.GeoRenderProvider() {
                private com.example.csgocompat.client.renderer.TeamArmorRenderer renderer;

                @Override
                public software.bernie.geckolib.renderer.GeoArmorRenderer<?, ?> getGeoArmorRenderer(net.minecraft.world.item.ItemStack itemStack, net.minecraft.world.entity.EquipmentSlot equipmentSlot) {
                    if (this.renderer == null) {
                        String piece = switch (TeamArmorItem.this.getEquipmentSlot()) {
                            case HEAD -> "helmet";
                            case CHEST -> "chestplate";
                            case LEGS -> "leggings";
                            case FEET -> "boots";
                            default -> "helmet";
                        };
                        this.renderer = new com.example.csgocompat.client.renderer.TeamArmorRenderer(TeamArmorItem.this.getTeam().toLowerCase(), piece);
                    }
                    return (software.bernie.geckolib.renderer.GeoArmorRenderer<?, ?>) (Object) this.renderer;
                }
            };
        }
    };

    @Override
    public Object getRenderProvider() {
        return this.renderProvider.get();
    }
}

package com.example.csgocompat.item;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import net.minecraft.world.item.Item;

/**
 * Defusal kit animato con GeckoLib: si apre quando viene impugnato.
 *
 * <p>L'animazione è {@code open} in
 * {@code assets/csgo_mc/geckolib/animations/defusal_kit.animation.json} e viene riprodotta una volta
 * sola ({@code thenPlayAndHold}), così il kit resta aperto finché lo tieni in mano.
 */
public class DefusalKitItem extends Item implements GeoItem {

    private static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("open");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public DefusalKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<DefusalKitItem>("open_controller", 0,
                state -> state.setAndContinue(OPEN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void createGeoRenderer(java.util.function.Consumer<software.bernie.geckolib.animatable.client.GeoRenderProvider> consumer) {
        // Il renderer viene fornito da getRenderProvider(), come per TeamArmorItem: così le classi
        // client non vengono toccate lato server.
    }

    private final java.util.function.Supplier<Object> renderProvider = new java.util.function.Supplier<>() {
        @Override
        public Object get() {
            return new software.bernie.geckolib.animatable.client.GeoRenderProvider() {
                private com.example.csgocompat.client.renderer.DefusalKitRenderer renderer;

                @Override
                public software.bernie.geckolib.renderer.GeoItemRenderer<?> getGeoItemRenderer() {
                    if (this.renderer == null) {
                        this.renderer = new com.example.csgocompat.client.renderer.DefusalKitRenderer();
                    }
                    return this.renderer;
                }
            };
        }
    };

    @Override
    public Object getRenderProvider() {
        return this.renderProvider.get();
    }
}

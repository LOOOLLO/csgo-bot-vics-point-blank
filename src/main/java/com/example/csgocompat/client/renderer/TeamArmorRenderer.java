package com.example.csgocompat.client.renderer;

import com.example.csgocompat.item.TeamArmorItem;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

@SuppressWarnings({"rawtypes", "unchecked"})
public class TeamArmorRenderer extends GeoArmorRenderer {
    public TeamArmorRenderer(String team, String piece) {
        super(new TeamArmorModel(team, piece));
    }

    @Override
    public String getBoneNameForSegment(HumanoidRenderState state, GeoArmorRenderer.ArmorSegment segment) {
        return switch (segment) {
            case HEAD -> "helmet";
            case CHEST -> "body";
            case RIGHT_ARM -> "arm_right";
            case LEFT_ARM -> "arm_left";
            case RIGHT_LEG -> "leg_right";
            case LEFT_LEG -> "leg_left";
            case RIGHT_FOOT -> "boot_right";
            case LEFT_FOOT -> "boot_left";
            default -> super.getBoneNameForSegment(state, segment);
        };
    }
}

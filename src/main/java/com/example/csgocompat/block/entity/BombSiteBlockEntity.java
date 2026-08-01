package com.example.csgocompat.block.entity;

import com.example.csgocompat.CsGoCompatMod;
import com.example.csgocompat.manager.SiteRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Dati dell'area di un Bomb Site.
 *
 * <p>Il registro autorevole è {@link SiteRegistry}: qui teniamo solo la copia persistita nel chunk e
 * la risincronizziamo al caricamento. La deregistrazione avviene in
 * {@code BombSiteBlock#affectNeighborsAfterRemoval}, non in {@code setRemoved}, che scatta anche
 * allo scaricamento del chunk.
 */
public class BombSiteBlockEntity extends BlockEntity {

    public BlockPos pos1 = null;
    public BlockPos pos2 = null;
    public String siteName = "";

    public BombSiteBlockEntity(BlockPos pos, BlockState state) {
        super(CsGoCompatMod.BOMB_SITE_ENTITY_TYPE, pos, state);
    }

    private static BlockPos parsePos(String value) {
        if (value == null || value.isEmpty()) return null;
        String[] parts = value.split(",");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException e) {
            // Un dato corrotto non deve impedire il caricamento del chunk.
            return null;
        }
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** Copia lo stato di questo blocco nel registro globale. */
    public void syncToRegistry() {
        SiteRegistry.Site site = SiteRegistry.register(this.worldPosition);
        if (this.siteName != null && !this.siteName.isEmpty()) {
            site.name = this.siteName;
        } else {
            this.siteName = site.name;
        }
        if (this.pos1 != null) site.corner1 = this.pos1;
        if (this.pos2 != null) site.corner2 = this.pos2;
    }

    /** Copia lo stato dal registro (usato quando una mappa viene caricata da file). */
    public void syncFromRegistry() {
        SiteRegistry.Site site = SiteRegistry.byPos(this.worldPosition);
        if (site == null) return;
        this.siteName = site.name;
        this.pos1 = site.corner1;
        this.pos2 = site.corner2;
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.pos1 = parsePos(input.getStringOr("Pos1", ""));
        this.pos2 = parsePos(input.getStringOr("Pos2", ""));
        this.siteName = input.getStringOr("SiteName", "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.pos1 != null) output.putString("Pos1", formatPos(this.pos1));
        if (this.pos2 != null) output.putString("Pos2", formatPos(this.pos2));
        output.putString("SiteName", this.siteName == null ? "" : this.siteName);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level != null && !level.isClientSide()) {
            syncToRegistry();
        }
    }
}

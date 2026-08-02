package dev.flare.msb.client.render;

import dev.flare.msb.MultiSidedBlocks;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * The model referenced by the blockstate file. Baking it produces the
 * {@link MultiSidedBakedModel} that renders the per-face textures.
 */
public class MultiSidedUnbakedModel implements UnbakedModel {

	private static final ResourceLocation BASE_TEXTURE = MultiSidedBlocks.id("block/multi_sided_base");
	private static final Material BASE_MATERIAL = new Material(TextureAtlas.LOCATION_BLOCKS, BASE_TEXTURE);

	@Override
	public Collection<ResourceLocation> getDependencies() {
		return List.of();
	}

	@Override
	public void resolveParents(Function<ResourceLocation, UnbakedModel> resolver) {
	}

	@Override
	public BakedModel bake(ModelBaker modelBaker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState transform, ResourceLocation location) {
		TextureAtlasSprite baseSprite = spriteGetter.apply(BASE_MATERIAL);
		return new MultiSidedBakedModel(baseSprite);
	}
}

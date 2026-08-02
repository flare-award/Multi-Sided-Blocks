package dev.flare.msb.client;

import dev.flare.msb.MultiSidedBlocks;
import dev.flare.msb.client.render.MultiSidedBlockEntityRenderer;
import dev.flare.msb.client.render.MultiSidedUnbakedModel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererFactories;
import net.minecraft.resources.ResourceLocation;

public class MultiSidedBlocksClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// The block in the world is drawn by its block entity renderer, which reads the
		// per-face data every frame (works with vanilla, Sodium and Iris, no Indium needed).
		BlockEntityRendererFactories.register(MultiSidedBlocks.MULTI_SIDED_BLOCK_ENTITY, MultiSidedBlockEntityRenderer::new);

		// The blockstate references "multi-sided-blocks:block/multi_sided" and the item
		// references "multi-sided-blocks:item/multi_sided_block". Instead of shipping a
		// static model JSON for either, we provide a fully dynamic unbaked model that
		// bakes into our per-face quad generator.
		//
		// IMPORTANT: in 1.20.1 a JSON model must NOT use a custom (non-BlockModel)
		// model as its "parent". BlockModel.resolveParents throws
		// "BlockModel parent has to be a block model." during the very first resource
		// reload (on the Mojang loading screen), which aborts the reload and leaves the
		// game stuck on the splash. Resolving the ITEM model id here as well (instead of
		// letting the item JSON's parent chain resolve to the dynamic model) avoids that;
		// the item then renders through the same dynamic model, keeping the saved faces.
		ModelLoadingPlugin.register(pluginContext ->
				pluginContext.resolveModel().register(context -> {
					ResourceLocation id = context.id();
					if (id.equals(MultiSidedBlocks.id("block/multi_sided"))
							|| id.equals(MultiSidedBlocks.id("item/multi_sided_block"))) {
						return new MultiSidedUnbakedModel();
					}
					return null;
				})
		);
	}
}

package dev.flare.msb.client;

import dev.flare.msb.MultiSidedBlocks;
import dev.flare.msb.client.render.MultiSidedUnbakedModel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;

public class MultiSidedBlocksClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// The blockstate file references "multi-sided-blocks:block/multi_sided"; instead of
		// shipping a static model JSON we provide a fully dynamic unbaked model that bakes
		// into our per-face quad generator.
		ModelLoadingPlugin.register(pluginContext ->
				pluginContext.resolveModel().register(context -> {
					if (context.id().equals(MultiSidedBlocks.id("block/multi_sided"))) {
						return new MultiSidedUnbakedModel();
					}
					return null;
				})
		);
	}
}

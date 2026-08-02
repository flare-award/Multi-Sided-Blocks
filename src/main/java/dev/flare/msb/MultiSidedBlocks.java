package dev.flare.msb;

import dev.flare.msb.block.MultiSidedBlock;
import dev.flare.msb.block.MultiSidedBlockEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiSidedBlocks implements ModInitializer {
	public static final String MOD_ID = "multi-sided-blocks";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Block MULTI_SIDED_BLOCK = new MultiSidedBlock();

	public static final BlockEntityType<MultiSidedBlockEntity> MULTI_SIDED_BLOCK_ENTITY =
			BlockEntityType.Builder.of(MultiSidedBlockEntity::new, MULTI_SIDED_BLOCK).build(null);

	public static final Item MULTI_SIDED_ITEM = new BlockItem(MULTI_SIDED_BLOCK, new Item.Properties());

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.BLOCK, id("multi_sided_block"), MULTI_SIDED_BLOCK);
		Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("multi_sided_block_entity"), MULTI_SIDED_BLOCK_ENTITY);
		Registry.register(BuiltInRegistries.ITEM, id("multi_sided_block"), MULTI_SIDED_ITEM);

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.BUILDING_BLOCKS)
				.register(entries -> entries.accept(MULTI_SIDED_ITEM));

		LOGGER.info("Multi-Sided Blocks initialized");
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}

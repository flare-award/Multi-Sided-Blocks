package dev.flare.msb.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A full opaque cube whose six faces each display the texture of an arbitrary block.
 *
 * <p>Right-click a face while holding any block item to assign that block's texture to the
 * clicked face. Sneak + right-click with an empty hand clears the face back to the default
 * texture. Face data is stored per-face in the {@link MultiSidedBlockEntity} and persists
 * in the world; broken blocks keep their faces via the item's BlockEntityTag.
 */
@SuppressWarnings("deprecation") // Block.use / getDrops are deprecated in 1.20.1 vanilla but remain the standard override points
public class MultiSidedBlock extends Block implements EntityBlock {

	public MultiSidedBlock() {
		super(BlockBehaviour.Properties.of()
				.strength(1.5F, 6.0F)
				.sound(SoundType.STONE));
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MultiSidedBlockEntity(pos, state);
	}

	/**
	 * The faces are drawn by {@code MultiSidedBlockEntityRenderer}, which reads the block
	 * entity every frame. Telling the renderer the shape is "animated" makes the vanilla
	 * chunk builder (and Sodium) skip this block in the chunk mesh — exactly like chests
	 * and signs — so face changes appear instantly without a chunk re-render, under any
	 * renderer (vanilla, Indigo, Sodium, Iris).
	 */
	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.ENTITYBLOCK_ANIMATED;
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!(level.getBlockEntity(pos) instanceof MultiSidedBlockEntity blockEntity)) {
			return InteractionResult.PASS;
		}

		Direction face = hit.getDirection();
		ItemStack stack = player.getItemInHand(hand);

		if (stack.getItem() instanceof BlockItem blockItem) {
			if (!level.isClientSide) {
				blockEntity.setFace(face, blockItem.getBlock().defaultBlockState());
				syncToClients(level, pos, blockEntity);
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}

		if (stack.isEmpty() && player.isShiftKeyDown()) {
			if (!level.isClientSide) {
				blockEntity.setFace(face, null);
				syncToClients(level, pos, blockEntity);
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}

		return InteractionResult.PASS;
	}

	/**
	 * Sends the updated face data to every player. The client's block entity renderer
	 * reads the data directly every frame, so no chunk re-render is needed for the change
	 * to show up.
	 */
	private static void syncToClients(Level level, BlockPos pos, MultiSidedBlockEntity blockEntity) {
		if (level instanceof ServerLevel serverLevel) {
			ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(blockEntity);
			for (ServerPlayer serverPlayer : serverLevel.players()) {
				serverPlayer.connection.send(packet);
			}
		}
	}

	/**
	 * Preserves the face data when the block is broken: the dropped item carries the
	 * BlockEntityTag and restores the faces when placed again.
	 */
	@Override
	public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		List<ItemStack> drops = super.getDrops(state, builder);
		BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		if (blockEntity instanceof MultiSidedBlockEntity multiSided && multiSided.hasAnyFace() && !drops.isEmpty()) {
			ItemStack stack = drops.get(0);
			stack.addTagElement("BlockEntityTag", saveFaces(multiSided));
		}
		return drops;
	}

	/** Creative middle-click also copies the faces into the picked item. */
	@Override
	public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
		ItemStack stack = super.getCloneItemStack(level, pos, state);
		if (level.getBlockEntity(pos) instanceof MultiSidedBlockEntity multiSided && multiSided.hasAnyFace()) {
			stack.addTagElement("BlockEntityTag", saveFaces(multiSided));
		}
		return stack;
	}

	private static CompoundTag saveFaces(MultiSidedBlockEntity blockEntity) {
		CompoundTag tag = blockEntity.saveWithFullMetadata();
		// Position is not meaningful on an item; BlockItem restores the entity at the
		// placement position, keeping the "id" (required to pick the right entity type).
		tag.remove("x");
		tag.remove("y");
		tag.remove("z");
		return tag;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
		Map<Direction, BlockState> faces = MultiSidedBlockEntity.readFaces(stack.getTagElement("BlockEntityTag"));
		if (faces.isEmpty()) {
			tooltip.add(Component.translatable("multi-sided-blocks.tooltip.hint"));
		} else {
			tooltip.add(Component.translatable("multi-sided-blocks.tooltip.faces"));
			for (Direction direction : Direction.values()) {
				BlockState faceState = faces.get(direction);
				if (faceState != null) {
					MutableComponent directionName = Component.translatable("multi-sided-blocks.tooltip." + direction.getSerializedName());
					tooltip.add(Component.translatable("multi-sided-blocks.tooltip.face", directionName, faceState.getBlock().getName()));
				}
			}
		}
		super.appendHoverText(stack, level, tooltip, flag);
	}
}

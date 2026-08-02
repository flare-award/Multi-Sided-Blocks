package dev.flare.msb.block;

import dev.flare.msb.MultiSidedBlocks;
import net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Stores the block state assigned to each of the six faces of a Multi-Sided Block.
 *
 * <p>Each face stores a full {@link BlockState} (not just a texture id), so properties
 * of the source block (slab type, waterlogging, facing, ...) are preserved too.
 * Data is persisted in NBT under the "faces" compound, keyed by direction name.
 */
public class MultiSidedBlockEntity extends BlockEntity implements RenderDataBlockEntity {

	public static final String FACES_TAG = "faces";

	/** The faces map is guarded by its own monitor so render threads can snapshot it safely. */
	private final Map<Direction, BlockState> faces = new EnumMap<>(Direction.class);

	public MultiSidedBlockEntity(BlockPos pos, BlockState state) {
		super(MultiSidedBlocks.MULTI_SIDED_BLOCK_ENTITY, pos, state);
	}

	@Nullable
	public BlockState getFace(Direction direction) {
		synchronized (this.faces) {
			return this.faces.get(direction);
		}
	}

	public boolean hasAnyFace() {
		synchronized (this.faces) {
			return !this.faces.isEmpty();
		}
	}

	/** Immutable copy of the currently assigned face states. */
	public Map<Direction, BlockState> facesSnapshot() {
		synchronized (this.faces) {
			return Collections.unmodifiableMap(new EnumMap<>(this.faces));
		}
	}

	@Override
	public FaceSnapshot getRenderData() {
		return new FaceSnapshot(this.facesSnapshot());
	}

	/** Thread-safe, immutable data consumed by asynchronous chunk builders. */
	public record FaceSnapshot(Map<Direction, BlockState> faces) {
		public FaceSnapshot {
			EnumMap<Direction, BlockState> copy = new EnumMap<>(Direction.class);
			copy.putAll(faces);
			faces = Collections.unmodifiableMap(copy);
		}
	}

	/**
	 * Assigns (or clears, when {@code state} is null/air) the texture source for one face.
	 */
	public void setFace(Direction direction, @Nullable BlockState state) {
		synchronized (this.faces) {
			if (state == null || state.isAir()) {
				this.faces.remove(direction);
			} else {
				this.faces.put(direction, state);
			}
		}
		this.setChanged();
	}

	/**
	 * Reads the face map out of an NBT compound that uses the same format as {@link #FACES_TAG}.
	 * Used to restore faces from item stacks (BlockEntityTag).
	 */
	public static Map<Direction, BlockState> readFaces(CompoundTag tag) {
		Map<Direction, BlockState> result = new EnumMap<>(Direction.class);
		if (tag == null) {
			return result;
		}
		CompoundTag facesTag = tag.getCompound(FACES_TAG);
		if (facesTag.isEmpty()) {
			return result;
		}
		HolderGetter<Block> blockGetter = BuiltInRegistries.BLOCK.asLookup();
		for (Direction direction : Direction.values()) {
			String key = direction.getSerializedName();
			if (facesTag.contains(key, Tag.TAG_COMPOUND)) {
				BlockState state = NbtUtils.readBlockState(blockGetter, facesTag.getCompound(key));
				if (state != null && !state.isAir()) {
					result.put(direction, state);
				}
			}
		}
		return result;
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		synchronized (this.faces) {
			this.faces.clear();
			this.faces.putAll(readFaces(tag));
		}
	}

	@Override
	public void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		CompoundTag facesTag = new CompoundTag();
		synchronized (this.faces) {
			for (Map.Entry<Direction, BlockState> entry : this.faces.entrySet()) {
				facesTag.put(entry.getKey().getSerializedName(), NbtUtils.writeBlockState(entry.getValue()));
			}
		}
		if (!facesTag.isEmpty()) {
			tag.put(FACES_TAG, facesTag);
		}
	}
}

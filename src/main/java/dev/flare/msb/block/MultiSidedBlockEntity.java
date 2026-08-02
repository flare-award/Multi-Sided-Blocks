package dev.flare.msb.block;

import dev.flare.msb.MultiSidedBlocks;
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
public class MultiSidedBlockEntity extends BlockEntity {

	public static final String FACES_TAG = "faces";

	/** The faces map is guarded by its own monitor so render threads can snapshot it safely. */
	private final Map<Direction, BlockState> faces = new EnumMap<>(Direction.class);

	/**
	 * Incremented whenever the in-memory face data changes (including loading from NBT),
	 * so the client-side renderer can invalidate its per-entity resolution cache.
	 */
	private long facesVersion = 0L;

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

	/**
	 * Returns the current faces version. The client renderer uses it to know when its
	 * cached per-face resolution is stale (see {@code MultiSidedBlockEntityRenderer}).
	 */
	public long getFacesVersion() {
		synchronized (this.faces) {
			return this.facesVersion;
		}
	}

	/** Immutable copy of the currently assigned face states. */
	public Map<Direction, BlockState> facesSnapshot() {
		synchronized (this.faces) {
			return Collections.unmodifiableMap(new EnumMap<>(this.faces));
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
			this.facesVersion++;
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
			this.facesVersion++;
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

package dev.flare.msb.client.render;

import com.mojang.math.Vector3f;
import dev.flare.msb.block.MultiSidedBlockEntity;
import net.fabricmc.fabric.api.blockview.v2.FabricBlockView;
import net.fabricmc.fabric.api.renderer.v1.RenderContext;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Renders a Multi-Sided Block: a full cube whose six faces each show the texture of the
 * block assigned to that face (stored per block entity).
 *
 * <p>Chunk building goes through {@link #emitBlockQuads} (Fabric renderer API, used by the
 * default Indigo renderer and by Sodium), which has access to the block view and position
 * and therefore to the per-block face data. {@link #getQuads} is the vanilla fallback used
 * when no renderer integration is available; it renders the default texture cube.
 */
public class MultiSidedBakedModel implements BakedModel, FabricBakedModel {

	// DefaultVertexFormat.BLOCK in 1.20.1: position(3) color(1) uv0(2) uv2(1) normal(1) = 8 ints/vertex
	private static final int INTS_PER_VERTEX = 8;
	private static final int POS_INDEX = 0;
	private static final int COLOR_INDEX = 3;
	private static final int UV_INDEX = 4;

	/**
	 * Per-face cube geometry, exactly as vanilla {@code FaceInfo} + {@code BlockFaceUV}
	 * produce for a full 0..16 UV cube: [direction][vertex][x, y, z, u16, v16].
	 */
	private static final float[][][] FACE_VERTICES = {
			// DOWN
			{{0, 0, 1, 0, 0}, {0, 0, 0, 0, 16}, {1, 0, 0, 16, 16}, {1, 0, 1, 16, 0}},
			// UP
			{{0, 1, 0, 0, 0}, {0, 1, 1, 0, 16}, {1, 1, 1, 16, 16}, {1, 1, 0, 16, 0}},
			// NORTH
			{{1, 1, 0, 0, 0}, {1, 0, 0, 0, 16}, {0, 0, 0, 16, 16}, {0, 1, 0, 16, 0}},
			// SOUTH
			{{0, 1, 1, 0, 0}, {0, 0, 1, 0, 16}, {1, 0, 1, 16, 16}, {1, 1, 1, 16, 0}},
			// WEST
			{{0, 1, 0, 0, 0}, {0, 0, 0, 0, 16}, {0, 0, 1, 16, 16}, {0, 1, 1, 16, 0}},
			// EAST
			{{1, 1, 1, 0, 0}, {1, 0, 1, 0, 16}, {1, 0, 0, 16, 16}, {1, 1, 0, 16, 0}},
	};

	/** Display transforms equivalent to the vanilla block/block model. */
	private static final ItemTransforms ITEM_TRANSFORMS = new ItemTransforms(
			new ItemTransform(new Vector3f(75, 45, 0), new Vector3f(0, 2.5F, 0), new Vector3f(0.375F, 0.375F, 0.375F)),
			new ItemTransform(new Vector3f(75, 45, 0), new Vector3f(0, 2.5F, 0), new Vector3f(0.375F, 0.375F, 0.375F)),
			new ItemTransform(new Vector3f(0, 45, 0), new Vector3f(0, 0, 0), new Vector3f(0.4F, 0.4F, 0.4F)),
			new ItemTransform(new Vector3f(0, 45, 0), new Vector3f(0, 0, 0), new Vector3f(0.4F, 0.4F, 0.4F)),
			new ItemTransform(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), new Vector3f(1.0F, 1.0F, 1.0F)),
			new ItemTransform(new Vector3f(30, 225, 0), new Vector3f(0, 0, 0), new Vector3f(0.625F, 0.625F, 0.625F)),
			new ItemTransform(new Vector3f(0, 0, 0), new Vector3f(0, 3, 0), new Vector3f(0.25F, 0.25F, 0.25F)),
			new ItemTransform(new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), new Vector3f(0.5F, 0.5F, 0.5F))
	);

	private final TextureAtlasSprite baseSprite;

	public MultiSidedBakedModel(TextureAtlasSprite baseSprite) {
		this.baseSprite = baseSprite;
	}

	// -----------------------------------------------------------------------------------
	// Fabric renderer API (used for chunk building and item rendering by Indigo/Sodium)
	// -----------------------------------------------------------------------------------

	@Override
	public boolean isVanillaAdapter() {
		return false;
	}

	@Override
	public void emitBlockQuads(BlockAndTintGetter blockView, BlockState state, BlockPos pos,
			Supplier<RandomSource> randomSupplier, RenderContext context) {
		MultiSidedBlockEntity.FaceSnapshot snapshot = null;
		if (blockView instanceof FabricBlockView fabricView) {
			Object data = fabricView.getBlockEntityRenderData(pos);
			if (data instanceof MultiSidedBlockEntity.FaceSnapshot faceSnapshot) {
				snapshot = faceSnapshot;
			}
		}
		Map<Direction, MultiSidedRenderData.FaceRenderData> resolved =
				snapshot == null ? null : MultiSidedRenderData.resolve(blockView, pos, snapshot.faces());
		emitFaces(context, resolved);
	}

	@Override
	public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
		// Dropped / picked-up items carry their faces in the BlockEntityTag, so they render
		// with the real textures; plain items show the default texture cube.
		Map<Direction, BlockState> faces = MultiSidedBlockEntity.readFaces(stack.getTagElement("BlockEntityTag"));
		Map<Direction, MultiSidedRenderData.FaceRenderData> resolved =
				faces.isEmpty() ? null : MultiSidedRenderData.resolve(null, null, faces);
		emitFaces(context, resolved);
	}

	private void emitFaces(RenderContext context, @Nullable Map<Direction, MultiSidedRenderData.FaceRenderData> resolved) {
		QuadEmitter emitter = context.getEmitter();
		for (Direction direction : Direction.values()) {
			MultiSidedRenderData.FaceRenderData face = resolved == null ? null : resolved.get(direction);
			if (face == null) {
				emitFace(emitter, direction, this.baseSprite, -1);
			} else {
				TextureAtlasSprite sprite = blockAtlas().getSprite(face.spriteId());
				emitFace(emitter, direction, sprite, face.tintColor());
			}
		}
	}

	private static void emitFace(QuadEmitter emitter, Direction direction, TextureAtlasSprite sprite, int tintColor) {
		emitter.square(direction, 0, 0, 1, 1, 0);
		emitter.spriteBake(sprite, MutableQuadView.BAKE_LOCK_UV);
		if (tintColor == -1) {
			emitter.color(-1, -1, -1, -1);
		} else {
			emitter.color(tintColor, tintColor, tintColor, tintColor);
		}
		emitter.emit();
	}

	private static TextureAtlas blockAtlas() {
		return Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
	}

	// -----------------------------------------------------------------------------------
	// Vanilla BakedModel fallback (no renderer integration: plain default cube)
	// -----------------------------------------------------------------------------------

	@Override
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
		if (direction == null) {
			// All our quads are face quads; the null pass is for non-cullable geometry only.
			return List.of();
		}
		return List.of(buildBaseQuad(direction));
	}

	private BakedQuad buildBaseQuad(Direction direction) {
		float[][] vertices = FACE_VERTICES[direction.get3DDataValue()];
		int[] data = new int[4 * INTS_PER_VERTEX];
		for (int i = 0; i < 4; i++) {
			int offset = i * INTS_PER_VERTEX;
			data[offset + POS_INDEX] = Float.floatToRawIntBits(vertices[i][0]);
			data[offset + POS_INDEX + 1] = Float.floatToRawIntBits(vertices[i][1]);
			data[offset + POS_INDEX + 2] = Float.floatToRawIntBits(vertices[i][2]);
			data[offset + COLOR_INDEX] = -1;
			// 1.20.1 baked quads store atlas-space UVs (FaceBakery maps them through the sprite)
			data[offset + UV_INDEX] = Float.floatToRawIntBits(this.baseSprite.getU((double) vertices[i][3]));
			data[offset + UV_INDEX + 1] = Float.floatToRawIntBits(this.baseSprite.getV((double) vertices[i][4]));
			// uv2 and normal stay zero; the renderer computes both itself.
		}
		return new BakedQuad(data, -1, direction, this.baseSprite, true);
	}

	@Override
	public boolean useAmbientOcclusion() {
		return true;
	}

	@Override
	public boolean isGui3d() {
		return true;
	}

	@Override
	public boolean usesBlockLight() {
		return false;
	}

	@Override
	public boolean isCustomRenderer() {
		return false;
	}

	@Override
	public TextureAtlasSprite getParticleIcon() {
		return this.baseSprite;
	}

	@Override
	public ItemTransforms getTransforms() {
		return ITEM_TRANSFORMS;
	}

	@Override
	public ItemOverrides getOverrides() {
		return ItemOverrides.EMPTY;
	}
}

package dev.flare.msb.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.flare.msb.MultiSidedBlocks;
import dev.flare.msb.block.MultiSidedBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws a Multi-Sided Block as a full cube whose six faces show the texture of the block
 * assigned to that face.
 *
 * <p>The block reports {@code RenderShape.ENTITYBLOCK_ANIMATED}, so the vanilla chunk
 * builder (and Sodium) skip it in the chunk mesh; this renderer draws it every frame and
 * reads the face data straight from the block entity. Because the data is re-read every
 * frame, assigning a face becomes visible immediately and works with any renderer
 * (vanilla, Indigo, Sodium, Iris) — no chunk re-render and no Indium dependency.
 */
public class MultiSidedBlockEntityRenderer implements BlockEntityRenderer<MultiSidedBlockEntity> {

	/**
	 * Per-face cube geometry, exactly as vanilla {@code FaceInfo} + {@code BlockFaceUV}
	 * produce for a full 0..16 UV cube: [direction][vertex][x, y, z, u16, v16].
	 * Vertex order matches vanilla winding (backface culling is disabled in the
	 * translucent layer anyway).
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

	/** Unit normals for each face direction, in {@link Direction#get3DDataValue()} order. */
	private static final float[][] FACE_NORMALS = {
			{0.0F, -1.0F, 0.0F},
			{0.0F, 1.0F, 0.0F},
			{0.0F, 0.0F, -1.0F},
			{0.0F, 0.0F, 1.0F},
			{-1.0F, 0.0F, 0.0F},
			{1.0F, 0.0F, 0.0F},
	};

	/**
	 * Resolved face data is cached per block entity and invalidated whenever the entity's
	 * face data version changes (weak keys, so entries die with the entity).
	 */
	private final Map<MultiSidedBlockEntity, CacheEntry> cache =
			Collections.synchronizedMap(new WeakHashMap<>());

	public MultiSidedBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(MultiSidedBlockEntity blockEntity, float partialTick, PoseStack poseStack,
			MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		BlockAndTintGetter level = blockEntity.getLevel();
		if (level == null) {
			return;
		}
		BlockPos pos = blockEntity.getBlockPos();

		Map<Direction, MultiSidedRenderData.FaceRenderData> resolved = resolveCached(blockEntity, level, pos);

		int light = LightTexture.pack(
				level.getBrightness(LightLayer.BLOCK, pos),
				level.getBrightness(LightLayer.SKY, pos));

		VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());
		Matrix4f matrix = poseStack.last().pose();
		Matrix3f normalMatrix = poseStack.last().normal();

		for (Direction direction : Direction.values()) {
			MultiSidedRenderData.FaceRenderData face = resolved == null ? null : resolved.get(direction);
			TextureAtlasSprite sprite;
			int tint = -1;
			if (face == null) {
				sprite = baseSprite();
			} else {
				TextureAtlasSprite faceSprite = atlasSprite(face.spriteId());
				sprite = faceSprite == null ? baseSprite() : faceSprite;
				tint = face.tintColor();
			}
			emitFace(consumer, matrix, normalMatrix, level, direction, sprite, tint, light);
		}
	}

	@Nullable
	private Map<Direction, MultiSidedRenderData.FaceRenderData> resolveCached(
			MultiSidedBlockEntity blockEntity, BlockAndTintGetter level, BlockPos pos) {
		long version = blockEntity.getFacesVersion();
		CacheEntry entry = this.cache.get(blockEntity);
		if (entry != null && entry.version == version) {
			return entry.resolved;
		}
		Map<Direction, MultiSidedRenderData.FaceRenderData> resolved = null;
		Map<Direction, BlockState> faces = blockEntity.facesSnapshot();
		if (!faces.isEmpty()) {
			try {
				resolved = MultiSidedRenderData.resolve(level, pos, faces);
			} catch (RuntimeException exception) {
				// Resolution may fail for exotic modded models or during resource reloads;
				// the faces simply fall back to the default texture for this frame.
				resolved = null;
			}
		}
		this.cache.put(blockEntity, new CacheEntry(version, resolved));
		return resolved;
	}

	private static void emitFace(VertexConsumer consumer, Matrix4f matrix, Matrix3f normalMatrix,
			BlockAndTintGetter level, Direction direction, TextureAtlasSprite sprite, int tint, int light) {
		// Per-face directional shading, exactly like vanilla cube faces.
		float shade = level.getShade(direction, true);
		float red = shade;
		float green = shade;
		float blue = shade;
		if (tint != -1) {
			red *= (float) (tint >> 16 & 255) / 255.0F;
			green *= (float) (tint >> 8 & 255) / 255.0F;
			blue *= (float) (tint & 255) / 255.0F;
		}

		float[] normal = FACE_NORMALS[direction.get3DDataValue()];
		float[][] vertices = FACE_VERTICES[direction.get3DDataValue()];
		for (int i = 0; i < 4; i++) {
			float[] vertex = vertices[i];
			consumer.vertex(matrix, vertex[0], vertex[1], vertex[2])
					.color(red, green, blue, 1.0F)
					.uv(sprite.getU(vertex[3]), sprite.getV(vertex[4]))
					.overlayCoords(OverlayTexture.NO_OVERLAY)
					.uv2(light)
					.normal(normalMatrix, normal[0], normal[1], normal[2])
					.endVertex();
		}
	}

	@Nullable
	private static TextureAtlasSprite atlasSprite(ResourceLocation spriteId) {
		try {
			return Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(spriteId);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private static TextureAtlasSprite baseSprite() {
		return Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS)
				.getSprite(MultiSidedBlocks.id("block/multi_sided_base"));
	}

	private record CacheEntry(long version, @Nullable Map<Direction, MultiSidedRenderData.FaceRenderData> resolved) {
	}
}

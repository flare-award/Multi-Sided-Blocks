package dev.flare.msb.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Client-side resolution of the texture shown on each face of a Multi-Sided Block.
 *
 * <p>For every assigned face we inspect the referenced block's baked model and pick the
 * dominant quad on that face: its sprite becomes the face texture and its tint index is
 * resolved against the referenced block's color provider (so grass, leaves, water etc.
 * keep their biome tint). Non-cube blocks fall back to their particle sprite.
 */
public final class MultiSidedRenderData {

	private static final Logger LOGGER = LoggerFactory.getLogger("multi-sided-blocks/render");

	/** A resolved face: the sprite to draw and the tint color (0xAARRGGBB, -1 = white). */
	public record FaceRenderData(ResourceLocation spriteId, int tintColor) {
	}

	private MultiSidedRenderData() {
	}

	/**
	 * Resolves the render data for a snapshot of face states.
	 *
	 * @param blockView world access for biome-dependent tints; may be null (item rendering)
	 * @param pos       position of the block; may be null when {@code blockView} is null
	 * @param faces     snapshot of the assigned face states (never modified)
	 */
	@Nullable
	public static Map<Direction, FaceRenderData> resolve(@Nullable BlockAndTintGetter blockView, @Nullable BlockPos pos, Map<Direction, BlockState> faces) {
		Map<Direction, FaceRenderData> result = new EnumMap<>(Direction.class);
		Minecraft minecraft = Minecraft.getInstance();
		BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
		BlockColors blockColors = minecraft.getBlockColors();

		for (Direction direction : Direction.values()) {
			BlockState reference = faces.get(direction);
			if (reference == null || reference.isAir()) {
				continue;
			}
			FaceRenderData face = resolveFace(dispatcher, blockColors, blockView, pos, reference, direction);
			if (face != null) {
				result.put(direction, face);
			}
		}
		return result.isEmpty() ? null : Collections.unmodifiableMap(result);
	}

	@Nullable
	private static FaceRenderData resolveFace(BlockRenderDispatcher dispatcher, BlockColors blockColors,
			@Nullable BlockAndTintGetter blockView, @Nullable BlockPos pos, BlockState reference, Direction direction) {
		try {
			BakedModel model = dispatcher.getBlockModel(reference);
			RandomSource random = RandomSource.create();

			BakedQuad dominant = null;
			float bestArea = -1.0F;
			for (BakedQuad quad : model.getQuads(reference, direction, random)) {
				if (quad.getDirection() != direction) {
					continue;
				}
				float area = quadArea(quad);
				if (area > bestArea) {
					bestArea = area;
					dominant = quad;
				}
			}

			TextureAtlasSprite sprite;
			int tint = -1;
			if (dominant != null) {
				sprite = dominant.getSprite();
				int tintIndex = dominant.getTintIndex();
				if (tintIndex >= 0 && blockView != null) {
					tint = blockColors.getColor(reference, blockView, pos, tintIndex);
				}
			} else {
				sprite = model.getParticleIcon();
			}

			if (sprite == null) {
				return null;
			}
			return new FaceRenderData(sprite.contents().name(), tint);
		} catch (Exception exception) {
			// A modded model may not be safe to query from a chunk-building thread or may
			// throw on weird states; the face simply falls back to the default texture.
			LOGGER.debug("Could not resolve face texture for {} ({})", reference, direction, exception);
			return null;
		}
	}

	/** Approximate area of a quad on its face plane, used to pick the dominant face quad. */
	private static float quadArea(BakedQuad quad) {
		int[] vertices = quad.getVertices();
		float[] xs = new float[4];
		float[] ys = new float[4];
		Direction direction = quad.getDirection();
		boolean useZ = direction == Direction.UP || direction == Direction.DOWN;
		boolean useY = !useZ && (direction == Direction.NORTH || direction == Direction.SOUTH);
		for (int i = 0; i < 4; i++) {
			int offset = i * 8; // DefaultVertexFormat.BLOCK: 8 ints per vertex in 1.20.1
			xs[i] = Float.intBitsToFloat(vertices[offset]);
			if (useZ) {
				ys[i] = Float.intBitsToFloat(vertices[offset + 2]);
			} else if (useY) {
				ys[i] = Float.intBitsToFloat(vertices[offset + 1]);
			} else {
				ys[i] = Float.intBitsToFloat(vertices[offset + 2]);
			}
		}
		float area = 0.0F;
		for (int i = 0; i < 4; i++) {
			int next = (i + 1) % 4;
			area += xs[i] * ys[next] - xs[next] * ys[i];
		}
		return Math.abs(area) * 0.5F;
	}
}

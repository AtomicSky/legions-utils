package com.legions.client.render;

import com.legions.client.LegionsClient;
import com.legions.client.access.LegionsPlayerOverlayRenderStateAccess;
import com.legions.client.mixin.ModelPartAccessor;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Opaque inverted hull that is clipped by the world's depth buffer. */
public final class LegionsQuipOutline {
    private static final int DEFAULT_WIDTH_PIXELS = 3;
    private static final float FALLBACK_EXPANSION = 0.035F;
    private static final float MIN_EXPANSION = 0.01F;
    private static final float MAX_EXPANSION = 0.22F;
    private static final Set<String> SECONDARY_SKIN_PARTS = Set.of(
            "hat", "jacket", "left_sleeve", "right_sleeve", "left_pants", "right_pants");
    private static final RenderLayer LAYER = RenderLayer.of("legions_quip_outline",
            RenderSetup.builder(RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                            .withLocation(Identifier.of(LegionsClient.MOD_ID, "pipeline/quip_outline"))
                            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                            .withCull(true)
                            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                            .withDepthWrite(false)
                            .withoutBlend()
                            .build()))
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .build());

    private LegionsQuipOutline() {
    }

    public static void initialize() {
        // Register the pipeline before resource and shader loading.
    }

    /** Converts the configured screen-space width to model-space expansion. */
    public static float expansionFor(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (player == null || client == null || client.options == null || client.getCameraEntity() == null
                || client.getWindow() == null || client.getWindow().getFramebufferWidth() <= 0) {
            return FALLBACK_EXPANSION;
        }
        int pixels = LegionsClient.CONFIG == null
                ? DEFAULT_WIDTH_PIXELS : LegionsClient.CONFIG.quipOutlineWidth;
        double distance = Math.sqrt(player.squaredDistanceTo(client.getCameraEntity()));
        double halfFov = Math.toRadians(client.options.getFov().getValue()) * 0.5;
        float expansion = (float) (2.0 * Math.max(1.0, distance) * Math.tan(halfFov)
                * pixels / client.getWindow().getFramebufferWidth());
        return Math.max(MIN_EXPANSION, Math.min(MAX_EXPANSION, expansion));
    }

    public static <S> void submit(RenderCommandQueue queue, Model<? super S> model, S state,
                                  MatrixStack matrices) {
        if (!(model instanceof BipedEntityModel<?>)
                || !(state instanceof LegionsPlayerOverlayRenderStateAccess access)) {
            return;
        }
        List<ModelPart> visibleParts = snapshotVisibleParts(model.getRootPart());
        if (!access.legions_client$markQuipOutlinePass(new SubmissionKey(model, visibleParts))) {
            return;
        }
        int color = access.legions_client$getQuipOutlineColor();
        if (color == 0) {
            return;
        }
        float expansion = access.legions_client$getQuipOutlineExpansion();
        int opaqueColor = color | 0xFF000000;
        queue.submitCustom(matrices, LAYER, (entry, vertices) -> {
            model.setAngles(state);
            MatrixStack posedMatrices = new MatrixStack();
            posedMatrices.peek().copy(entry);
            renderPart(model.getRootPart(), posedMatrices, vertices, opaqueColor, expansion,
                    identitySet(visibleParts), model instanceof PlayerEntityModel, true);
        });
    }

    private static void renderPart(ModelPart part, MatrixStack matrices, VertexConsumer vertices,
                                   int color, float expansion, Set<ModelPart> visibleParts,
                                   boolean skipSecondarySkinParts, boolean root) {
        if (!visibleParts.contains(part)) {
            return;
        }
        matrices.push();
        part.applyTransform(matrices);
        ModelPartAccessor access = (ModelPartAccessor) (Object) part;
        if (!part.hidden) {
            for (ModelPart.Cuboid cuboid : access.legions_client$getCuboids()) {
                renderCuboid(cuboid, matrices.peek(), vertices, color, expansion);
            }
        }
        for (var child : access.legions_client$getChildren().entrySet()) {
            if (!root || !skipSecondarySkinParts || !SECONDARY_SKIN_PARTS.contains(child.getKey())) {
                renderPart(child.getValue(), matrices, vertices, color, expansion,
                        visibleParts, skipSecondarySkinParts, false);
            }
        }
        matrices.pop();
    }

    private static List<ModelPart> snapshotVisibleParts(ModelPart root) {
        List<ModelPart> visibleParts = new ArrayList<>();
        collectVisibleParts(root, visibleParts);
        return List.copyOf(visibleParts);
    }

    private static void collectVisibleParts(ModelPart part, List<ModelPart> visibleParts) {
        if (!part.visible) {
            return;
        }
        visibleParts.add(part);
        for (ModelPart child : ((ModelPartAccessor) (Object) part).legions_client$getChildren().values()) {
            collectVisibleParts(child, visibleParts);
        }
    }

    private static Set<ModelPart> identitySet(List<ModelPart> parts) {
        Set<ModelPart> result = Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(parts);
        return result;
    }

    private static final class SubmissionKey {
        private final Object model;
        private final List<ModelPart> visibleParts;

        private SubmissionKey(Object model, List<ModelPart> visibleParts) {
            this.model = model;
            this.visibleParts = visibleParts;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof SubmissionKey key)
                    || model != key.model || visibleParts.size() != key.visibleParts.size()) {
                return false;
            }
            for (int index = 0; index < visibleParts.size(); index++) {
                if (visibleParts.get(index) != key.visibleParts.get(index)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = System.identityHashCode(model);
            for (ModelPart part : visibleParts) {
                hash = 31 * hash + System.identityHashCode(part);
            }
            return hash;
        }
    }

    private static void renderCuboid(ModelPart.Cuboid cuboid, MatrixStack.Entry entry,
                                     VertexConsumer vertices, int color, float expansion) {
        float centerX = (cuboid.minX + cuboid.maxX) / 32.0F;
        float centerY = (cuboid.minY + cuboid.maxY) / 32.0F;
        float centerZ = (cuboid.minZ + cuboid.maxZ) / 32.0F;
        for (ModelPart.Quad quad : cuboid.sides) {
            ModelPart.Vertex[] corners = quad.vertices();
            // Reversed winding makes the normal front faces get culled. Only the
            // enlarged rear shell survives, and the player depth hides its interior.
            for (int index = corners.length - 1; index >= 0; index--) {
                ModelPart.Vertex corner = corners[index];
                vertices.vertex(entry,
                                expand(corner.worldX(), centerX, expansion),
                                expand(corner.worldY(), centerY, expansion),
                                expand(corner.worldZ(), centerZ, expansion))
                        .color(color);
            }
        }
    }

    private static float expand(float position, float center, float expansion) {
        return position + Math.signum(position - center) * expansion;
    }
}

package com.legions.client.mixin;

import com.legions.client.LegionsSpectateLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@Pseudo
@Mixin(targets = "com.atomics.client.DualSpectateCamera")
public abstract class AtomicsDualSpectateCameraMixin {
    private static final float LOCKED_FRAME_PADDING = 2.5F;
    private static final float LOCKED_MIN_DISTANCE = 2.0F;
    private static final float LOCKED_MAX_DISTANCE = 160.0F;
    private static final float LOCKED_MAX_Y_DIFFERENCE = 10.0F;
    private static final double MIN_USEFUL_CAMERA_DISTANCE_SQUARED = 4.0D;
    private static final double WALL_BACKOFF = 0.35D;
    private static final Map<Class<?>, Map<String, Field>> FIELD_CACHE = new HashMap<>();
    private static Class<?> atomicsClientClass;

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private static void legions_client$forceDualSpectateDefaults(Minecraft client, CallbackInfo ci) {
        if (!LegionsSpectateLock.hasLock()) {
            return;
        }
        forceDualSpectateDefaults();
    }

    @Inject(method = "resolveCameraPosition", at = @At("RETURN"), cancellable = true, remap = false)
    private static void legions_client$preferClearCameraPosition(Minecraft client, Player first,
                                                                 Player second, Vec3 center, Vec3 side,
                                                                 float distance, Vec3 lookTarget,
                                                                 Vec3 preferredCameraPos, @Coerce Object pvp,
                                                                 CallbackInfoReturnable<Vec3> cir) {
        if (!LegionsSpectateLock.isLockedPair(first, second)) {
            return;
        }

        Vec3 preferred = cir.getReturnValue();
        Vec3 adjusted = findClearCameraPosition(client, lookTarget, preferred);
        if (adjusted != null) {
            cir.setReturnValue(adjusted);
        }
    }

    @Inject(method = "isWithinYDifference", at = @At("HEAD"), cancellable = true, remap = false)
    private static void legions_client$onlyLimitYDifferenceInOverhead(Player first, Player second,
                                                                      @Coerce Object pvp,
                                                                      CallbackInfoReturnable<Boolean> cir) {
        if (!LegionsSpectateLock.isLockedPair(first, second)) {
            return;
        }

        if (!isDualSpectateOverheadEnabled(pvp)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isAllowedSpectatePair", at = @At("HEAD"), cancellable = true, remap = false)
    private static void legions_client$allowExplicitLockedPair(Minecraft client, Player first,
                                                               Player second, @Coerce Object pvp,
                                                               @Coerce Object teamRules,
                                                               CallbackInfoReturnable<Boolean> cir) {
        if (LegionsSpectateLock.isLockedPair(first, second)) {
            cir.setReturnValue(true);
        }
    }

    private static Vec3 findClearCameraPosition(Minecraft client, Vec3 lookTarget, Vec3 preferred) {
        if (client == null || client.level == null || lookTarget == null || preferred == null) {
            return preferred;
        }

        if (hasClearPath(client, lookTarget, preferred) && isClearAt(client, preferred)) {
            return preferred;
        }

        Vec3 clipped = clipBeforeWall(client, lookTarget, preferred);
        if (isUsableCameraPosition(client, lookTarget, clipped)) {
            return clipped;
        }

        Vec3 direction = preferred.subtract(lookTarget);
        if (direction.lengthSqr() < 1.0E-4D) {
            return preferred;
        }

        Vec3 candidate = usableCameraCandidate(client, lookTarget, preferred.add(0.0D, 0.75D, 0.0D));
        if (candidate != null) {
            return candidate;
        }
        candidate = usableCameraCandidate(client, lookTarget, preferred.add(0.0D, 1.5D, 0.0D));
        if (candidate != null) {
            return candidate;
        }
        candidate = usableCameraCandidate(client, lookTarget, preferred.add(0.0D, -0.75D, 0.0D));
        if (candidate != null) {
            return candidate;
        }
        candidate = usableCameraCandidate(client, lookTarget, preferred.add(0.0D, -1.5D, 0.0D));
        if (candidate != null) {
            return candidate;
        }
        candidate = usableCameraCandidate(client, lookTarget, lookTarget.add(direction.scale(0.75D)));
        if (candidate != null) {
            return candidate;
        }
        candidate = usableCameraCandidate(client, lookTarget, lookTarget.add(direction.scale(0.5D)));
        return candidate == null ? preferred : candidate;
    }

    private static Vec3 usableCameraCandidate(Minecraft client, Vec3 lookTarget, Vec3 candidate) {
        Vec3 clipped = clipBeforeWall(client, lookTarget, candidate);
        return isUsableCameraPosition(client, lookTarget, clipped) ? clipped : null;
    }

    private static boolean isUsableCameraPosition(Minecraft client, Vec3 lookTarget, Vec3 position) {
        return position != null
                && position.distanceToSqr(lookTarget) >= MIN_USEFUL_CAMERA_DISTANCE_SQUARED
                && isClearAt(client, position);
    }

    private static Vec3 clipBeforeWall(Minecraft client, Vec3 start, Vec3 end) {
        HitResult hit = raycast(client, start, end);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return end;
        }

        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-4D) {
            return end;
        }

        Vec3 clipped = hit.getLocation().subtract(direction.normalize().scale(WALL_BACKOFF));
        return clipped.distanceToSqr(start) < end.distanceToSqr(start) ? clipped : end;
    }

    private static boolean hasClearPath(Minecraft client, Vec3 start, Vec3 end) {
        HitResult hit = raycast(client, start, end);
        return hit == null || hit.getType() != HitResult.Type.BLOCK;
    }

    private static HitResult raycast(Minecraft client, Vec3 start, Vec3 end) {
        return client.level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                client.player
        ));
    }

    private static boolean isClearAt(Minecraft client, Vec3 position) {
        BlockPos blockPos = BlockPos.containing(position);
        return client.level.getBlockState(blockPos).getCollisionShape(client.level, blockPos).isEmpty();
    }

    private static void forceDualSpectateDefaults() {
        try {
            Object config = cachedField(atomicsClientClass(), "CONFIG").get(null);
            if (config == null) {
                return;
            }

            Object pvp = cachedField(config.getClass(), "pvp").get(config);
            if (pvp == null) {
                return;
            }

            setFloatField(pvp, "dualSpectatePadding", LOCKED_FRAME_PADDING);
            setFloatField(pvp, "dualSpectateMinDistance", LOCKED_MIN_DISTANCE);
            setFloatField(pvp, "dualSpectateMaxDistance", LOCKED_MAX_DISTANCE);
            setFloatField(pvp, "dualSpectateMaxYDifference", LOCKED_MAX_Y_DIFFERENCE);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static boolean isDualSpectateOverheadEnabled(Object pvp) {
        if (pvp == null) {
            return false;
        }
        try {
            Object value = cachedField(pvp.getClass(), "dualSpectateOverheadEnabled").get(pvp);
            return value instanceof Boolean enabled && enabled;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static void setFloatField(Object owner, String fieldName, float value) throws ReflectiveOperationException {
        Field field = cachedField(owner.getClass(), fieldName);
        if (field.getType() == float.class) {
            field.setFloat(owner, value);
        } else if (field.getType() == double.class) {
            field.setDouble(owner, value);
        }
    }

    private static Class<?> atomicsClientClass() throws ClassNotFoundException {
        if (atomicsClientClass == null) {
            atomicsClientClass = Class.forName("com.atomics.client.AtomicsClient");
        }
        return atomicsClientClass;
    }

    private static Field cachedField(Class<?> owner, String name) throws NoSuchFieldException {
        Map<String, Field> fields = FIELD_CACHE.computeIfAbsent(owner, ignored -> new HashMap<>());
        Field field = fields.get(name);
        if (field == null) {
            field = owner.getDeclaredField(name);
            field.setAccessible(true);
            fields.put(name, field);
        }
        return field;
    }
}

package com.dziubek.boxpvp;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renderuje aktywnego peta gracza jako zestaw encji ItemDisplay (jedna na kość z geometrią) i
 * odtwarza go w czasie rzeczywistym za graczem - ruch podąża wygładzony (bez teleportu co klatkę,
 * z odrobiną opóźnienia jak żywy zwierzak), animacja przełącza się między "idle" i "walk" zależnie
 * od tego czy pet aktualnie nadrabia dystans, a "pet" odtwarza się na chwilę po prawym kliknięciu.
 *
 * Hierarchia kości komponowana rekurencyjnie (world = parent.world * local), tak jak w
 * CrateModelDisplayManager - ale tutaj co tick (nie raz przy spawnie), bo pet się rusza i
 * animuje w pętli.
 */
public class PetDisplayManager {

    private static final long TICK_INTERVAL = 2L;
    private static final double FOLLOW_DISTANCE = 1.6;
    private static final double SMOOTH_FACTOR = 0.22;
    private static final double SNAP_DISTANCE = 10.0;
    private static final double MOVING_EPSILON = 0.02;

    private final Map<UUID, ActivePet> activePets = new HashMap<>();
    private final Map<UUID, UUID> entityToOwner = new HashMap<>();

    private record BoneState(Vector3f pos, Quaternionf rot) {
    }

    private static class ActivePet {
        final String species;
        final PetModel model;
        final Map<String, UUID> entities = new HashMap<>();
        Location anchor;
        float yaw;
        boolean moving;
        BukkitTask task;

        ActivePet(String species, PetModel model, Location anchor, float yaw) {
            this.species = species;
            this.model = model;
            this.anchor = anchor;
            this.yaw = yaw;
        }
    }

    public void spawn(BoxPvpPlugin plugin, Player owner, String species, PetModel model) {
        despawn(owner);

        Location start = owner.getLocation().clone();
        ActivePet active = new ActivePet(species, model, start, start.getYaw());

        for (PetModel.Bone bone : model.bones) {
            if (bone.modelKey == null) {
                continue;
            }
            ItemDisplay display = start.getWorld().spawn(start, ItemDisplay.class);
            display.setItemStack(customItem(bone.modelKey));
            display.setBillboard(Display.Billboard.FIXED);
            active.entities.put(bone.name, display.getUniqueId());
            entityToOwner.put(display.getUniqueId(), owner.getUniqueId());
        }

        active.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(active, owner),
                TICK_INTERVAL, TICK_INTERVAL);
        activePets.put(owner.getUniqueId(), active);
    }

    public void despawn(Player owner) {
        ActivePet active = activePets.remove(owner.getUniqueId());
        if (active == null) {
            return;
        }
        if (active.task != null) {
            active.task.cancel();
        }
        for (UUID uuid : active.entities.values()) {
            org.bukkit.entity.Entity entity = owner.getWorld().getEntity(uuid);
            if (entity == null && active.anchor.getWorld() != null) {
                entity = active.anchor.getWorld().getEntity(uuid);
            }
            if (entity != null) {
                entity.remove();
            }
            entityToOwner.remove(uuid);
        }
    }

    /** UUID właściciela peta, do którego należy ta encja (kość ItemDisplay), albo null. */
    public UUID ownerOfPetEntity(UUID entityId) {
        return entityToOwner.get(entityId);
    }

    public boolean hasActivePet(Player owner) {
        return activePets.containsKey(owner.getUniqueId());
    }

    public String activeSpecies(Player owner) {
        ActivePet active = activePets.get(owner.getUniqueId());
        return active == null ? null : active.species;
    }

    private void tick(ActivePet active, Player owner) {
        if (!owner.isOnline()) {
            return;
        }
        boolean changedWorld = !owner.getWorld().equals(active.anchor.getWorld());

        Location ownerLoc = owner.getLocation();
        Vector3f behind = new Vector3f((float) -Math.sin(Math.toRadians(ownerLoc.getYaw())), 0,
                (float) Math.cos(Math.toRadians(ownerLoc.getYaw())));
        Location target = ownerLoc.clone().add(behind.x() * FOLLOW_DISTANCE, 0, behind.z() * FOLLOW_DISTANCE);

        double distance = changedWorld ? Double.MAX_VALUE : target.distance(active.anchor);

        if (changedWorld || distance > SNAP_DISTANCE) {
            active.anchor = target.clone();
            active.moving = false;
        } else if (distance > MOVING_EPSILON) {
            double dx = target.getX() - active.anchor.getX();
            double dy = target.getY() - active.anchor.getY();
            double dz = target.getZ() - active.anchor.getZ();
            active.anchor.add(dx * SMOOTH_FACTOR, dy * SMOOTH_FACTOR, dz * SMOOTH_FACTOR);
            active.moving = distance > 0.08;
            if (active.moving) {
                double moveYaw = Math.toDegrees(Math.atan2(-dx, dz));
                active.yaw = smoothAngle(active.yaw, (float) moveYaw, 0.35f);
            }
        } else {
            active.moving = false;
        }

        // Statyczna poza spoczynkowa - animacja idle/walk/pet z modelu glitchowala sie na
        // wielokościowym rigu peta, więc na razie tylko podążanie za graczem (ruch encji), bez
        // odtwarzania klatek kluczowych.
        Quaternionf baseYaw = new Quaternionf().rotateY((float) Math.toRadians(-active.yaw));
        Map<String, BoneState> states = new HashMap<>();
        for (PetModel.Bone bone : active.model.bones) {
            resolveState(bone, active.model, null, 0, baseYaw, states);
        }

        for (PetModel.Bone bone : active.model.bones) {
            UUID uuid = active.entities.get(bone.name);
            if (uuid == null) {
                continue;
            }
            org.bukkit.entity.Entity entity = active.anchor.getWorld().getEntity(uuid);
            if (!(entity instanceof ItemDisplay display)) {
                continue;
            }
            if (!entity.getLocation().getWorld().equals(active.anchor.getWorld())
                    || entity.getLocation().distanceSquared(active.anchor) > 0.0001) {
                entity.teleport(active.anchor);
            }
            BoneState state = states.get(bone.name);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration((int) TICK_INTERVAL);
            display.setTransformation(toTransformation(state));
        }
    }

    private static float smoothAngle(float current, float target, float factor) {
        float diff = ((target - current + 540f) % 360f) - 180f;
        return current + diff * factor;
    }

    private BoneState resolveState(PetModel.Bone bone, PetModel model, PetModel.Animation animation,
                                    double time, Quaternionf baseYaw, Map<String, BoneState> cache) {
        BoneState cached = cache.get(bone.name);
        if (cached != null) {
            return cached;
        }

        Vector3f parentPos;
        Quaternionf parentRot;
        double[] parentPivot;
        if (bone.parent == null) {
            parentPos = new Vector3f(0, 0, 0);
            parentRot = baseYaw;
            parentPivot = new double[]{0, 0, 0};
        } else {
            PetModel.Bone parentBone = model.bone(bone.parent);
            BoneState parentState = resolveState(parentBone, model, animation, time, baseYaw, cache);
            parentPos = parentState.pos();
            parentRot = parentState.rot();
            parentPivot = parentBone.pivot;
        }

        Vector3f animPos = channelVector(animation, bone.name, "position", time, 0);
        Vector3f animRotDeg = channelVector(animation, bone.name, "rotation", time, 0);

        Vector3f localOffset = new Vector3f(
                (float) ((bone.pivot[0] - parentPivot[0]) / 16.0),
                (float) ((bone.pivot[1] - parentPivot[1]) / 16.0),
                (float) ((bone.pivot[2] - parentPivot[2]) / 16.0)
        ).add(new Vector3f(animPos).div(16f));

        Vector3f rotatedOffset = parentRot.transform(new Vector3f(localOffset), new Vector3f());
        Vector3f worldPos = new Vector3f(parentPos).add(rotatedOffset);

        Quaternionf localRot = new Quaternionf().rotateXYZ(
                (float) Math.toRadians(animRotDeg.x),
                (float) Math.toRadians(animRotDeg.y),
                (float) Math.toRadians(animRotDeg.z));
        Quaternionf worldRot = new Quaternionf(parentRot).mul(localRot);

        BoneState state = new BoneState(worldPos, worldRot);
        cache.put(bone.name, state);
        return state;
    }

    private Vector3f channelVector(PetModel.Animation animation, String boneName, String channel,
                                    double time, float defaultValue) {
        if (animation == null) {
            return new Vector3f(defaultValue, defaultValue, defaultValue);
        }
        PetModel.BoneTrack track = animation.tracks.get(boneName);
        if (track == null) {
            return new Vector3f(defaultValue, defaultValue, defaultValue);
        }
        List<PetModel.Keyframe> keyframes = switch (channel) {
            case "position" -> track.position;
            case "rotation" -> track.rotation;
            case "scale" -> track.scale;
            default -> List.of();
        };
        if (keyframes.isEmpty()) {
            return new Vector3f(defaultValue, defaultValue, defaultValue);
        }
        if (time <= keyframes.get(0).time) {
            PetModel.Keyframe kf = keyframes.get(0);
            return new Vector3f((float) kf.x, (float) kf.y, (float) kf.z);
        }
        for (int i = 0; i < keyframes.size() - 1; i++) {
            PetModel.Keyframe a = keyframes.get(i);
            PetModel.Keyframe b = keyframes.get(i + 1);
            if (time >= a.time && time <= b.time) {
                double span = b.time - a.time;
                float t = span <= 0 ? 0 : (float) ((time - a.time) / span);
                return new Vector3f(
                        (float) lerp(a.x, b.x, t),
                        (float) lerp(a.y, b.y, t),
                        (float) lerp(a.z, b.z, t));
            }
        }
        PetModel.Keyframe last = keyframes.get(keyframes.size() - 1);
        return new Vector3f((float) last.x, (float) last.y, (float) last.z);
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private static Transformation toTransformation(BoneState state) {
        return new Transformation(state.pos(), state.rot(), new Vector3f(1, 1, 1), new Quaternionf());
    }

    private static ItemStack customItem(String modelKey) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("boxpvp", modelKey));
        item.setItemMeta(meta);
        return item;
    }
}

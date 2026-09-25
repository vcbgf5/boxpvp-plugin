package com.dziubek.boxpvp;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Renderuje 3D model skrzyni (skonwertowany z Blockbencha) jako zestaw encji ItemDisplay - jedna
 * encja na kość (bone) z geometrią. Blok pod skrzynią staje się niewidzialnym Material.BARRIER,
 * a model wizualnie go zastępuje. Animacje (np. "open") odtwarzane są przez ustawianie docelowej
 * Transformation na każdej klatce kluczowej z setInterpolationDuration() - resztę (płynne przejście
 * między klatkami) robi silnik gry, nie musimy sami liczyć pośrednich klatek.
 *
 * Hierarchia kości jest komponowana rekurencyjnie (world = parent.world * local) przy użyciu
 * wektorów/kwaternionów JOML zamiast pełnych macierzy - prostsze i wystarczające, bo modele nie
 * mają ścinania (shear).
 */
public class CrateModelDisplayManager {

    private final Map<String, Map<String, UUID>> entitiesByLocation = new HashMap<>();
    private final Map<String, Binding> bindingsByLocation = new HashMap<>();

    private record Binding(String modelName, float yaw) {
    }

    private record BoneState(Vector3f pos, Quaternionf rot, Vector3f scale) {
    }

    public void spawn(Location blockLocation, CrateModel model, float yaw) {
        String key = key(blockLocation);
        despawn(blockLocation);

        blockLocation.getBlock().setType(Material.BARRIER);

        Location anchor = blockLocation.clone().add(0.5, 0, 0.5);
        Quaternionf baseYaw = new Quaternionf().rotateY((float) Math.toRadians(-yaw));

        Map<String, BoneState> restStates = new HashMap<>();
        for (CrateModel.Bone bone : model.bones) {
            resolveState(bone, model, null, 0, baseYaw, restStates);
        }

        Map<String, UUID> entities = new HashMap<>();
        for (CrateModel.Bone bone : model.bones) {
            if (bone.modelKey == null) {
                continue;
            }
            BoneState state = restStates.get(bone.name);
            ItemDisplay display = anchor.getWorld().spawn(anchor, ItemDisplay.class);
            display.setItem(customItem(bone.modelKey));
            display.setBillboard(Display.Billboard.FIXED);
            display.setTransformation(toTransformation(state));
            entities.put(bone.name, display.getUniqueId());
        }

        entitiesByLocation.put(key, entities);
        bindingsByLocation.put(key, new Binding(model.name, yaw));
    }

    public void despawn(Location blockLocation) {
        String key = key(blockLocation);
        Map<String, UUID> entities = entitiesByLocation.remove(key);
        bindingsByLocation.remove(key);
        if (entities == null) {
            return;
        }
        for (UUID uuid : entities.values()) {
            org.bukkit.entity.Entity entity = blockLocation.getWorld() != null
                    ? blockLocation.getWorld().getEntity(uuid) : null;
            if (entity != null) {
                entity.remove();
            }
        }
    }

    public boolean hasModel(Location blockLocation) {
        return bindingsByLocation.containsKey(key(blockLocation));
    }

    /** Wygodny skrót - odtwarza "open" na modelu przypiętym do lokalizacji, jeśli taki istnieje. */
    public void playOpenAnimation(BoxPvpPlugin plugin, Location blockLocation) {
        Binding binding = bindingsByLocation.get(key(blockLocation));
        if (binding == null) {
            return;
        }
        CrateModel model = plugin.getCrateModels().get(binding.modelName());
        if (model == null) {
            return;
        }
        playAnimation(plugin, blockLocation, model, "open");
    }

    /**
     * Odtwarza nazwaną animację (np. "open") na modelu przypiętym do tej lokalizacji. Dla każdego
     * unikalnego czasu klatki kluczowej (scalone ze wszystkich kości/kanałów) liczy stan świata
     * wszystkich kości i planuje setTransformation() z interpolacją do następnej klatki.
     */
    public void playAnimation(BoxPvpPlugin plugin, Location blockLocation, CrateModel model, String animationName) {
        CrateModel.Animation animation = model.animations.get(animationName);
        if (animation == null) {
            return;
        }
        String key = key(blockLocation);
        Map<String, UUID> entities = entitiesByLocation.get(key);
        Binding binding = bindingsByLocation.get(key);
        if (entities == null || binding == null) {
            return;
        }
        Quaternionf baseYaw = new Quaternionf().rotateY((float) Math.toRadians(-binding.yaw()));

        TreeSet<Double> times = new TreeSet<>();
        times.add(0.0);
        for (CrateModel.BoneTrack track : animation.tracks.values()) {
            for (CrateModel.Keyframe kf : track.rotation) times.add(kf.time);
            for (CrateModel.Keyframe kf : track.position) times.add(kf.time);
            for (CrateModel.Keyframe kf : track.scale) times.add(kf.time);
        }
        List<Double> timeline = new ArrayList<>(times);

        for (int idx = 0; idx < timeline.size(); idx++) {
            double time = timeline.get(idx);
            long delayTicks = Math.round(time * 20);
            long durationTicks = idx + 1 < timeline.size()
                    ? Math.max(1, Math.round((timeline.get(idx + 1) - time) * 20))
                    : 10L;

            Map<String, BoneState> states = new HashMap<>();
            for (CrateModel.Bone bone : model.bones) {
                resolveState(bone, model, animation, time, baseYaw, states);
            }

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (CrateModel.Bone bone : model.bones) {
                    UUID uuid = entities.get(bone.name);
                    if (uuid == null) {
                        continue;
                    }
                    org.bukkit.entity.Entity entity = blockLocation.getWorld() != null
                            ? blockLocation.getWorld().getEntity(uuid) : null;
                    if (!(entity instanceof ItemDisplay display)) {
                        continue;
                    }
                    BoneState state = states.get(bone.name);
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration((int) durationTicks);
                    display.setTransformation(toTransformation(state));
                }
            }, delayTicks);
        }
    }

    private BoneState resolveState(CrateModel.Bone bone, CrateModel model, CrateModel.Animation animation,
                                    double time, Quaternionf baseYaw, Map<String, BoneState> cache) {
        BoneState cached = cache.get(bone.name);
        if (cached != null) {
            return cached;
        }

        Vector3f parentPos;
        Quaternionf parentRot;
        Vector3f parentScale;
        double[] parentPivot;
        if (bone.parent == null) {
            parentPos = new Vector3f(0, 0, 0);
            parentRot = baseYaw;
            parentScale = new Vector3f(1, 1, 1);
            parentPivot = new double[]{0, 0, 0};
        } else {
            CrateModel.Bone parentBone = model.bone(bone.parent);
            BoneState parentState = resolveState(parentBone, model, animation, time, baseYaw, cache);
            parentPos = parentState.pos();
            parentRot = parentState.rot();
            parentScale = parentState.scale();
            parentPivot = parentBone.pivot;
        }

        Vector3f animPos = channelVector(animation, bone.name, "position", time, 0);
        Vector3f animRotDeg = channelVector(animation, bone.name, "rotation", time, 0);
        Vector3f animScale = channelVector(animation, bone.name, "scale", time, 1);

        Vector3f localOffset = new Vector3f(
                (float) ((bone.pivot[0] - parentPivot[0]) / 16.0),
                (float) ((bone.pivot[1] - parentPivot[1]) / 16.0),
                (float) ((bone.pivot[2] - parentPivot[2]) / 16.0)
        ).add(new Vector3f(animPos).div(16f));

        Vector3f rotatedOffset = parentRot.transform(new Vector3f(localOffset).mul(parentScale), new Vector3f());
        Vector3f worldPos = new Vector3f(parentPos).add(rotatedOffset);

        Quaternionf localRot = new Quaternionf().rotateXYZ(
                (float) Math.toRadians(animRotDeg.x),
                (float) Math.toRadians(animRotDeg.y),
                (float) Math.toRadians(animRotDeg.z));
        Quaternionf worldRot = new Quaternionf(parentRot).mul(localRot);
        Vector3f worldScale = new Vector3f(parentScale).mul(animScale);

        BoneState state = new BoneState(worldPos, worldRot, worldScale);
        cache.put(bone.name, state);
        return state;
    }

    private Vector3f channelVector(CrateModel.Animation animation, String boneName, String channel,
                                    double time, float defaultValue) {
        if (animation == null) {
            return new Vector3f(defaultValue, defaultValue, defaultValue);
        }
        CrateModel.BoneTrack track = animation.tracks.get(boneName);
        if (track == null) {
            return new Vector3f(defaultValue, defaultValue, defaultValue);
        }
        List<CrateModel.Keyframe> keyframes = switch (channel) {
            case "position" -> track.position;
            case "rotation" -> track.rotation;
            case "scale" -> track.scale;
            default -> List.of();
        };
        if (keyframes.isEmpty()) {
            return new Vector3f(defaultValue, defaultValue, defaultValue);
        }
        if (time <= keyframes.get(0).time) {
            CrateModel.Keyframe kf = keyframes.get(0);
            return new Vector3f((float) kf.x, (float) kf.y, (float) kf.z);
        }
        for (int i = 0; i < keyframes.size() - 1; i++) {
            CrateModel.Keyframe a = keyframes.get(i);
            CrateModel.Keyframe b = keyframes.get(i + 1);
            if (time >= a.time && time <= b.time) {
                double span = b.time - a.time;
                float t = span <= 0 ? 0 : (float) ((time - a.time) / span);
                return new Vector3f(
                        (float) lerp(a.x, b.x, t),
                        (float) lerp(a.y, b.y, t),
                        (float) lerp(a.z, b.z, t));
            }
        }
        CrateModel.Keyframe last = keyframes.get(keyframes.size() - 1);
        return new Vector3f((float) last.x, (float) last.y, (float) last.z);
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private static Transformation toTransformation(BoneState state) {
        return new Transformation(state.pos(), state.rot(), state.scale(), new Quaternionf());
    }

    private static ItemStack customItem(String modelKey) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("boxpvp", modelKey));
        item.setItemMeta(meta);
        return item;
    }

    private static String key(Location location) {
        return location.getWorld() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}

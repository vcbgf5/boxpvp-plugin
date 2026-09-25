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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renderuje statyczny 3D model skrzyni (skonwertowany z Blockbencha) jako zestaw encji ItemDisplay -
 * jedna encja na kość (bone) z geometrią, zawsze w pozycji spoczynkowej (bez animacji otwarcia).
 * Blok pod skrzynią staje się niewidzialnym Material.BARRIER, a model wizualnie go zastępuje.
 *
 * Hierarchia kości jest komponowana rekurencyjnie (world = parent.world * local) przy użyciu
 * wektorów/kwaternionów JOML zamiast pełnych macierzy - prostsze i wystarczające, bo modele nie
 * mają ścinania (shear).
 */
public class CrateModelDisplayManager {

    private final Map<String, Map<String, UUID>> entitiesByLocation = new HashMap<>();
    private final Map<String, String> boundModelByLocation = new HashMap<>();

    private record BoneState(Vector3f pos, Quaternionf rot) {
    }

    public void spawn(Location blockLocation, CrateModel model, float yaw) {
        String key = key(blockLocation);
        despawn(blockLocation);

        blockLocation.getBlock().setType(Material.BARRIER);

        Location anchor = blockLocation.clone().add(0.5, 0, 0.5);
        Quaternionf baseYaw = new Quaternionf().rotateY((float) Math.toRadians(-yaw));

        Map<String, BoneState> states = new HashMap<>();
        for (CrateModel.Bone bone : model.bones) {
            resolveState(bone, model, baseYaw, states);
        }

        Map<String, UUID> entities = new HashMap<>();
        for (CrateModel.Bone bone : model.bones) {
            if (bone.modelKey == null) {
                continue;
            }
            BoneState state = states.get(bone.name);
            ItemDisplay display = anchor.getWorld().spawn(anchor, ItemDisplay.class);
            display.setItemStack(customItem(bone.modelKey));
            display.setBillboard(Display.Billboard.FIXED);
            display.setTransformation(toTransformation(state));
            entities.put(bone.name, display.getUniqueId());
        }

        entitiesByLocation.put(key, entities);
        boundModelByLocation.put(key, model.name);
    }

    public void despawn(Location blockLocation) {
        String key = key(blockLocation);
        Map<String, UUID> entities = entitiesByLocation.remove(key);
        boundModelByLocation.remove(key);
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
        return boundModelByLocation.containsKey(key(blockLocation));
    }

    private BoneState resolveState(CrateModel.Bone bone, CrateModel model, Quaternionf baseYaw,
                                    Map<String, BoneState> cache) {
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
            CrateModel.Bone parentBone = model.bone(bone.parent);
            BoneState parentState = resolveState(parentBone, model, baseYaw, cache);
            parentPos = parentState.pos();
            parentRot = parentState.rot();
            parentPivot = parentBone.pivot;
        }

        Vector3f localOffset = new Vector3f(
                (float) ((bone.pivot[0] - parentPivot[0]) / 16.0),
                (float) ((bone.pivot[1] - parentPivot[1]) / 16.0),
                (float) ((bone.pivot[2] - parentPivot[2]) / 16.0)
        );

        Vector3f rotatedOffset = parentRot.transform(new Vector3f(localOffset), new Vector3f());
        Vector3f worldPos = new Vector3f(parentPos).add(rotatedOffset);

        BoneState state = new BoneState(worldPos, new Quaternionf(parentRot));
        cache.put(bone.name, state);
        return state;
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

    private static String key(Location location) {
        return location.getWorld() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}

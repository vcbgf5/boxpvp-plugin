package com.dziubek.boxpvp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Statyczny model 3D skrzyni skonwertowany z Blockbencha (patrz scratchpad/models/convert - skrypt
 * konwersji poza repo) - hierarchia kości (bones) z pivotami. Każda kość z geometrią odpowiada
 * osobnemu custom itemowi w resource packu (assets/boxpvp/models/item/&lt;model&gt;_&lt;kość&gt;.json),
 * renderowanemu jako encja ItemDisplay w pozycji spoczynkowej (bez animacji).
 */
public class CrateModel {

    public final String name;
    public final List<Bone> bones;

    public CrateModel(String name, List<Bone> bones) {
        this.name = name;
        this.bones = bones;
    }

    public Bone bone(String boneName) {
        for (Bone b : bones) {
            if (b.name.equals(boneName)) {
                return b;
            }
        }
        return null;
    }

    public static class Bone {
        public final String name;
        public final String parent;
        public final double[] pivot; // [x,y,z] w jednostkach Blockbencha (16 = 1 blok)
        public final String modelKey; // np. "common_crate_lid" - null jesli kosc bez geometrii

        public Bone(String name, String parent, double[] pivot, String modelKey) {
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
            this.modelKey = modelKey;
        }
    }

    @SuppressWarnings("unchecked")
    public static CrateModel fromJson(String modelName, Map<String, Object> root) {
        List<Bone> bones = new ArrayList<>();
        for (Object o : (List<Object>) root.get("bones")) {
            Map<String, Object> b = (Map<String, Object>) o;
            String name = (String) b.get("name");
            String parent = (String) b.get("parent");
            List<Object> pivotList = (List<Object>) b.get("pivot");
            double[] pivot = new double[]{
                    ((Number) pivotList.get(0)).doubleValue(),
                    ((Number) pivotList.get(1)).doubleValue(),
                    ((Number) pivotList.get(2)).doubleValue()
            };
            String modelKey = (String) b.get("modelKey");
            bones.add(new Bone(name, parent, pivot, modelKey));
        }

        return new CrateModel(modelName, bones);
    }
}

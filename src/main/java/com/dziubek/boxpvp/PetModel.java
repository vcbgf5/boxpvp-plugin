package com.dziubek.boxpvp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model 3D peta skonwertowany z Blockbencha (ModelEngine "free" format, patrz
 * scratchpad/models/convert/convert_pets.py - skrypt konwersji poza repo) - hierarchia kości
 * (bones) z pivotami oraz animacje keyframe (idle/walk/pet). Każda kość z geometrią odpowiada
 * osobnemu custom itemowi w resource packu, renderowanemu jako encja ItemDisplay.
 */
public class PetModel {

    public final String name;
    public final List<Bone> bones;
    public final Map<String, Animation> animations;

    public PetModel(String name, List<Bone> bones, Map<String, Animation> animations) {
        this.name = name;
        this.bones = bones;
        this.animations = animations;
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
        public final double[] pivot;
        public final String modelKey;

        public Bone(String name, String parent, double[] pivot, String modelKey) {
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
            this.modelKey = modelKey;
        }
    }

    public static class Animation {
        public final double length;
        public final String loop;
        public final Map<String, BoneTrack> tracks;

        public Animation(double length, String loop, Map<String, BoneTrack> tracks) {
            this.length = length;
            this.loop = loop;
            this.tracks = tracks;
        }
    }

    public static class BoneTrack {
        public final List<Keyframe> rotation;
        public final List<Keyframe> position;
        public final List<Keyframe> scale;

        public BoneTrack(List<Keyframe> rotation, List<Keyframe> position, List<Keyframe> scale) {
            this.rotation = rotation;
            this.position = position;
            this.scale = scale;
        }
    }

    public static class Keyframe {
        public final double time;
        public final double x;
        public final double y;
        public final double z;

        public Keyframe(double time, double x, double y, double z) {
            this.time = time;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    @SuppressWarnings("unchecked")
    public static PetModel fromJson(String modelName, Map<String, Object> root) {
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

        Map<String, Animation> animations = new LinkedHashMap<>();
        Object animsObj = root.get("animations");
        if (animsObj instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) animsObj).entrySet()) {
                Map<String, Object> animMap = (Map<String, Object>) entry.getValue();
                double length = ((Number) animMap.get("length")).doubleValue();
                String loop = (String) animMap.get("loop");
                Map<String, BoneTrack> tracks = new LinkedHashMap<>();
                Map<String, Object> tracksObj = (Map<String, Object>) animMap.get("tracks");
                for (Map.Entry<String, Object> trackEntry : tracksObj.entrySet()) {
                    Map<String, Object> channels = (Map<String, Object>) trackEntry.getValue();
                    List<Keyframe> rotation = parseKeyframes((List<Object>) channels.get("rotation"));
                    List<Keyframe> position = parseKeyframes((List<Object>) channels.get("position"));
                    List<Keyframe> scale = parseKeyframes((List<Object>) channels.get("scale"));
                    tracks.put(trackEntry.getKey(), new BoneTrack(rotation, position, scale));
                }
                animations.put(entry.getKey(), new Animation(length, loop, tracks));
            }
        }

        return new PetModel(modelName, bones, animations);
    }

    @SuppressWarnings("unchecked")
    private static List<Keyframe> parseKeyframes(List<Object> raw) {
        List<Keyframe> list = new ArrayList<>();
        if (raw == null) {
            return list;
        }
        for (Object o : raw) {
            Map<String, Object> kf = (Map<String, Object>) o;
            list.add(new Keyframe(
                    ((Number) kf.get("time")).doubleValue(),
                    ((Number) kf.get("x")).doubleValue(),
                    ((Number) kf.get("y")).doubleValue(),
                    ((Number) kf.get("z")).doubleValue()
            ));
        }
        return list;
    }
}

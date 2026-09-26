package com.botanic.garden.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** 节点之间的步行路段（无向）。 */
public class Edge {
    private final String id;
    private final String fromId;
    private final String toId;
    private double distanceMeters; // 步行距离（米）
    private double slopePercent;   // 坡度（%）
    private boolean strollerFriendly; // 是否适合推车
    private String name;           // 路段名（可选）

    public Edge(String id, String fromId, String toId, double distanceMeters,
                double slopePercent, boolean strollerFriendly, String name) {
        this.id = id;
        this.fromId = fromId;
        this.toId = toId;
        this.distanceMeters = distanceMeters;
        this.slopePercent = slopePercent;
        this.strollerFriendly = strollerFriendly;
        this.name = name;
    }

    public String getId() { return id; }
    public String getFromId() { return fromId; }
    public String getToId() { return toId; }
    public double getDistanceMeters() { return distanceMeters; }
    public double getSlopePercent() { return slopePercent; }
    public boolean isStrollerFriendly() { return strollerFriendly; }
    public String getName() { return name; }

    public void setDistanceMeters(double v) { this.distanceMeters = v; }
    public void setSlopePercent(double v) { this.slopePercent = v; }
    public void setStrollerFriendly(boolean v) { this.strollerFriendly = v; }
    public void setName(String v) { this.name = v; }

    /** 无向边：返回另一端节点 id，若不属于该边返回 null。 */
    public String otherEnd(String nodeId) {
        if (fromId.equals(nodeId)) return toId;
        if (toId.equals(nodeId)) return fromId;
        return null;
    }

    public boolean connects(String a, String b) {
        return (fromId.equals(a) && toId.equals(b)) || (fromId.equals(b) && toId.equals(a));
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("fromId", fromId);
        m.put("toId", toId);
        m.put("distanceMeters", distanceMeters);
        m.put("slopePercent", slopePercent);
        m.put("strollerFriendly", strollerFriendly);
        m.put("name", name == null ? "" : name);
        return m;
    }
}

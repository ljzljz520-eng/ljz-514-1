package com.botanic.garden.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** 园区节点：温室 / 花境 / 湖边栈道 / 休息点。 */
public class GardenNode {
    private final String id;
    private String name;
    private NodeType type;
    private String description;
    private double x; // 地图坐标（0-1000 虚拟坐标系）
    private double y;

    public GardenNode(String id, String name, NodeType type, String description, double x, double y) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.description = description;
        this.x = x;
        this.y = y;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public NodeType getType() { return type; }
    public String getDescription() { return description; }
    public double getX() { return x; }
    public double getY() { return y; }

    public void setName(String name) { this.name = name; }
    public void setType(NodeType type) { this.type = type; }
    public void setDescription(String description) { this.description = description; }
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("type", type.name());
        m.put("typeLabel", type.label());
        m.put("icon", type.icon());
        m.put("description", description);
        m.put("x", x);
        m.put("y", y);
        return m;
    }
}

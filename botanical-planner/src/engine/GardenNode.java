package engine;

import java.util.LinkedHashMap;
import java.util.Map;

/** 园区内的一个点位 */
public class GardenNode {
    public final String id;
    public final String name;
    public final NodeType type;
    /** 展示坐标，0-1000 的虚拟坐标系（供前端 SVG 定位） */
    public final int x;
    public final int y;
    public final String description;

    public GardenNode(String id, String name, NodeType type, int x, int y, String description) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.x = x;
        this.y = y;
        this.description = description == null ? "" : description;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("type", type.code());
        m.put("typeLabel", type.label());
        m.put("x", x);
        m.put("y", y);
        m.put("description", description);
        return m;
    }
}

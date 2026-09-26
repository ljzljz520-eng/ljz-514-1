package com.botanic.garden.store;

import com.botanic.garden.http.Json;
import com.botanic.garden.model.Edge;
import com.botanic.garden.model.Garden;
import com.botanic.garden.model.GardenNode;
import com.botanic.garden.model.NodeType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 园区数据的读写与持久化（JSON 文件），线程安全。 */
public class DataStore {

    private final Path file;
    private Garden garden = new Garden();

    public DataStore(Path file) {
        this.file = file;
    }

    public synchronized Garden garden() { return garden; }

    /** 启动时加载：文件不存在则写入种子数据。 */
    public synchronized void load() {
        try {
            if (Files.exists(file)) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                garden = fromJson(Json.parseObject(text));
                System.out.println("[DataStore] 已加载数据文件 " + file.toAbsolutePath()
                        + "（节点 " + garden.getNodes().size() + "，路段 " + garden.getEdges().size() + "）");
            } else {
                garden = seed();
                save();
                System.out.println("[DataStore] 首次启动，已生成示例园区数据 -> " + file.toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("[DataStore] 数据文件损坏，改用示例数据：" + e.getMessage());
            garden = seed();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, Json.stringify(toJson(garden)), StandardCharsets.UTF_8);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[DataStore] 保存失败：" + e.getMessage());
        }
    }

    public static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ---------------- 序列化 ----------------

    private Map<String, Object> toJson(Garden g) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("nodes", g.getNodes().stream().map(GardenNode::toJson).toList());
        root.put("edges", g.getEdges().stream().map(Edge::toJson).toList());
        return root;
    }

    @SuppressWarnings("unchecked")
    private Garden fromJson(Map<String, Object> root) {
        Garden g = new Garden();
        Object nodes = root.get("nodes");
        if (nodes instanceof List) {
            for (Object o : (List<Object>) nodes) {
                Map<String, Object> m = (Map<String, Object>) o;
                NodeType type = NodeType.fromString(Json.asString(m.get("type")));
                if (type == null) type = NodeType.REST_AREA;
                Double x = Json.asDouble(m.get("x"));
                Double y = Json.asDouble(m.get("y"));
                g.addNode(new GardenNode(
                        Json.asString(m.get("id")),
                        Json.asString(m.get("name")),
                        type,
                        Json.asString(m.get("description")),
                        x == null ? 500 : x,
                        y == null ? 400 : y));
            }
        }
        Object edges = root.get("edges");
        if (edges instanceof List) {
            for (Object o : (List<Object>) edges) {
                Map<String, Object> m = (Map<String, Object>) o;
                Double dist = Json.asDouble(m.get("distanceMeters"));
                Double slope = Json.asDouble(m.get("slopePercent"));
                Boolean stroller = Json.asBoolean(m.get("strollerFriendly"));
                g.addEdge(new Edge(
                        Json.asString(m.get("id")),
                        Json.asString(m.get("fromId")),
                        Json.asString(m.get("toId")),
                        dist == null ? 0 : dist,
                        slope == null ? 0 : slope,
                        stroller != null && stroller,
                        Json.asString(m.get("name"))));
            }
        }
        return g;
    }

    // ---------------- 示例园区 ----------------

    private Garden seed() {
        Garden g = new Garden();
        g.addNode(new GardenNode("gate",      "正门入口",   NodeType.REST_AREA,       "园区正门，设有游客服务中心与导览图。", 500, 720));
        g.addNode(new GardenNode("plaza",     "中央广场",   NodeType.REST_AREA,       "园区中心广场，通往各主题区的枢纽。", 500, 540));
        g.addNode(new GardenNode("tropical",  "热带温室",   NodeType.GREENHOUSE,      "棕榈、芭蕉与兰花，常年温暖湿润。", 240, 400));
        g.addNode(new GardenNode("desert",    "沙漠温室",   NodeType.GREENHOUSE,      "仙人掌与多肉植物王国，光照充足。", 130, 250));
        g.addNode(new GardenNode("fern",      "蕨类温室",   NodeType.GREENHOUSE,      "荫生蕨类与食虫植物，清凉幽静。", 370, 250));
        g.addNode(new GardenNode("rose",      "玫瑰花境",   NodeType.FLOWER_BORDER,   "数百个月季品种，春秋两季最盛。", 660, 430));
        g.addNode(new GardenNode("herb",      "香草花境",   NodeType.FLOWER_BORDER,   "薰衣草、迷迭香与薄荷，香气怡人。", 830, 330));
        g.addNode(new GardenNode("perennial", "宿根花境",   NodeType.FLOWER_BORDER,   "多年生花卉混植，四季皆有花看。", 620, 300));
        g.addNode(new GardenNode("boardwalk", "湖东栈道",   NodeType.LAKESIDE_WALK,   "沿湖木栈道，可观水鸟与睡莲。", 880, 560));
        g.addNode(new GardenNode("pier",      "观湖平台",   NodeType.LAKESIDE_WALK,   "伸入湖面的观景平台，日落绝佳。", 760, 700));
        g.addNode(new GardenNode("teahouse",  "湖畔茶室",   NodeType.REST_AREA,       "供应茶饮点心，可远眺湖景。", 680, 590));
        g.addNode(new GardenNode("pavilion",  "山顶凉亭",   NodeType.REST_AREA,       "园区制高点，可俯瞰全园与湖面。", 400, 120));

        g.addEdge(new Edge("e-gate-plaza",      "gate", "plaza",      220, 0, true,  "迎宾大道"));
        g.addEdge(new Edge("e-plaza-tropical",  "plaza", "tropical",  260, 2, true,  "棕榈步道"));
        g.addEdge(new Edge("e-tropical-desert", "tropical", "desert", 180, 5, true,  "温室连廊"));
        g.addEdge(new Edge("e-tropical-fern",   "tropical", "fern",   150, 1, true,  "荫生小径"));
        g.addEdge(new Edge("e-fern-pavilion",   "fern", "pavilion",   200, 9, false, "登山石阶"));
        g.addEdge(new Edge("e-desert-pavilion", "desert", "pavilion", 240, 12, false, "山脊步道"));
        g.addEdge(new Edge("e-plaza-rose",      "plaza", "rose",      200, 0, true,  "月季园路"));
        g.addEdge(new Edge("e-rose-herb",       "rose", "herb",       170, 3, true,  "香草小径"));
        g.addEdge(new Edge("e-rose-perennial",  "rose", "perennial",  160, 4, true,  "花境环路"));
        g.addEdge(new Edge("e-perennial-herb",  "perennial", "herb",  190, 6, false, "坡地花径"));
        g.addEdge(new Edge("e-herb-boardwalk",  "herb", "boardwalk",  230, 2, true,  "湖畔引道"));
        g.addEdge(new Edge("e-boardwalk-pier",  "boardwalk", "pier",  140, 0, true,  "临水栈道"));
        g.addEdge(new Edge("e-pier-teahouse",   "pier", "teahouse",   110, 0, true,  "湖岸步道"));
        g.addEdge(new Edge("e-teahouse-plaza",  "teahouse", "plaza",  180, 1, true,  "广场南路"));
        g.addEdge(new Edge("e-teahouse-boardwalk", "teahouse", "boardwalk", 160, 1, true, "湖东环路"));
        g.addEdge(new Edge("e-plaza-pavilion",  "plaza", "pavilion",  420, 8, false, "登高直道"));
        g.addEdge(new Edge("e-gate-teahouse",   "gate", "teahouse",   300, 1, true,  "滨湖大道"));
        g.addEdge(new Edge("e-fern-perennial",  "fern", "perennial",  260, 5, true,  "林下花径"));
        return g;
    }
}

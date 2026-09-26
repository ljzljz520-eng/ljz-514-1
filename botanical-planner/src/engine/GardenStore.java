package engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import server.Json;

/** 园区数据仓库：内存图 + JSON 文件持久化 */
public class GardenStore {

    private GardenGraph graph = new GardenGraph();
    private final Path dataFile;

    public GardenStore(Path dataFile) {
        this.dataFile = dataFile;
        load();
    }

    public synchronized GardenGraph graph() {
        return graph;
    }

    /** 用内置示例园区替换当前数据并持久化 */
    public synchronized void resetToDefaults() {
        graph = defaultGraph();
        save();
    }

    public synchronized void load() {
        if (dataFile != null && Files.exists(dataFile)) {
            try {
                String content = new String(Files.readAllBytes(dataFile), StandardCharsets.UTF_8);
                graph = parseGraph(Json.parseObject(content));
                return;
            } catch (Exception e) {
                System.err.println("读取园区数据失败，改用内置默认数据: " + e.getMessage());
            }
        }
        graph = defaultGraph();
        save();
    }

    public synchronized void save() {
        if (dataFile == null) return;
        try {
            if (dataFile.getParent() != null) Files.createDirectories(dataFile.getParent());
            Map<String, Object> root = new LinkedHashMap<>();
            List<Object> nodeList = new ArrayList<>();
            for (GardenNode n : graph.nodes()) nodeList.add(n.toMap());
            List<Object> edgeList = new ArrayList<>();
            for (Edge e : graph.edges()) edgeList.add(e.toMap());
            root.put("nodes", nodeList);
            root.put("edges", edgeList);
            Files.write(dataFile, Json.pretty(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("保存园区数据失败: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static GardenGraph parseGraph(Map<String, Object> root) {
        GardenGraph g = new GardenGraph();
        for (Object o : (List<Object>) root.getOrDefault("nodes", new ArrayList<>())) {
            Map<String, Object> m = (Map<String, Object>) o;
            g.addNode(new GardenNode(
                    Json.str(m, "id"),
                    Json.str(m, "name"),
                    NodeType.fromCode(Json.str(m, "type")),
                    Json.integer(m, "x", 0),
                    Json.integer(m, "y", 0),
                    Json.str(m, "description")));
        }
        for (Object o : (List<Object>) root.getOrDefault("edges", new ArrayList<>())) {
            Map<String, Object> m = (Map<String, Object>) o;
            g.addEdge(new Edge(
                    Json.str(m, "id"),
                    Json.str(m, "from"),
                    Json.str(m, "to"),
                    Json.integer(m, "distanceMeters", 0),
                    Json.dbl(m, "slopePercent", 0),
                    Json.bool(m, "strollerFriendly", false),
                    Json.integer(m, "scenicScore", 5)));
        }
        return g;
    }

    /** 内置示例园区：12 个节点、20 段步道 */
    public static GardenGraph defaultGraph() {
        GardenGraph g = new GardenGraph();

        g.addNode(new GardenNode("e1", "南门主入口", NodeType.ENTRANCE, 120, 820,
                "植物园南门，紧邻停车场与游客集散广场"));
        g.addNode(new GardenNode("e2", "东门入口", NodeType.ENTRANCE, 890, 520,
                "东门，靠近公交站台与自行车停放处"));
        g.addNode(new GardenNode("gh1", "热带植物温室", NodeType.GREENHOUSE, 340, 470,
                "恒温高湿，展示热带雨林与食虫植物"));
        g.addNode(new GardenNode("gh2", "多肉植物温室", NodeType.GREENHOUSE, 560, 700,
                "沙生植物主题，仙人掌与多肉专区"));
        g.addNode(new GardenNode("fb1", "郁金香花境", NodeType.FLOWER_BORDER, 260, 640,
                "春季球根花卉主展区，3-5 月最佳"));
        g.addNode(new GardenNode("fb2", "玫瑰月季花境", NodeType.FLOWER_BORDER, 640, 420,
                "欧式规则花境，花香浓郁"));
        g.addNode(new GardenNode("fb3", "水生花境", NodeType.FLOWER_BORDER, 760, 770,
                "鸢尾、再力花与睡莲的水岸花境"));
        g.addNode(new GardenNode("bw1", "观鹭栈道", NodeType.BOARDWALK, 790, 190,
                "伸入湖面的木栈道，常见白鹭栖息"));
        g.addNode(new GardenNode("bw2", "荷风栈桥", NodeType.BOARDWALK, 520, 140,
                "穿越荷花荡的九曲栈桥，夏季赏荷"));
        g.addNode(new GardenNode("r1", "槐荫休息点", NodeType.REST_POINT, 430, 560,
                "大槐树荫下，有座椅与直饮水"));
        g.addNode(new GardenNode("r2", "湖畔茶室", NodeType.REST_POINT, 700, 300,
                "临湖茶室，可品茶观景、使用洗手间"));
        g.addNode(new GardenNode("r3", "梅林休息亭", NodeType.REST_POINT, 470, 850,
                "仿古凉亭，冬季梅花环绕"));

        // id, from, to, 米, 坡度%, 可推车, 景观分
        g.addEdge(new Edge("w01", "e1",  "r1",  150, 1, true,  4));
        g.addEdge(new Edge("w02", "e1",  "fb1", 200, 2, true,  8));
        g.addEdge(new Edge("w03", "r1",  "fb1", 120, 1, true,  5));
        g.addEdge(new Edge("w04", "fb1", "gh1", 180, 4, true,  6));
        g.addEdge(new Edge("w05", "r1",  "gh1", 230, 3, true,  5));
        g.addEdge(new Edge("w06", "gh1", "bw2", 320, 9, false, 7));
        g.addEdge(new Edge("w07", "gh1", "fb2", 250, 5, true,  7));
        g.addEdge(new Edge("w08", "fb2", "bw1", 280, 3, true,  9));
        g.addEdge(new Edge("w09", "fb2", "r2",  150, 1, true,  6));
        g.addEdge(new Edge("w10", "r2",  "bw1", 200, 2, true, 10));
        g.addEdge(new Edge("w11", "bw1", "bw2", 260, 0, true,  9));
        g.addEdge(new Edge("w12", "e2",  "bw1", 190, 2, true,  6));
        g.addEdge(new Edge("w13", "e2",  "fb2", 220, 2, true,  5));
        g.addEdge(new Edge("w14", "fb2", "gh2", 300, 7, false, 5));
        g.addEdge(new Edge("w15", "gh2", "r3",  200, 2, true,  4));
        g.addEdge(new Edge("w16", "gh2", "fb3", 170, 1, true,  8));
        g.addEdge(new Edge("w17", "fb3", "r3",  240, 3, true,  6));
        g.addEdge(new Edge("w18", "fb3", "e2",  300, 2, true,  5));
        g.addEdge(new Edge("w19", "r1",  "gh2", 400, 5, false, 4));
        g.addEdge(new Edge("w20", "gh1", "gh2", 280, 3, true,  4));

        return g;
    }
}

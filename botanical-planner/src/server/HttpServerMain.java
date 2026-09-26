package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import engine.Edge;
import engine.GardenGraph;
import engine.GardenNode;
import engine.GardenStore;
import engine.NodeType;
import engine.Preference;
import engine.RoutePlanner;
import engine.RouteResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * HTTP 服务入口：
 *   GET  /                       前端单页
 *   GET  /api/nodes  POST /api/nodes  PUT/DELETE /api/nodes/{id}
 *   GET  /api/edges  POST /api/edges  PUT/DELETE /api/edges/{id}
 *   POST /api/plan
 *   POST /api/admin/reset
 *
 * 管理类接口通过请求头 X-Admin-Token 校验（默认 garden-admin，可用环境变量 ADMIN_TOKEN 修改）。
 */
public class HttpServerMain {

    private final GardenStore store;
    private final Path webDir;
    private final String adminToken;

    public HttpServerMain(GardenStore store, Path webDir, String adminToken) {
        this.store = store;
        this.webDir = webDir;
        this.adminToken = adminToken;
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::route);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("=====================================================");
        System.out.println(" 植物园游线规划器已启动");
        System.out.println(" 游客界面:  http://localhost:" + port + "/");
        System.out.println(" 管理口令(X-Admin-Token): " + adminToken);
        System.out.println("=====================================================");
    }

    private void route(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();

            if (path.startsWith("/api/")) {
                handleApi(ex, path, method);
            } else {
                serveStatic(ex, path);
            }
        } catch (ApiException ae) {
            sendJson(ex, ae.status, Map.of("error", ae.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, Map.of("error", "服务器内部错误: " + e.getMessage()));
        }
    }

    // ---------------- API 路由 ----------------

    private void handleApi(HttpExchange ex, String path, String method) throws IOException {
        if (path.equals("/api/plan") && method.equals("POST")) {
            plan(ex);
        } else if (path.equals("/api/nodes") && method.equals("GET")) {
            listNodes(ex);
        } else if (path.equals("/api/edges") && method.equals("GET")) {
            listEdges(ex);
        } else if (path.equals("/api/nodes") && method.equals("POST")) {
            requireAdmin(ex); createNode(ex);
        } else if (path.equals("/api/edges") && method.equals("POST")) {
            requireAdmin(ex); createEdge(ex);
        } else if (path.startsWith("/api/nodes/") && method.equals("PUT")) {
            requireAdmin(ex); updateNode(ex, tailId(path));
        } else if (path.startsWith("/api/edges/") && method.equals("PUT")) {
            requireAdmin(ex); updateEdge(ex, tailId(path));
        } else if (path.startsWith("/api/nodes/") && method.equals("DELETE")) {
            requireAdmin(ex); deleteNode(ex, tailId(path));
        } else if (path.startsWith("/api/edges/") && method.equals("DELETE")) {
            requireAdmin(ex); deleteEdge(ex, tailId(path));
        } else if (path.equals("/api/admin/reset") && method.equals("POST")) {
            requireAdmin(ex); resetData(ex);
        } else if (path.equals("/api/health") && method.equals("GET")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", "ok");
            m.put("nodes", store.graph().nodeCount());
            m.put("edges", store.graph().edgeCount());
            sendJson(ex, 200, m);
        } else {
            throw new ApiException(404, "接口不存在: " + method + " " + path);
        }
    }

    private String tailId(String path) {
        String id = path.substring(path.lastIndexOf('/') + 1);
        return URLDecoder.decode(id, StandardCharsets.UTF_8);
    }

    private void requireAdmin(HttpExchange ex) {
        String token = ex.getRequestHeaders().getFirst("X-Admin-Token");
        if (token == null || !token.equals(adminToken)) {
            throw new ApiException(401, "管理员口令缺失或不正确（请在请求头 X-Admin-Token 中提供）");
        }
    }

    // ---------------- 查询与规划 ----------------

    private void listNodes(HttpExchange ex) throws IOException {
        List<Object> list = new ArrayList<>();
        for (GardenNode n : store.graph().nodes()) list.add(n.toMap());
        sendJson(ex, 200, Map.of("nodes", list));
    }

    private void listEdges(HttpExchange ex) throws IOException {
        List<Object> list = new ArrayList<>();
        for (Edge e : store.graph().edges()) list.add(e.toMap());
        sendJson(ex, 200, Map.of("edges", list));
    }

    private void plan(HttpExchange ex) throws IOException {
        Map<String, Object> body = readJsonBody(ex);
        String start = required(body, "startId");
        String goal = required(body, "goalId");
        if (start.equals(goal)) throw new ApiException(400, "起点和终点不能相同");
        String prefCode = Json.str(body, "preference");
        if (prefCode == null) prefCode = "shortest";
        Preference pref;
        try {
            pref = Preference.fromCode(prefCode);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, e.getMessage());
        }
        boolean withStroller = Json.bool(body, "withStroller", false);

        RoutePlanner planner = new RoutePlanner(store.graph());
        RouteResult result = planner.plan(start, goal, pref, withStroller);
        sendJson(ex, 200, result.toMap());
    }

    // ---------------- 管理员：节点维护 ----------------

    private void createNode(HttpExchange ex) throws IOException {
        Map<String, Object> body = readJsonBody(ex);
        String id = required(body, "id").trim();
        validateId(id);
        if (store.graph().node(id) != null) throw new ApiException(409, "节点 id 已存在: " + id);
        GardenNode node = readNode(id, body);
        store.graph().addNode(node);
        store.save();
        sendJson(ex, 201, node.toMap());
    }

    private void updateNode(HttpExchange ex, String id) throws IOException {
        if (store.graph().node(id) == null) throw new ApiException(404, "节点不存在: " + id);
        Map<String, Object> body = readJsonBody(ex);
        // 就地替换属性，id 不变，所有相连步道保持关联
        GardenNode updated = readNode(id, body);
        store.graph().replaceNode(updated);
        store.save();
        sendJson(ex, 200, updated.toMap());
    }

    private void deleteNode(HttpExchange ex, String id) throws IOException {
        if (store.graph().node(id) == null) throw new ApiException(404, "节点不存在: " + id);
        store.graph().removeNode(id); // 连带删除相连步道
        store.save();
        sendJson(ex, 200, Map.of("deleted", id));
    }

    private GardenNode readNode(String id, Map<String, Object> body) {
        String name = required(body, "name").trim();
        if (name.isEmpty()) throw new ApiException(400, "节点名称不能为空");
        String typeCode = required(body, "type");
        NodeType type;
        try {
            type = NodeType.fromCode(typeCode);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, e.getMessage());
        }
        int x = Json.integer(body, "x", 100);
        int y = Json.integer(body, "y", 100);
        if (x < 0 || x > 1000 || y < 0 || y > 1000) {
            throw new ApiException(400, "坐标 x/y 必须在 0-1000 之间");
        }
        String desc = Json.str(body, "description");
        return new GardenNode(id, name, type, x, y, desc);
    }

    // ---------------- 管理员：步道维护 ----------------

    private void createEdge(HttpExchange ex) throws IOException {
        Map<String, Object> body = readJsonBody(ex);
        String id = required(body, "id").trim();
        validateId(id);
        if (store.graph().edge(id) != null) throw new ApiException(409, "步道 id 已存在: " + id);
        Edge edge = readEdge(id, body);
        try {
            store.graph().addEdge(edge);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, e.getMessage());
        }
        store.save();
        sendJson(ex, 201, edge.toMap());
    }

    private void updateEdge(HttpExchange ex, String id) throws IOException {
        if (store.graph().edge(id) == null) throw new ApiException(404, "步道不存在: " + id);
        Edge edge = readEdge(id, readJsonBody(ex));
        store.graph().removeEdge(id);
        try {
            store.graph().addEdge(edge);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, e.getMessage());
        }
        store.save();
        sendJson(ex, 200, edge.toMap());
    }

    private void deleteEdge(HttpExchange ex, String id) throws IOException {
        if (store.graph().edge(id) == null) throw new ApiException(404, "步道不存在: " + id);
        store.graph().removeEdge(id);
        store.save();
        sendJson(ex, 200, Map.of("deleted", id));
    }

    private Edge readEdge(String id, Map<String, Object> body) {
        String from = required(body, "from");
        String to = required(body, "to");
        if (from.equals(to)) throw new ApiException(400, "步道的两个端点不能是同一节点");
        int distance = Json.integer(body, "distanceMeters", -1);
        if (distance <= 0 || distance > 5000) throw new ApiException(400, "步行距离必须在 1-5000 米之间");
        double slope = Json.dbl(body, "slopePercent", 0);
        if (slope < -40 || slope > 40) throw new ApiException(400, "坡度应在 -40% 到 40% 之间");
        boolean stroller = Json.bool(body, "strollerFriendly", false);
        int scenic = Json.integer(body, "scenicScore", 5);
        if (scenic < 0 || scenic > 10) throw new ApiException(400, "景观评分应在 0-10 之间");
        return new Edge(id, from, to, distance, slope, stroller, scenic);
    }

    private void resetData(HttpExchange ex) throws IOException {
        store.resetToDefaults();
        GardenGraph fresh = store.graph();
        sendJson(ex, 200, Map.of("reset", true, "nodes", fresh.nodeCount(), "edges", fresh.edgeCount()));
    }

    // ---------------- 工具 ----------------

    private static String required(Map<String, Object> body, String key) {
        String v = Json.str(body, key);
        if (v == null || v.trim().isEmpty()) throw new ApiException(400, "缺少必填字段: " + key);
        return v.trim();
    }

    private static void validateId(String id) {
        if (!id.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new ApiException(400, "id 只能包含字母、数字、下划线和连字符（1-32 位）");
        }
    }

    private static Map<String, Object> readJsonBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.isEmpty()) throw new ApiException(400, "请求体不能为空");
            Object parsed = Json.parse(text);
            if (!(parsed instanceof Map)) throw new ApiException(400, "请求体必须是 JSON 对象");
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) parsed;
            return m;
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "JSON 解析失败: " + e.getMessage());
        }
    }

    private void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] data = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }

    private void serveStatic(HttpExchange ex, String path) throws IOException {
        if (path.equals("/") || path.isEmpty()) path = "/index.html";
        // 防路径穿越
        if (path.contains("..")) throw new ApiException(400, "非法路径");
        Path file = webDir.resolve(path.substring(1)).normalize();
        if (!file.startsWith(webDir) || !Files.exists(file) || Files.isDirectory(file)) {
            sendJson(ex, 404, Map.of("error", "资源不存在: " + path));
            return;
        }
        byte[] data = Files.readAllBytes(file);
        String type = contentType(file.getFileName().toString());
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    static class ApiException extends RuntimeException {
        final int status;
        ApiException(int status, String msg) {
            super(msg);
            this.status = status;
        }
    }

    // ---------------- main ----------------

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String token = System.getenv().getOrDefault("ADMIN_TOKEN", "garden-admin");
        Path dataFile = Paths.get(System.getenv().getOrDefault("DATA_FILE", "data/garden.json"));
        Path webDir = Paths.get(System.getenv().getOrDefault("WEB_DIR", "web"));

        GardenStore store = new GardenStore(dataFile);
        new HttpServerMain(store, webDir, token).start(port);
    }
}

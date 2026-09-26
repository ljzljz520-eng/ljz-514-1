package com.botanic.garden.http;

import com.botanic.garden.model.Edge;
import com.botanic.garden.model.Garden;
import com.botanic.garden.model.GardenNode;
import com.botanic.garden.model.NodeType;
import com.botanic.garden.service.RoutePlanner;
import com.botanic.garden.store.DataStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/** REST API + 静态资源服务。 */
public class WebServer {

    private final DataStore store;
    private final RoutePlanner planner = new RoutePlanner();
    private final String adminToken;
    private final Path staticRoot;

    public WebServer(DataStore store, String adminToken, Path staticRoot) {
        this.store = store;
        this.adminToken = adminToken;
        this.staticRoot = staticRoot;
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::route);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("[WebServer] 植物园游线规划器已启动：http://localhost:" + port);
        System.out.println("[WebServer] 管理口令（X-Admin-Token）：" + adminToken);
    }

    private void route(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());

            if (path.equals("/api/garden") && method.equals("GET")) { handleGarden(ex); return; }
            if (path.equals("/api/route") && method.equals("GET")) { handleRoute(ex, query); return; }
            if (path.equals("/api/admin/nodes") && method.equals("POST")) { requireAdmin(ex); handleCreateNode(ex); return; }
            if (path.matches("/api/admin/nodes/[^/]+") && method.equals("PUT")) { requireAdmin(ex); handleUpdateNode(ex, path); return; }
            if (path.matches("/api/admin/nodes/[^/]+") && method.equals("DELETE")) { requireAdmin(ex); handleDeleteNode(ex, path); return; }
            if (path.equals("/api/admin/edges") && method.equals("POST")) { requireAdmin(ex); handleCreateEdge(ex); return; }
            if (path.matches("/api/admin/edges/[^/]+") && method.equals("PUT")) { requireAdmin(ex); handleUpdateEdge(ex, path); return; }
            if (path.matches("/api/admin/edges/[^/]+") && method.equals("DELETE")) { requireAdmin(ex); handleDeleteEdge(ex, path); return; }

            if (method.equals("GET")) { serveStatic(ex, path); return; }
            sendJson(ex, 404, error("接口不存在"));
        } catch (ApiException e) {
            sendJson(ex, e.status, error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            sendJson(ex, 400, error(e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, error("服务器内部错误：" + e.getMessage()));
        } finally {
            ex.close();
        }
    }

    // ---------------- 游客接口 ----------------

    private void handleGarden(HttpExchange ex) throws IOException {
        Garden g = store.garden();
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (GardenNode n : g.getNodes()) nodes.add(n.toJson());
        List<Map<String, Object>> edges = new ArrayList<>();
        for (Edge e : g.getEdges()) edges.add(e.toJson());
        List<Map<String, Object>> modes = new ArrayList<>();
        for (RoutePlanner.Mode m : RoutePlanner.Mode.values()) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("value", m.name());
            mm.put("label", m.label);
            mm.put("description", m.description);
            modes.add(mm);
        }
        body.put("nodes", nodes);
        body.put("edges", edges);
        body.put("modes", modes);
        sendJson(ex, 200, body);
    }

    private void handleRoute(HttpExchange ex, Map<String, String> query) throws IOException {
        String start = query.get("start");
        String end = query.get("end");
        if (start == null || start.isBlank()) throw new IllegalArgumentException("缺少参数：start（起点）");
        if (end == null || end.isBlank()) throw new IllegalArgumentException("缺少参数：end（终点）");
        Double maxSlope = null;
        if (query.containsKey("maxSlope") && !query.get("maxSlope").isBlank()) {
            try { maxSlope = Double.parseDouble(query.get("maxSlope")); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("maxSlope 必须是数字"); }
        }
        RoutePlanner.Preferences prefs = RoutePlanner.Preferences.of(query.get("mode"), maxSlope);
        RoutePlanner.PlanResult r = planner.plan(store.garden(), start, end, prefs);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("found", r.found);
        body.put("mode", r.mode.name());
        body.put("modeLabel", r.mode.label);
        if (!r.found) {
            body.put("reason", r.reason);
            sendJson(ex, 200, body);
            return;
        }
        body.put("totalDistanceMeters", Math.round(r.totalDistance));
        body.put("estimatedMinutes", r.estimatedMinutes);
        body.put("maxSlopePercent", r.maxSlopeOnRoute);
        body.put("allStrollerFriendly", r.allStrollerFriendly);
        body.put("tips", r.tips);

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (GardenNode n : r.nodes) nodes.add(n.toJson());
        body.put("nodes", nodes);

        List<Map<String, Object>> segments = new ArrayList<>();
        for (RoutePlanner.Segment s : r.segments) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("order", s.order);
            sm.put("from", s.from.toJson());
            sm.put("to", s.to.toJson());
            sm.put("edgeId", s.edge.getId());
            sm.put("edgeName", s.edge.getName());
            sm.put("distanceMeters", Math.round(s.distance));
            sm.put("slopePercent", s.slope);
            sm.put("strollerFriendly", s.strollerFriendly);
            sm.put("instruction", s.instruction);
            segments.add(sm);
        }
        body.put("segments", segments);
        sendJson(ex, 200, body);
    }

    // ---------------- 管理接口 ----------------

    private void handleCreateNode(HttpExchange ex) throws IOException {
        Map<String, Object> body = readJsonBody(ex);
        String name = requireText(body, "name", "节点名称");
        NodeType type = NodeType.fromString(Json.asString(body.get("type")));
        if (type == null) throw new IllegalArgumentException("type 必须是 GREENHOUSE / FLOWER_BORDER / LAKESIDE_WALK / REST_AREA");
        String description = optText(body, "description", "");
        double x = optNum(body, "x", 500);
        double y = optNum(body, "y", 400);
        GardenNode node = new GardenNode(DataStore.newId("n"), name, type, description, x, y);
        synchronized (store) {
            store.garden().addNode(node);
            store.save();
        }
        sendJson(ex, 201, node.toJson());
    }

    private void handleUpdateNode(HttpExchange ex, String path) throws IOException {
        String id = lastSegment(path);
        Map<String, Object> body = readJsonBody(ex);
        synchronized (store) {
            GardenNode node = store.garden().getNode(id);
            if (node == null) throw new ApiException(404, "节点不存在：" + id);
            if (body.containsKey("name")) node.setName(requireText(body, "name", "节点名称"));
            if (body.containsKey("type")) {
                NodeType t = NodeType.fromString(Json.asString(body.get("type")));
                if (t == null) throw new IllegalArgumentException("非法的节点类型");
                node.setType(t);
            }
            if (body.containsKey("description")) node.setDescription(optText(body, "description", ""));
            if (body.containsKey("x")) node.setX(optNum(body, "x", node.getX()));
            if (body.containsKey("y")) node.setY(optNum(body, "y", node.getY()));
            store.save();
            sendJson(ex, 200, node.toJson());
        }
    }

    private void handleDeleteNode(HttpExchange ex, String path) throws IOException {
        String id = lastSegment(path);
        synchronized (store) {
            if (store.garden().removeNode(id) == null) throw new ApiException(404, "节点不存在：" + id);
            store.save();
        }
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("deleted", id);
        sendJson(ex, 200, ok);
    }

    private void handleCreateEdge(HttpExchange ex) throws IOException {
        Map<String, Object> body = readJsonBody(ex);
        String from = requireText(body, "fromId", "起点节点");
        String to = requireText(body, "toId", "终点节点");
        if (from.equals(to)) throw new IllegalArgumentException("路段两端不能是同一节点");
        double dist = optNum(body, "distanceMeters", -1);
        if (dist <= 0) throw new IllegalArgumentException("distanceMeters（步行距离）必须大于 0");
        double slope = optNum(body, "slopePercent", 0);
        if (slope < 0 || slope > 30) throw new IllegalArgumentException("slopePercent（坡度）需在 0-30 之间");
        boolean stroller = Boolean.TRUE.equals(Json.asBoolean(body.get("strollerFriendly")));
        String name = optText(body, "name", "");
        synchronized (store) {
            Garden g = store.garden();
            if (!g.hasNode(from)) throw new IllegalArgumentException("节点不存在：" + from);
            if (!g.hasNode(to)) throw new IllegalArgumentException("节点不存在：" + to);
            if (g.findEdgeBetween(from, to) != null) throw new IllegalArgumentException("两个节点之间已存在路段");
            Edge edge = new Edge(DataStore.newId("e"), from, to, dist, slope, stroller, name);
            g.addEdge(edge);
            store.save();
            sendJson(ex, 201, edge.toJson());
        }
    }

    private void handleUpdateEdge(HttpExchange ex, String path) throws IOException {
        String id = lastSegment(path);
        Map<String, Object> body = readJsonBody(ex);
        synchronized (store) {
            Edge edge = store.garden().getEdge(id);
            if (edge == null) throw new ApiException(404, "路段不存在：" + id);
            if (body.containsKey("distanceMeters")) {
                double d = optNum(body, "distanceMeters", -1);
                if (d <= 0) throw new IllegalArgumentException("步行距离必须大于 0");
                edge.setDistanceMeters(d);
            }
            if (body.containsKey("slopePercent")) {
                double s = optNum(body, "slopePercent", -1);
                if (s < 0 || s > 30) throw new IllegalArgumentException("坡度需在 0-30 之间");
                edge.setSlopePercent(s);
            }
            if (body.containsKey("strollerFriendly")) {
                edge.setStrollerFriendly(Boolean.TRUE.equals(Json.asBoolean(body.get("strollerFriendly"))));
            }
            if (body.containsKey("name")) edge.setName(optText(body, "name", ""));
            store.save();
            sendJson(ex, 200, edge.toJson());
        }
    }

    private void handleDeleteEdge(HttpExchange ex, String path) throws IOException {
        String id = lastSegment(path);
        synchronized (store) {
            if (store.garden().removeEdge(id) == null) throw new ApiException(404, "路段不存在：" + id);
            store.save();
        }
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("deleted", id);
        sendJson(ex, 200, ok);
    }

    // ---------------- 工具 ----------------

    private void requireAdmin(HttpExchange ex) {
        String token = ex.getRequestHeaders().getFirst("X-Admin-Token");
        if (token == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                adminToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(401, "未授权：请在请求头携带正确的 X-Admin-Token");
        }
    }

    private Map<String, Object> readJsonBody(HttpExchange ex) throws IOException {
        String text = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (text.isBlank()) throw new IllegalArgumentException("请求体不能为空");
        return Json.parseObject(text);
    }

    private String requireText(Map<String, Object> body, String key, String label) {
        String v = Json.asString(body.get(key));
        if (v == null || v.isBlank()) throw new IllegalArgumentException("缺少字段：" + key + "（" + label + "）");
        return v.trim();
    }

    private String optText(Map<String, Object> body, String key, String def) {
        String v = Json.asString(body.get(key));
        return v == null ? def : v.trim();
    }

    private double optNum(Map<String, Object> body, String key, double def) {
        Double v = Json.asDouble(body.get(key));
        return v == null ? def : v;
    }

    private String lastSegment(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return m;
    }

    private void sendJson(HttpExchange ex, int status, Map<String, Object> body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void serveStatic(HttpExchange ex, String path) throws IOException {
        if (path.equals("/")) path = "/index.html";
        if (path.contains("..")) { sendJson(ex, 403, error("非法路径")); return; }
        Path file = staticRoot.resolve(path.substring(1)).normalize();
        if (!file.startsWith(staticRoot) || !Files.isRegularFile(file)) {
            sendJson(ex, 404, error("页面不存在"));
            return;
        }
        String name = file.getFileName().toString();
        String type = name.endsWith(".html") ? "text/html; charset=utf-8"
                : name.endsWith(".css") ? "text/css; charset=utf-8"
                : name.endsWith(".js") ? "application/javascript; charset=utf-8"
                : name.endsWith(".svg") ? "image/svg+xml"
                : "application/octet-stream";
        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isBlank()) return map;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            String k = i < 0 ? pair : pair.substring(0, i);
            String v = i < 0 ? "" : pair.substring(i + 1);
            map.put(urlDecode(k), urlDecode(v));
        }
        return map;
    }

    private String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static final class ApiException extends RuntimeException {
        final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
    }
}

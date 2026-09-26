package com.botanic.garden.service;

import com.botanic.garden.model.Edge;
import com.botanic.garden.model.Garden;
import com.botanic.garden.model.GardenNode;
import com.botanic.garden.model.NodeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 路线规划器：按游览偏好对路段加权，用 Dijkstra 求最优路线。
 *
 * 偏好模式：
 *   shortest  —— 最短距离
 *   easy      —— 轻松少爬坡（坡度惩罚，可设最大坡度）
 *   stroller  —— 推车友好（只走适合推车的路段）
 *   scenic    —— 观景优先（偏向经过温室/花境/湖边栈道）
 *   flat      —— 坡度最小
 */
public class RoutePlanner {

    public enum Mode {
        SHORTEST("最短距离", "总步行距离最短的路线"),
        EASY("轻松少爬坡", "优先平缓路段，坡度越大惩罚越高"),
        STROLLER("推车友好", "全程只走适合婴儿车/轮椅通行的路段"),
        SCENIC("观景优先", "尽量串联温室、花境与湖边栈道"),
        FLAT("坡度最小", "让全程累计爬升最小，距离其次");

        public final String label;
        public final String description;
        Mode(String label, String description) { this.label = label; this.description = description; }

        public static Mode of(String s) {
            if (s == null || s.isBlank()) return SHORTEST;
            for (Mode m : values()) if (m.name().equalsIgnoreCase(s.trim())) return m;
            return null;
        }
    }

    /** 游览偏好。 */
    public static final class Preferences {
        public Mode mode = Mode.SHORTEST;
        public Double maxSlope; // 仅 EASY 模式生效：超过该坡度的路段不可通行

        public static Preferences of(String modeStr, Double maxSlope) {
            Preferences p = new Preferences();
            Mode m = Mode.of(modeStr);
            if (m == null) throw new IllegalArgumentException("未知的游览偏好：" + modeStr);
            p.mode = m;
            p.maxSlope = maxSlope;
            return p;
        }
    }

    /** 规划结果。 */
    public static final class PlanResult {
        public boolean found;
        public String reason;                 // 未找到时的原因
        public Mode mode;
        public double totalDistance;          // 米
        public double maxSlopeOnRoute;        // %
        public boolean allStrollerFriendly;
        public int estimatedMinutes;
        public List<Segment> segments = new ArrayList<>();
        public List<GardenNode> nodes = new ArrayList<>(); // 含起终点，按顺序
        public List<String> tips = new ArrayList<>();
    }

    /** 一段路的说明。 */
    public static final class Segment {
        public int order;
        public GardenNode from;
        public GardenNode to;
        public Edge edge;
        public double distance;
        public double slope;
        public boolean strollerFriendly;
        public String instruction;
    }

    public PlanResult plan(Garden garden, String startId, String endId, Preferences prefs) {
        PlanResult result = new PlanResult();
        result.mode = prefs.mode;

        GardenNode start = garden.getNode(startId);
        GardenNode end = garden.getNode(endId);
        if (start == null) return fail(result, "起点不存在：" + startId);
        if (end == null) return fail(result, "终点不存在：" + endId);
        if (startId.equals(endId)) return fail(result, "起点和终点相同，无需规划路线");

        // 1) 按偏好过滤不可通行路段
        List<Edge> removed = new ArrayList<>();
        Map<String, List<Edge>> adj = buildAdjacency(garden, prefs, removed);

        // 2) Dijkstra
        Map<String, Double> dist = new HashMap<>();
        Map<String, Edge> prevEdge = new HashMap<>();
        Map<String, String> prevNode = new HashMap<>();
        dist.put(startId, 0.0);
        PriorityQueue<String[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> Double.parseDouble(a[1])));
        pq.add(new String[]{startId, "0"});
        Map<String, Double> best = new HashMap<>();

        while (!pq.isEmpty()) {
            String[] cur = pq.poll();
            String u = cur[0];
            double d = Double.parseDouble(cur[1]);
            if (best.containsKey(u) && d > best.get(u)) continue;
            best.put(u, d);
            if (u.equals(endId)) break;
            for (Edge e : adj.getOrDefault(u, List.of())) {
                String v = e.otherEnd(u);
                if (v == null) continue;
                double w = effort(e, garden, prefs.mode);
                double nd = d + w;
                if (nd < dist.getOrDefault(v, Double.POSITIVE_INFINITY)) {
                    dist.put(v, nd);
                    prevEdge.put(v, e);
                    prevNode.put(v, u);
                    pq.add(new String[]{v, String.valueOf(nd)});
                }
            }
        }

        if (!prevNode.containsKey(endId)) {
            String why = removed.isEmpty()
                    ? "两点之间没有连通的步行路段"
                    : "当前偏好下无可通行路线（已排除 " + removed.size() + " 段不符合条件的路段），"
                      + (prefs.mode == Mode.STROLLER ? "请确认沿途路段均标记为适合推车" : "可尝试放宽最大坡度限制");
            return fail(result, why);
        }

        // 3) 回溯路径
        List<String> nodeIds = new ArrayList<>();
        List<Edge> pathEdges = new ArrayList<>();
        String cur = endId;
        nodeIds.add(cur);
        while (!cur.equals(startId)) {
            Edge e = prevEdge.get(cur);
            pathEdges.add(e);
            cur = prevNode.get(cur);
            nodeIds.add(cur);
        }
        Collections.reverse(nodeIds);
        Collections.reverse(pathEdges);

        // 4) 组装结果
        double total = 0, maxSlope = 0;
        boolean allStroller = true;
        for (int i = 0; i < pathEdges.size(); i++) {
            Edge e = pathEdges.get(i);
            GardenNode from = garden.getNode(nodeIds.get(i));
            GardenNode to = garden.getNode(nodeIds.get(i + 1));
            Segment seg = new Segment();
            seg.order = i + 1;
            seg.from = from;
            seg.to = to;
            seg.edge = e;
            seg.distance = e.getDistanceMeters();
            seg.slope = e.getSlopePercent();
            seg.strollerFriendly = e.isStrollerFriendly();
            seg.instruction = buildInstruction(seg);
            result.segments.add(seg);
            total += e.getDistanceMeters();
            maxSlope = Math.max(maxSlope, e.getSlopePercent());
            if (!e.isStrollerFriendly()) allStroller = false;
        }
        for (String id : nodeIds) result.nodes.add(garden.getNode(id));

        result.found = true;
        result.totalDistance = total;
        result.maxSlopeOnRoute = maxSlope;
        result.allStrollerFriendly = allStroller;
        result.estimatedMinutes = estimateMinutes(result.segments, prefs.mode);
        result.tips = buildTips(result, prefs);
        return result;
    }

    private PlanResult fail(PlanResult r, String reason) {
        r.found = false;
        r.reason = reason;
        return r;
    }

    /** 构建按偏好过滤后的邻接表，被排除的边记入 removed。 */
    private Map<String, List<Edge>> buildAdjacency(Garden garden, Preferences prefs, List<Edge> removed) {
        Map<String, List<Edge>> adj = new LinkedHashMap<>();
        for (GardenNode n : garden.getNodes()) adj.put(n.getId(), new ArrayList<>());
        for (Edge e : garden.getEdges()) {
            boolean pass = switch (prefs.mode) {
                case STROLLER -> e.isStrollerFriendly();
                case EASY -> prefs.maxSlope == null || e.getSlopePercent() <= prefs.maxSlope;
                default -> true;
            };
            if (!pass) { removed.add(e); continue; }
            if (adj.containsKey(e.getFromId()) && adj.containsKey(e.getToId())) {
                adj.get(e.getFromId()).add(e);
                adj.get(e.getToId()).add(e);
            }
        }
        return adj;
    }

    /** 路段代价：距离为基准，按偏好加权。 */
    private double effort(Edge e, Garden garden, Mode mode) {
        double d = e.getDistanceMeters();
        double slope = e.getSlopePercent();
        return switch (mode) {
            case SHORTEST -> d;
            case EASY -> d * (1 + slope / 4.0);
            case STROLLER -> d * (1 + slope / 8.0);
            case FLAT -> d * slope + d * 0.05; // 累计爬升优先，距离其次
            case SCENIC -> {
                // 途经景点（温室/花境/湖边栈道）的路段大幅打折，纯休息点路段加价
                double factor = 1.0;
                GardenNode a = garden.getNode(e.getFromId());
                GardenNode b = garden.getNode(e.getToId());
                if (isScenic(a)) factor -= 0.4;
                if (isScenic(b)) factor -= 0.4;
                if (a != null && a.getType() == NodeType.REST_AREA) factor += 0.3;
                if (b != null && b.getType() == NodeType.REST_AREA) factor += 0.3;
                yield d * Math.max(factor, 0.15);
            }
        };
    }

    private boolean isScenic(GardenNode n) {
        return n != null && n.getType() != NodeType.REST_AREA;
    }

    private String buildInstruction(Segment seg) {
        StringBuilder sb = new StringBuilder();
        sb.append("从「").append(seg.from.getName()).append("」出发");
        if (seg.edge.getName() != null && !seg.edge.getName().isBlank()) {
            sb.append("，沿").append(seg.edge.getName());
        }
        sb.append("步行 ").append(Math.round(seg.distance)).append(" 米");
        if (seg.slope > 0) {
            sb.append("，坡度约 ").append(trimNum(seg.slope)).append("%");
            if (seg.slope >= 8) sb.append("（较陡，请注意脚下）");
        } else {
            sb.append("，路面平坦");
        }
        sb.append(seg.strollerFriendly ? "，可推行婴儿车" : "，不适合推车通行");
        sb.append("，到达「").append(seg.to.getName()).append("」");
        if (seg.to.getType() == NodeType.REST_AREA) sb.append("，可在此休息");
        sb.append("。");
        return sb.toString();
    }

    private int estimateMinutes(List<Segment> segments, Mode mode) {
        double speed = switch (mode) {
            case STROLLER -> 60.0; // 推车较慢，米/分钟
            case EASY, FLAT -> 70.0;
            default -> 80.0;
        };
        double minutes = 0;
        for (Segment s : segments) {
            minutes += s.distance / speed;
            minutes += s.distance * Math.max(s.slope, 0) / 100.0 * 0.5; // 坡度加时
        }
        return Math.max(1, (int) Math.round(minutes));
    }

    private List<String> buildTips(PlanResult r, Preferences prefs) {
        List<String> tips = new ArrayList<>();
        if (r.mode == Mode.STROLLER && r.allStrollerFriendly) {
            tips.add("全程路段均适合推车通行。");
        } else if (!r.allStrollerFriendly) {
            tips.add("部分路段不适合推车，带婴儿车或轮椅的游客请留意。");
        }
        if (r.maxSlopeOnRoute >= 8) {
            tips.add("路线包含较陡路段（最大坡度 " + trimNum(r.maxSlopeOnRoute) + "%），建议穿防滑鞋。");
        }
        if (r.mode == Mode.SCENIC) {
            long scenic = r.nodes.stream().filter(this::isScenic).count();
            tips.add("本路线途经 " + scenic + " 处景点，适合慢慢游览。");
        }
        if (r.totalDistance > 1500) {
            tips.add("全程较长，建议合理安排体力，沿途休息点可驻足。");
        }
        return tips;
    }

    private String trimNum(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}

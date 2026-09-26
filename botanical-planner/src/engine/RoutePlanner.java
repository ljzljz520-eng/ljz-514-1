package engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 游线规划核心。
 *
 * 使用 Dijkstra 求单源最短路，权重随游客偏好变化：
 *  - SHORTEST     ：权重 = 距离（米）
 *  - LEAST_SLOPE ：权重 = 距离 × (1 + 坡度惩罚)，坡越陡代价越大
 *  - STROLLER    ：仅可走推车友好步道（不可推车的边直接排除）
 *  - SCENIC      ：权重 = 距离 ÷ (0.5 + 景观分/10)，好风景“抵消”距离，
 *                  等距时优先穿过花境、栈道等高景观段
 *
 * 所有权重均为非负，Dijkstra 适用。
 */
public class RoutePlanner {

    /** 每 1% 坡度的距离惩罚系数 */
    private static final double SLOPE_PENALTY_PER_PERCENT = 0.08;
    /** 陡坡阈值（%），超过会在结果里给出提示 */
    private static final double STEEP_SLOPE = 8.0;
    /** 平均步行速度：米/分钟（园区观光慢走） */
    private static final double WALK_METERS_PER_MINUTE = 65.0;

    private final GardenGraph graph;

    public RoutePlanner(GardenGraph graph) {
        this.graph = graph;
    }

    public RouteResult plan(String startId, String goalId, Preference preference, boolean withStroller) {
        RouteResult result = new RouteResult();
        result.preference = preference;

        GardenNode start = graph.node(startId);
        GardenNode goal = graph.node(goalId);
        if (start == null) throw new IllegalArgumentException("起点不存在: " + startId);
        if (goal == null) throw new IllegalArgumentException("终点不存在: " + goalId);

        // 1) Dijkstra：dist 为累计权重，prev 记录最优前驱边
        Map<String, Double> dist = new HashMap<>();
        Map<String, Edge> prev = new HashMap<>();
        for (GardenNode n : graph.nodes()) dist.put(n.id, Double.POSITIVE_INFINITY);
        dist.put(startId, 0.0);

        PriorityQueue<String> queue =
                new PriorityQueue<>(Comparator.comparingDouble(dist::get));
        queue.add(startId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(goalId)) break;
            double curDist = dist.get(current);
            // 同一节点可能在堆中存在旧条目，跳过已过期的
            if (Double.isInfinite(curDist)) continue;

            for (Edge edge : graph.edgesOf(current)) {
                String next = edge.otherEnd(current);
                double w = weight(edge, preference, withStroller);
                if (Double.isInfinite(w)) continue; // 该偏好下不可通行
                double nd = curDist + w;
                if (nd < dist.get(next)) {
                    dist.put(next, nd);
                    prev.put(next, edge);
                    queue.add(next);
                }
            }
        }

        // 2) 不可达
        if (Double.isInfinite(dist.get(goalId))) {
            result.found = false;
            if (preference == Preference.STROLLER || withStroller) {
                result.message = "仅靠推车友好步道无法从“" + start.name + "”到达“" + goal.name
                        + "”。请取消“携带推车”选项，或联系园区工作人员。";
            } else {
                result.message = "“" + start.name + "”与“" + goal.name + "”之间当前没有连通步道。";
            }
            return result;
        }

        // 3) 回溯路径（得到的边是 goal -> start 方向，需反转）
        List<Edge> reversed = new ArrayList<>();
        String cursor = goalId;
        while (!cursor.equals(startId)) {
            Edge e = prev.get(cursor);
            reversed.add(e);
            cursor = e.from.equals(cursor) ? e.to : e.from;
        }
        List<Edge> pathEdges = new ArrayList<>(reversed);
        java.util.Collections.reverse(pathEdges);

        // 4) 组装分段与统计
        result.found = true;
        String prevId = startId;
        result.nodeIds.add(startId);

        int totalDist = 0;
        int climb = 0;

        for (Edge e : pathEdges) {
            String fromId = prevId;
            String toId = e.otherEnd(fromId);
            GardenNode fromNode = graph.node(fromId);
            GardenNode toNode = graph.node(toId);

            String bearing = bearing(fromNode, toNode);
            int segMinutes = (int) Math.max(1, Math.round(e.distanceMeters / WALK_METERS_PER_MINUTE));
            String instruction = buildInstruction(fromNode, toNode, e, bearing, segMinutes);

            result.segments.add(new Segment(
                    e.id, fromId, fromNode.name, toId, toNode.name,
                    e.distanceMeters, e.slopePercent, e.strollerFriendly,
                    e.scenicScore, bearing, instruction, segMinutes));

            totalDist += e.distanceMeters;
            if (e.slopePercent > 0) {
                climb += (int) Math.round(e.distanceMeters * e.slopePercent / 100.0);
            }
            result.nodeIds.add(toId);
            prevId = toId;
        }

        result.totalDistanceMeters = totalDist;
        result.totalMinutes = (int) Math.round(totalDist / WALK_METERS_PER_MINUTE);
        result.climbMeters = climb;
        result.message = "已为您规划" + preference.label() + "路线，共 "
                + result.segments.size() + " 段。";

        // 5) 提示信息
        boolean hasNonStroller = pathEdges.stream().anyMatch(e -> !e.strollerFriendly);
        if (hasNonStroller && !withStroller) {
            result.warnings.add("路线包含不适合推车的路段；如需婴儿车或轮椅通行，"
                    + "请勾选“携带推车”后重新规划。");
        }
        long steepCount = pathEdges.stream().filter(e -> e.slopePercent >= STEEP_SLOPE).count();
        if (steepCount > 0) {
            result.warnings.add("有 " + steepCount + " 段坡度达到 " + (int) STEEP_SLOPE
                    + "% 以上，请注意脚下、量力而行。");
        }
        // 起点终点本身的休息点不计“途经”
        long passRest = result.nodeIds.subList(1, Math.max(1, result.nodeIds.size() - 1)).stream()
                .filter(id -> graph.node(id).type == NodeType.REST_POINT)
                .count();
        if (result.totalDistanceMeters >= 1200 && passRest == 0) {
            result.warnings.add("全程超过 1.2 公里且中途没有休息点，建议自备饮水。");
        }

        return result;
    }

    /** 单条步道在给定偏好下的规划权重；Infinity 表示不可通行 */
    private double weight(Edge e, Preference pref, boolean withStroller) {
        // 携带推车时（无论什么偏好）都只能走推车友好步道
        if (withStroller && !e.strollerFriendly) return Double.POSITIVE_INFINITY;

        switch (pref) {
            case SHORTEST:
                return e.distanceMeters;
            case LEAST_SLOPE: {
                double penalty = 1.0 + SLOPE_PENALTY_PER_PERCENT * Math.max(0, e.slopePercent);
                // 下坡（负坡度）代价基本等同于平路，稍给一点系数避免危险陡坡被选中
                if (e.slopePercent < 0) penalty = 1.0 + 0.03 * Math.abs(e.slopePercent);
                return e.distanceMeters * penalty;
            }
            case STROLLER:
                // 偏好本身就要求推车友好；不能推车的直接排除
                if (!e.strollerFriendly) return Double.POSITIVE_INFINITY;
                // 同为可推车路段，优先平路、近路
                return e.distanceMeters * (1.0 + 0.05 * Math.abs(e.slopePercent));
            case SCENIC: {
                double scenicFactor = 0.5 + e.scenicScore / 10.0; // 0.5 ~ 1.5
                double w = e.distanceMeters / scenicFactor;
                // 太陡的路即便好看也略微加罚
                if (e.slopePercent > STEEP_SLOPE) w *= 1.3;
                return w;
            }
            default:
                return e.distanceMeters;
        }
    }

    /** 根据节点坐标计算八方位 */
    private String bearing(GardenNode a, GardenNode b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y; // 屏幕坐标 y 向下，这里只用于方向文字，不影响距离
        if (Math.abs(dx) < 1e-9 && Math.abs(dy) < 1e-9) return "前方";
        double angle = Math.toDegrees(Math.atan2(dy, dx)); // 0=东, 90=南(屏幕)
        // 转为地理方位角：屏幕 dy 向下对应“向南”
        String[] dirs = {"东", "东南", "南", "西南", "西", "西北", "北", "东北"};
        int idx = (int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8;
        return dirs[idx];
    }

    private String buildInstruction(GardenNode from, GardenNode to, Edge e,
                                    String bearing, int minutes) {
        StringBuilder sb = new StringBuilder();
        sb.append("从“").append(from.name).append("”出发，向").append(bearing)
          .append("步行约 ").append(e.distanceMeters).append(" 米");
        if (Math.abs(e.slopePercent) < 0.5) {
            sb.append("（平路）");
        } else if (e.slopePercent > 0) {
            sb.append("（上坡，坡度约 ").append(fmt(e.slopePercent)).append("%）");
        } else {
            sb.append("（下坡，坡度约 ").append(fmt(Math.abs(e.slopePercent))).append("%）");
        }
        sb.append("，到达“").append(to.name).append("”（").append(to.type.label())
          .append("，约 ").append(minutes).append(" 分钟）");
        if (!e.strollerFriendly) sb.append("。注意：此段不适合推车");
        if (e.scenicScore >= 8) sb.append("。沿途景观极佳，适合拍照停留");
        sb.append('。');
        return sb.toString();
    }

    private static String fmt(double d) {
        if (d == Math.rint(d)) return String.valueOf((int) d);
        return String.format("%.1f", d);
    }
}

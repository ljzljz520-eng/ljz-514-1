package engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 两个节点之间的一段步道（无向）。
 * slope 为平均坡度百分比（如 5 表示 5%）。
 * stroller 表示是否适合推车通行。
 */
public class Edge {
    public final String id;
    public final String from;
    public final String to;
    public final int distanceMeters;
    public final double slopePercent;
    public final boolean strollerFriendly;
    /** 景观评分 0-10，仅在“观景优先”偏好中参与权重计算 */
    public final int scenicScore;

    public Edge(String id, String from, String to, int distanceMeters,
                double slopePercent, boolean strollerFriendly, int scenicScore) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.distanceMeters = distanceMeters;
        this.slopePercent = slopePercent;
        this.strollerFriendly = strollerFriendly;
        this.scenicScore = scenicScore;
    }

    /** 该边是否与节点 nodeId 相连，相连时返回另一端节点 id，否则返回 null */
    public String otherEnd(String nodeId) {
        if (from.equals(nodeId)) return to;
        if (to.equals(nodeId)) return from;
        return null;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("from", from);
        m.put("to", to);
        m.put("distanceMeters", distanceMeters);
        m.put("slopePercent", slopePercent);
        m.put("strollerFriendly", strollerFriendly);
        m.put("scenicScore", scenicScore);
        return m;
    }
}

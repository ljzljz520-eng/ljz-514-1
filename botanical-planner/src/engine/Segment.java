package engine;

import java.util.LinkedHashMap;
import java.util.Map;

/** 规划路线中的一段（两个相邻节点之间） */
public class Segment {
    public final String edgeId;
    public final String fromId;
    public final String fromName;
    public final String toId;
    public final String toName;
    public final int distanceMeters;
    public final double slopePercent;
    public final boolean strollerFriendly;
    public final int scenicScore;
    /** 八方位行进方向，如“东北” */
    public final String bearing;
    /** 面向游客的中文分步说明 */
    public final String instruction;
    /** 该段预计步行分钟数 */
    public final int minutes;

    public Segment(String edgeId, String fromId, String fromName, String toId, String toName,
                   int distanceMeters, double slopePercent, boolean strollerFriendly,
                   int scenicScore, String bearing, String instruction, int minutes) {
        this.edgeId = edgeId;
        this.fromId = fromId;
        this.fromName = fromName;
        this.toId = toId;
        this.toName = toName;
        this.distanceMeters = distanceMeters;
        this.slopePercent = slopePercent;
        this.strollerFriendly = strollerFriendly;
        this.scenicScore = scenicScore;
        this.bearing = bearing;
        this.instruction = instruction;
        this.minutes = minutes;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("edgeId", edgeId);
        m.put("fromId", fromId);
        m.put("fromName", fromName);
        m.put("toId", toId);
        m.put("toName", toName);
        m.put("distanceMeters", distanceMeters);
        m.put("slopePercent", slopePercent);
        m.put("strollerFriendly", strollerFriendly);
        m.put("scenicScore", scenicScore);
        m.put("bearing", bearing);
        m.put("instruction", instruction);
        m.put("minutes", minutes);
        return m;
    }
}

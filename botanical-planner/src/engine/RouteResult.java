package engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 一次路线规划的结果 */
public class RouteResult {
    public boolean found;
    public Preference preference;
    public int totalDistanceMeters;
    public int totalMinutes;
    /** 全程累计爬升（米） */
    public int climbMeters;
    public List<Segment> segments = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();
    public List<String> nodeIds = new ArrayList<>();
    public String message;

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("found", found);
        m.put("preference", preference == null ? null : preference.code());
        m.put("preferenceLabel", preference == null ? null : preference.label());
        m.put("totalDistanceMeters", totalDistanceMeters);
        m.put("totalMinutes", totalMinutes);
        m.put("climbMeters", climbMeters);
        m.put("message", message);
        List<Object> segList = new ArrayList<>();
        for (Segment s : segments) segList.add(s.toMap());
        m.put("segments", segList);
        m.put("warnings", new ArrayList<>(warnings));
        m.put("nodeIds", new ArrayList<>(nodeIds));
        return m;
    }
}

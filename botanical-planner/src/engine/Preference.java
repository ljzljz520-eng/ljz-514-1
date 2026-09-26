package engine;

import java.util.Arrays;

/** 游客的游览偏好 */
public enum Preference {
    SHORTEST("shortest", "距离最短"),
    LEAST_SLOPE("leastSlope", "坡度最缓"),
    STROLLER("stroller", "推车友好"),
    SCENIC("scenic", "观景最佳");

    private final String code;
    private final String label;

    Preference(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() { return code; }
    public String label() { return label; }

    public static Preference fromCode(String code) {
        return Arrays.stream(values())
                .filter(p -> p.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知游览偏好: " + code));
    }
}

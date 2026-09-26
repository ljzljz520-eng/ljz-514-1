package engine;

import java.util.Arrays;

/** 园区节点类型 */
public enum NodeType {
    ENTRANCE("entrance", "入口/出口"),
    GREENHOUSE("greenhouse", "温室"),
    FLOWER_BORDER("border", "花境"),
    BOARDWALK("boardwalk", "湖边栈道"),
    REST_POINT("rest", "休息点");

    private final String code;
    private final String label;

    NodeType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() { return code; }
    public String label() { return label; }

    public static NodeType fromCode(String code) {
        return Arrays.stream(values())
                .filter(t -> t.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知节点类型: " + code));
    }
}

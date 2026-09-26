package com.botanic.garden.model;

/** 园区节点类型。 */
public enum NodeType {
    GREENHOUSE("温室", "🏛️"),
    FLOWER_BORDER("花境", "🌸"),
    LAKESIDE_WALK("湖边栈道", "🌊"),
    REST_AREA("休息点", "🪑");

    private final String label;
    private final String icon;

    NodeType(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String label() { return label; }
    public String icon() { return icon; }

    public static NodeType fromString(String s) {
        if (s == null) return null;
        for (NodeType t : values()) {
            if (t.name().equalsIgnoreCase(s.trim()) || t.label.equals(s.trim())) return t;
        }
        return null;
    }
}

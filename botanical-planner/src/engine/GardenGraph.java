package engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 植物园园区无向图：节点 + 步道边 */
public class GardenGraph {

    private final Map<String, GardenNode> nodes = new LinkedHashMap<>();
    private final Map<String, Edge> edges = new LinkedHashMap<>();
    /** 邻接表：nodeId -> 与该节点相连的边 */
    private final Map<String, List<Edge>> adjacency = new LinkedHashMap<>();

    public void addNode(GardenNode node) {
        nodes.put(node.id, node);
        adjacency.computeIfAbsent(node.id, k -> new ArrayList<>());
    }

    /** 就地替换同 id 节点的属性（名称、类型、坐标、简介），保留全部步道关联 */
    public void replaceNode(GardenNode node) {
        if (!nodes.containsKey(node.id)) {
            throw new IllegalArgumentException("节点不存在，无法替换: " + node.id);
        }
        nodes.put(node.id, node);
        adjacency.computeIfAbsent(node.id, k -> new ArrayList<>());
    }

    public void addEdge(Edge edge) {
        if (!nodes.containsKey(edge.from) || !nodes.containsKey(edge.to)) {
            throw new IllegalArgumentException(
                    "步道 " + edge.id + " 引用了不存在的节点: " + edge.from + " -> " + edge.to);
        }
        edges.put(edge.id, edge);
        adjacency.computeIfAbsent(edge.from, k -> new ArrayList<>()).add(edge);
        adjacency.computeIfAbsent(edge.to, k -> new ArrayList<>()).add(edge);
    }

    public void removeNode(String id) {
        // 连带删除相关步道
        List<Edge> related = new ArrayList<>(adjacency.getOrDefault(id, Collections.emptyList()));
        for (Edge e : related) removeEdge(e.id);
        nodes.remove(id);
        adjacency.remove(id);
    }

    public void removeEdge(String id) {
        Edge e = edges.remove(id);
        if (e != null) {
            adjacency.getOrDefault(e.from, Collections.emptyList()).remove(e);
            adjacency.getOrDefault(e.to, Collections.emptyList()).remove(e);
        }
    }

    public GardenNode node(String id) { return nodes.get(id); }
    public Edge edge(String id) { return edges.get(id); }
    public Collection<GardenNode> nodes() { return nodes.values(); }
    public Collection<Edge> edges() { return edges.values(); }
    public List<Edge> edgesOf(String nodeId) {
        return adjacency.getOrDefault(nodeId, Collections.emptyList());
    }

    public int nodeCount() { return nodes.size(); }
    public int edgeCount() { return edges.size(); }
}

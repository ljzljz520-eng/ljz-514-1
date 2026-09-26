package com.botanic.garden.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 园区图：节点 + 无向路段。 */
public class Garden {
    private final Map<String, GardenNode> nodes = new LinkedHashMap<>();
    private final Map<String, Edge> edges = new LinkedHashMap<>();

    public Collection<GardenNode> getNodes() { return nodes.values(); }
    public Collection<Edge> getEdges() { return edges.values(); }

    public GardenNode getNode(String id) { return nodes.get(id); }
    public Edge getEdge(String id) { return edges.get(id); }

    public boolean hasNode(String id) { return nodes.containsKey(id); }

    public void addNode(GardenNode node) { nodes.put(node.getId(), node); }

    public GardenNode removeNode(String id) {
        GardenNode removed = nodes.remove(id);
        if (removed != null) {
            // 级联删除相连路段
            List<String> toRemove = new ArrayList<>();
            for (Edge e : edges.values()) {
                if (e.getFromId().equals(id) || e.getToId().equals(id)) toRemove.add(e.getId());
            }
            toRemove.forEach(edges::remove);
        }
        return removed;
    }

    public void addEdge(Edge edge) { edges.put(edge.getId(), edge); }

    public Edge removeEdge(String id) { return edges.remove(id); }

    public Edge findEdgeBetween(String a, String b) {
        for (Edge e : edges.values()) {
            if (e.connects(a, b)) return e;
        }
        return null;
    }

    /** 邻接表：nodeId -> 相连路段列表 */
    public Map<String, List<Edge>> adjacency() {
        Map<String, List<Edge>> adj = new LinkedHashMap<>();
        for (String id : nodes.keySet()) adj.put(id, new ArrayList<>());
        for (Edge e : edges.values()) {
            if (adj.containsKey(e.getFromId()) && adj.containsKey(e.getToId())) {
                adj.get(e.getFromId()).add(e);
                adj.get(e.getToId()).add(e);
            }
        }
        return adj;
    }
}

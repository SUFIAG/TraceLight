package com.sentinel.graph.application.dto;

import lombok.*;

import java.util.List;

/**
 * Node/edge payload for the link-analysis explorer. Shaped for a force
 * layout so the browser does no transformation before rendering.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GraphView {

    private String rootId;
    private List<Node> nodes;
    private List<Edge> edges;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Node {
        private String id;
        private String type;
        private String label;
        /** 0-100. Drives node colour in the explorer. */
        private int risk;
        private boolean root;
        private int degree;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Edge {
        private String source;
        private String target;
        private String type;
        private String label;
        private double strength;
        private String via;
        private int observations;
    }
}

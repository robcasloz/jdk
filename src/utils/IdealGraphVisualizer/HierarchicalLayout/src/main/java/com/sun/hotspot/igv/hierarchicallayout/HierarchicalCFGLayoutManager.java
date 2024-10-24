/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package com.sun.hotspot.igv.hierarchicallayout;

import com.sun.hotspot.igv.layout.LayoutManager;
import com.sun.hotspot.igv.layout.*;
import java.awt.*;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HierarchicalCFGLayoutManager implements LayoutManager {

    private static final int BLOCK_BORDER = 5;
    private final FontMetrics fontMetrics;
    // Lays out nodes within a single cluster (basic block).
    private LayoutManager subManager;
    // Lays out clusters in the CFG.
    private LayoutManager manager;
    private Set<Cluster> clusters;
    private List<Segment> segments;

    public HierarchicalCFGLayoutManager() {
        // Anticipate block label sizes to dimension blocks appropriately.
        Canvas canvas = new Canvas();
        Font font = new Font("Arial", Font.BOLD, 14);
        fontMetrics = canvas.getFontMetrics(font);
    }

    @Override
    public void setCutEdges(boolean enable) {
        subManager.setCutEdges(enable);
        manager.setCutEdges(enable);
    }

    public void setSubManager(LayoutManager manager) {
        this.subManager = manager;
    }

    public void setManager(LayoutManager manager) {
        this.manager = manager;
    }

    public void setClusters(Set<Cluster> clusters) {
        this.clusters = clusters;
    }

    public void setSegments(List<Segment> segments) {
        this.segments = segments;
    }

    public void doLayout(LayoutGraph graph, Set<? extends Link> importantLinks) {
        doLayout(graph);
    }

    public void doLayout(LayoutGraph graph) {

        // Create cluster-level nodes and edges.
        Map<Cluster, ClusterNode> clusterNode = createClusterNodes(graph);
        Set<ClusterEdge> clusterEdges = createClusterEdges(clusterNode);
        markRootClusters(clusterEdges);
        for (Segment s : segments) {
            Cluster c = s.getCluster();
            assert c != null : "Cluster of segment " + s + " is null!";
            clusterNode.get(c).addSubSegment(s);
        }

        // Compute layout for each cluster.
        for (Cluster c : clusters) {
            ClusterNode n = clusterNode.get(c);
            n.groupSegments();
            LayoutGraph g = new LayoutGraph(n.getSubEdges(), n.getSubNodes());
            Set<Segment> clusterSegments = new HashSet<>();
            for (Segment s : segments) {
                if (s.getCluster().equals(c)) {
                    clusterSegments.add(s);
                }
            }
            g.setSegments(n.getSubSegments());
            subManager.doLayout(g, new HashSet<>());
            n.updateSize();
        }

        // Compute inter-cluster layout.
        manager.doLayout(new LayoutGraph(clusterEdges, new HashSet<>(clusterNode.values())), new HashSet<>());

        // Write back results.
        writeBackClusterBounds(clusterNode);
        writeBackClusterEdgePoints(graph, clusterEdges);
    }

    private Map<Cluster, ClusterNode> createClusterNodes(LayoutGraph graph) {
        Map<Cluster, ClusterNode> clusterNode = new HashMap<>();
        for (Cluster c : clusters) {
            String blockLabel = "B" + c;
            Dimension emptySize = new Dimension(fontMetrics.stringWidth(blockLabel) + BLOCK_BORDER * 2,
                                                fontMetrics.getHeight() + BLOCK_BORDER);
            ClusterNode cn = new ClusterNode(c, c.toString(), BLOCK_BORDER, c.getNodeOffset(),
                                             fontMetrics.getHeight(), emptySize);
            clusterNode.put(c, cn);
        }

        for (Vertex v : graph.getVertices()) {
            Cluster c = v.getCluster();
            assert c != null : "Cluster of vertex " + v + " is null!";
            clusterNode.get(c).addSubNode(v);
        }
        return clusterNode;
    }

    private Set<ClusterEdge> createClusterEdges(Map<Cluster, ClusterNode> clusterNode) {
        Set<ClusterEdge> clusterEdges = new HashSet<>();
        for (Cluster c : clusters) {
            ClusterNode start = clusterNode.get(c);
            for (Cluster succ : c.getSuccessors()) {
                ClusterNode end = clusterNode.get(succ);
                if (end != null) {
                    ClusterEdge e = new ClusterEdge(start, end);
                    clusterEdges.add(e);
                }
            }
        }
        return clusterEdges;
    }

    private void markRootClusters(Set<ClusterEdge> clusterEdges) {
        Set<Vertex> roots = new LayoutGraph(clusterEdges).findRootVertices();
        for (Vertex v : roots) {
            assert v instanceof ClusterNode;
            ((ClusterNode) v).setRoot(true);
        }
    }

    private void writeBackClusterBounds(Map<Cluster, ClusterNode> clusterNode) {
        for (Cluster c : clusters) {
            ClusterNode n = clusterNode.get(c);
            c.setBounds(new Rectangle(n.getPosition(), n.getSize()));
        }
    }

    private void writeBackClusterEdgePoints(LayoutGraph graph, Set<ClusterEdge> clusterEdges) {
        // Map from "primitive" cluster edges to their input links.
        Map<AbstractMap.SimpleEntry<Cluster, Cluster>, Link> inputLink = new HashMap<>();
        for (Link l : graph.getLinks()) {
            inputLink.put(new AbstractMap.SimpleEntry<>(l.getFromCluster(), l.getToCluster()), l);
        }
        for (ClusterEdge ce : clusterEdges) {
            assert (ce.getControlPoints() != null);
            Link l = inputLink.get(new AbstractMap.SimpleEntry<>(ce.getFromCluster(), ce.getToCluster()));
            l.setControlPoints(ce.getControlPoints());
        }
    }
}

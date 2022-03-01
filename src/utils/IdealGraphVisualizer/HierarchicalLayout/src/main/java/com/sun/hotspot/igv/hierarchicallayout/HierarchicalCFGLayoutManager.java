/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Canvas;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.SortedSet;
import com.sun.hotspot.igv.layout.Cluster;
import com.sun.hotspot.igv.layout.LayoutGraph;
import com.sun.hotspot.igv.layout.LayoutManager;
import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Vertex;

/**
 *
 * @author Thomas Wuerthinger
 */
public class HierarchicalCFGLayoutManager implements LayoutManager {

    private static final int BLOCK_BORDER = 5;
    private FontMetrics fontMetrics;
    private LayoutManager subManager;
    private LayoutManager manager;

    public HierarchicalCFGLayoutManager() {
        // Anticipate block label sizes to dimension blocks appropriately.
        Canvas canvas = new Canvas();
        Font font = new Font("Arial", Font.BOLD, 14);
        fontMetrics = canvas.getFontMetrics(font);
    }

    public void setSubManager(LayoutManager manager) {
        this.subManager = manager;
    }

    public void setManager(LayoutManager manager) {
        this.manager = manager;
    }

    public void doLayout(LayoutGraph graph, Set<? extends Link> importantLinks) {
        doLayout(graph);
    }

    public void doLayout(LayoutGraph graph) {

        assert graph.verify();

        HashMap<Cluster, ClusterNode> clusterNodes = new HashMap<Cluster, ClusterNode>();
        Set<Link> clusterEdges = new HashSet<Link>();
        Set<ClusterNode> clusterNodeSet = new HashSet<ClusterNode>();

        SortedSet<Cluster> clusters = new TreeSet<Cluster>();
        for (Link l : graph.getLinks()) {
            clusters.add(l.getFromCluster());
            clusters.add(l.getToCluster());
        }
        for (Cluster c : clusters) {
            ClusterNode cn = new ClusterNode(c, c.toString());
            cn.setBorder(BLOCK_BORDER);
            cn.setHeaderVerticalSpace(fontMetrics.getHeight());
            cn.setNodeOffset(c.getNodeOffset());
            String blockLabel = "B" + c.toString();
            Dimension emptySize = new Dimension(fontMetrics.stringWidth(blockLabel) + BLOCK_BORDER * 2,
                                                fontMetrics.getHeight() + BLOCK_BORDER);
            cn.setEmptySize(emptySize);
            clusterNodes.put(c, cn);
            clusterNodeSet.add(cn);
        }

        // Add cluster edges
        for (Cluster c : clusters) {
            System.out.println("c: " + c);
            ClusterNode start = clusterNodes.get(c);

            for (Cluster succ : c.getSuccessors()) {
                ClusterNode end = clusterNodes.get(succ);
                if (end != null) {
                    ClusterEdge e = new ClusterEdge(start, end);
                    System.out.println("e: " + e);
                    clusterEdges.add(e);
                }
            }
        }

        for (Vertex v : graph.getVertices()) {
            Cluster c = v.getCluster();
            assert c != null : "Cluster of vertex " + v + " is null!";
            clusterNodes.get(c).addSubNode(v);
        }

        for (Cluster c : clusters) {
            ClusterNode n = clusterNodes.get(c);
            subManager.doLayout(new LayoutGraph(n.getSubEdges(), n.getSubNodes()), new HashSet<Link>());
            n.updateSize();
        }

        Set<Vertex> roots = new LayoutGraph(clusterEdges).findRootVertices();
        for (Vertex v : roots) {
            assert v instanceof ClusterNode;
            ((ClusterNode) v).setRoot(true);
        }

        manager.doLayout(new LayoutGraph(clusterEdges, clusterNodeSet), new HashSet<Link>());

        for (Cluster c : clusters) {
            ClusterNode n = clusterNodes.get(c);
            c.setBounds(new Rectangle(n.getPosition(), n.getSize()));
        }

        for (Link l : graph.getLinks()) {
            // Find corresponding link in graph.getLinks() and set control points.
            // TODO: create map.
            for (Link cl : clusterEdges) {
                if (l.getFromCluster() == cl.getFromCluster() &&
                    l.getToCluster() == cl.getToCluster()) {
                    l.setControlPoints(cl.getControlPoints());
                    break;
                }
            }
            assert(l.getControlPoints() != null);
        }
    }

    public void doRouting(LayoutGraph graph) {
    }
}

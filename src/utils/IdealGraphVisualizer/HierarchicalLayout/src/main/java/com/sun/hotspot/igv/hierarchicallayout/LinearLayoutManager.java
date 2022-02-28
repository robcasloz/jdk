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

import com.sun.hotspot.igv.layout.LayoutGraph;
import com.sun.hotspot.igv.layout.LayoutManager;
import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Vertex;
import java.awt.Dimension;
import java.awt.Point;
import java.util.*;

public class LinearLayoutManager implements LayoutManager {

    public static final int LAYER_OFFSET = 0;

    public enum Combine {

        NONE,
        SAME_INPUTS,
        SAME_OUTPUTS
    }
    // Options
    private int layerOffset;
    private List<LayoutNode> nodes;
    private Map<? extends Vertex, Integer> vertexRank;

    private class LayoutNode {

        public int x;
        public int y;
        public int height;
        public Vertex vertex;

        @Override
        public String toString() {
            return "Node " + vertex;
        }
    }

    public LinearLayoutManager(Map<? extends Vertex, Integer> vertexRank) {
        this(Combine.NONE);
        this.vertexRank = vertexRank;
    }

    private LinearLayoutManager(Combine b) {
        this.layerOffset = LAYER_OFFSET;
    }

    @Override
    public void doLayout(LayoutGraph graph) {
        doLayout(graph, new HashSet<Link>());
    }

    @Override
    public void doLayout(LayoutGraph graph, Set<? extends Link> importantLinks) {
        System.out.println("LinearLayoutManager::doLayout(");
        System.out.println("\tgraph: " + graph);
        System.out.println("\timportantLinks: " + importantLinks);
        System.out.println("\t)");

        // Build data structures.
        assert (graph.getLinks().isEmpty());
        List<Vertex> vertices = new ArrayList<>(graph.getVertices());
        vertices.sort((Vertex a, Vertex b) ->
                      Integer.compare(vertexRank.getOrDefault(a, Integer.MAX_VALUE),
                                      vertexRank.getOrDefault(b, Integer.MAX_VALUE)));

        nodes = new ArrayList<>();
        for (Vertex v : vertices) {
            LayoutNode node = new LayoutNode();
            Dimension size = v.getSize();
            node.height = (int) size.getHeight();
            node.vertex = v;
            node.x = 0;
            nodes.add(node);
        }

        // Assign Y coordinates.
        int curY = 0;
        for (LayoutNode n : nodes) {
            n.y = curY;
            curY += n.height + layerOffset;
        }

        // Write back result into vertices.
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (LayoutNode n : nodes) {
            Point p = new Point(n.x, n.y);
            n.vertex.setPosition(p);
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
        }
        for (Vertex v : graph.getVertices()) {
            Point p = v.getPosition();
            p.x -= minX;
            p.y -= minY;
            v.setPosition(p);
        }
    }

    @Override
    public void doRouting(LayoutGraph graph) {
    }
}

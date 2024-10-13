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
import com.sun.hotspot.igv.layout.Segment;
import com.sun.hotspot.igv.layout.Vertex;
import java.awt.Point;
import java.util.*;

public class LinearLayoutManager implements LayoutManager {

    // Ranking determining the vertical node ordering.
    private final Map<? extends Vertex, Integer> vertexRank;

    public LinearLayoutManager(Map<? extends Vertex, Integer> vertexRank) {
        this.vertexRank = vertexRank;
    }

    @Override
    public void setCutEdges(boolean enable) {}

    @Override
    public void doLayout(LayoutGraph graph) {
        doLayout(graph, new HashSet<>());
    }

    @Override
    public void doLayout(LayoutGraph graph, Set<? extends Link> importantLinks) {

        assert (graph.getLinks().isEmpty());

        // Sort vertices according to given rank.
        List<Vertex> vertices = new ArrayList<>(graph.getVertices());
        vertices.sort(Comparator.comparingInt((Vertex v) -> vertexRank.getOrDefault(v, Integer.MAX_VALUE)));

        // Assign vertical coordinates in rank order.
        assignVerticalCoordinates(vertices);

        if (vertices.isEmpty()) {
            int x = ClusterNode.EMPTY_BLOCK_LIVE_RANGE_OFFSET;
            for (Segment s : graph.getSegments()) {
                s.setStartPoint(new Point(x, 0));
                s.setEndPoint(new Point(x, 0));
                x += s.getCluster().getLiveRangeSeparation();
            }
        } else {
            Vertex first = vertices.get(0);
            int x = (int)first.getSize().getWidth();
            int entryY = (int)first.getPosition().getY();
            Vertex last = vertices.get(vertices.size() - 1);
            int exitY = (int)last.getPosition().getY() + (int)last.getSize().getHeight();
            for (Segment s : graph.getSegments()) {
                int startY = s.getStart() == null ? entryY : s.getStart().getPosition().y;
                s.setStartPoint(new Point(x, startY));
                int endY = s.getEnd() == null ? exitY : s.getEnd().getPosition().y;
                s.setEndPoint(new Point(x, endY));
                x += s.getCluster().getLiveRangeSeparation();
            }
        }
        for (Segment segment : graph.getSegments()) {
            Point s = segment.getStartPoint();
            System.out.println("linearlayoutmanager: start point: " + s);
        }

    }

    private void assignVerticalCoordinates(List<Vertex> vertices) {
        int curY = 0;
        for (Vertex v : vertices) {
            v.setPosition(new Point(0, curY));
            curY += v.getSize().getHeight();
        }
    }
}

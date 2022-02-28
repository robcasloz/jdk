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

    public static final boolean TRACE = false;
    public static final boolean CHECK = false;
    public static final int X_OFFSET = 8;
    public static final int LAYER_OFFSET = 0;
    public static final int MAX_LAYER_LENGTH = -1;

    public enum Combine {

        NONE,
        SAME_INPUTS,
        SAME_OUTPUTS
    }
    // Options
    private int xOffset;
    private int layerOffset;
    private int maxLayerLength;
    // Algorithm global datastructures
    private List<LayoutNode> nodes;
    private HashMap<Vertex, LayoutNode> vertexToLayoutNode;
    private LayoutGraph graph;
    private List<LayoutNode>[] layers;
    private int layerCount;
    private Map<? extends Vertex, Integer> vertexRank;


    private ArrayList<Integer>[] space;
    private ArrayList<LayoutNode>[] downProcessingOrder;
    private ArrayList<LayoutNode>[] upProcessingOrder;

    private class LayoutNode {

        public int x;
        public int y;
        public int width;
        public int height;
        public int layer = -1;
        public Vertex vertex;

        public int pos = -1; // Position within layer

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
        this.xOffset = X_OFFSET;
        this.layerOffset = LAYER_OFFSET;
        this.maxLayerLength = MAX_LAYER_LENGTH;
    }

    public int getMaxLayerLength() {
        return maxLayerLength;
    }

    public void setMaxLayerLength(int v) {
        maxLayerLength = v;
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
        this.graph = graph;

        vertexToLayoutNode = new HashMap<>();
        nodes = new ArrayList<>();
        runBuildDatastructure();
        runAssignLayers();
        runCrossingReduction();
        runAssignXCoordinates();
        runAssignYCoordinates();
        runWriteResult();
    }

    protected void runWriteResult() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (Vertex v : graph.getVertices()) {
            LayoutNode n = vertexToLayoutNode.get(v);
            Point p = new Point(n.x, n.y);
            v.setPosition(p);
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

    private static final Comparator<LayoutNode> nodePositionComparator = new Comparator<LayoutNode>() {
        @Override
        public int compare(LayoutNode n1, LayoutNode n2) {
            return n1.pos - n2.pos;
        }
    };
    private static final Comparator<LayoutNode> nodeProcessingDownComparator = new Comparator<LayoutNode>() {
        @Override
        public int compare(LayoutNode n1, LayoutNode n2) {
            if (n1.vertex == null) {
                if (n2.vertex == null) {
                    return 0;
                }
                return -1;
            }
            if (n2.vertex == null) {
                return 1;
            }
            return 0;
        }
    };
    private static final Comparator<LayoutNode> nodeProcessingUpComparator = new Comparator<LayoutNode>() {

        @Override
        public int compare(LayoutNode n1, LayoutNode n2) {
            if (n1.vertex == null) {
                if (n2.vertex == null) {
                    return 0;
                }
                return -1;
            }
            if (n2.vertex == null) {
                return 1;
            }
            return 0;
        }
    };

    private void initialPositions() {
        for (LayoutNode n : nodes) {
            n.x = space[n.layer].get(n.pos);
        }
    }
    @SuppressWarnings("unchecked")
    private void createArrays() {
        space = new ArrayList[layers.length];
        downProcessingOrder = new ArrayList[layers.length];
        upProcessingOrder = new ArrayList[layers.length];
    }

    protected void runAssignXCoordinates() {
        createArrays();

        for (int i = 0; i < layers.length; i++) {
            space[i] = new ArrayList<>();
            downProcessingOrder[i] = new ArrayList<>();
            upProcessingOrder[i] = new ArrayList<>();

            int curX = 0;
            for (LayoutNode n : layers[i]) {
                space[i].add(curX);
                curX += n.width + xOffset;
                downProcessingOrder[i].add(n);
                upProcessingOrder[i].add(n);
            }

            Collections.sort(downProcessingOrder[i], nodeProcessingDownComparator);
            Collections.sort(upProcessingOrder[i], nodeProcessingUpComparator);
        }

        initialPositions();

        sweepDown();
        adjustSpace();
        sweepUp();
    }

    private void adjustSpace() {
        for (int i = 0; i < layers.length; i++) {
            for (LayoutNode n : layers[i]) {
                space[i].add(n.x);
            }
        }
    }

    private void sweepUp() {
        for (int i = layers.length - 1; i >= 0; i--) {
            NodeRow r = new NodeRow(space[i]);
            for (LayoutNode n : upProcessingOrder[i]) {
                r.insert(n, n.x);
            }
        }
    }

    private void sweepDown() {
        for (int i = 1; i < layers.length; i++) {
            NodeRow r = new NodeRow(space[i]);
            for (LayoutNode n : downProcessingOrder[i]) {
                r.insert(n, n.x);
            }
        }
    }

    private static class NodeRow {

        private TreeSet<LayoutNode> treeSet;
        private ArrayList<Integer> space;

        public NodeRow(ArrayList<Integer> space) {
            treeSet = new TreeSet<>(nodePositionComparator);
            this.space = space;
        }

        public int offset(LayoutNode n1, LayoutNode n2) {
            int v1 = space.get(n1.pos) + n1.width;
            int v2 = space.get(n2.pos);
            return v2 - v1;
        }

        public void insert(LayoutNode n, int pos) {

            SortedSet<LayoutNode> headSet = treeSet.headSet(n);

            LayoutNode leftNeighbor = null;
            int minX = Integer.MIN_VALUE;
            if (!headSet.isEmpty()) {
                leftNeighbor = headSet.last();
                minX = leftNeighbor.x + leftNeighbor.width + offset(leftNeighbor, n);
            }

            if (pos < minX) {
                n.x = minX;
            } else {

                LayoutNode rightNeighbor = null;
                SortedSet<LayoutNode> tailSet = treeSet.tailSet(n);
                int maxX = Integer.MAX_VALUE;
                if (!tailSet.isEmpty()) {
                    rightNeighbor = tailSet.first();
                    maxX = rightNeighbor.x - offset(n, rightNeighbor) - n.width;
                }

                if (pos > maxX) {
                    n.x = maxX;
                } else {
                    n.x = pos;
                }

                assert minX <= maxX : minX + " vs " + maxX;
            }

            treeSet.add(n);
        }
    }

    @SuppressWarnings("unchecked")
    private void createLayers() {
        layers = new List[layerCount];
        for (int i = 0; i < layerCount; i++) {
            layers[i] = new ArrayList<>();
        }
    }

    protected void runCrossingReduction() {
        createLayers();

        // Generate initial ordering
        HashSet<LayoutNode> visited = new HashSet<>();
        for (LayoutNode n : nodes) {
            layers[n.layer].add(n);
            visited.add(n);
        }
        updatePositions();
        for (int i = 0; i < layers.length; i++) {
            updateXOfLayer(i);
        }
    }

    private void updateXOfLayer(int index) {
        int x = 0;
        for (LayoutNode n : layers[index]) {
            n.x = x;
            x += n.width + X_OFFSET;
        }
    }

    private void updatePositions() {
        for (int i = 0; i < layers.length; i++) {
            int z = 0;
            for (LayoutNode n : layers[i]) {
                n.pos = z;
                z++;
            }
        }
    }

    protected void runAssignYCoordinates() {
        int curY = 0;

        for (int i = 0; i < layers.length; i++) {
            int maxHeight = 0;
            for (LayoutNode n : layers[i]) {
                maxHeight = Math.max(maxHeight, n.height);
            }
            int maxXOffset = 0;
            for (LayoutNode n : layers[i]) {
                assert (n.vertex != null);
                n.y = curY + (maxHeight - n.height) / 2;
            }
            curY += maxHeight;
            curY += layerOffset + ((int) (Math.sqrt(maxXOffset) * 1.5));
        }
    }

    protected void runAssignLayers() {
        // TODO: start from 0?
        int i = 1, max = -1;
        for (LayoutNode n : nodes) {
            n.layer = i;
            i++;
            if (i > max) {
                max = i;
            }
        }
        layerCount = max + 1;
    }

    protected void runBuildDatastructure() {
        assert (graph.getLinks().isEmpty());
        List<Vertex> vertices = new ArrayList<>(graph.getVertices());
        vertices.sort((Vertex a, Vertex b) ->
                      Integer.compare(vertexRank.getOrDefault(a, Integer.MAX_VALUE),
                                      vertexRank.getOrDefault(b, Integer.MAX_VALUE)));

        for (Vertex v : vertices) {
            LayoutNode node = new LayoutNode();
            Dimension size = v.getSize();
            node.width = (int) size.getWidth();
            node.height = (int) size.getHeight();
            node.vertex = v;
            nodes.add(node);
            vertexToLayoutNode.put(v, node);
        }
    }

    @Override
    public void doRouting(LayoutGraph graph) {
    }
}

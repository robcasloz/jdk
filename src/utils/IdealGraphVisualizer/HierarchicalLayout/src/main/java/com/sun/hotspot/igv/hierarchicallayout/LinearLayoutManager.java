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

    private abstract class AlgorithmPart {

        public void start() {
            if (CHECK) {
                preCheck();
            }

            long start = 0;
            if (TRACE) {
                System.out.println("##################################################");
                System.out.println("Starting part " + this.getClass().getName());
                start = System.currentTimeMillis();
            }
            run();
            if (TRACE) {
                System.out.println("Timing for " + this.getClass().getName() + " is " + (System.currentTimeMillis() - start));
                printStatistics();
            }

            if (CHECK) {
                postCheck();
            }
        }

        protected abstract void run();

        protected void printStatistics() {
        }

        protected void postCheck() {
        }

        protected void preCheck() {
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

        // #############################################################
        // Step 1: Build up data structure
        new BuildDatastructure().start();

        // #############################################################
        // STEP 3: Assign layers
        new AssignLayers().start();

        // #############################################################
        // STEP 5: Crossing Reduction
        new CrossingReduction().start();

        // #############################################################
        // STEP 7: Assign X coordinates
        new AssignXCoordinates().start();

        // #############################################################
        // STEP 6: Assign Y coordinates
        new AssignYCoordinates().start();

        // #############################################################
        // STEP 8: Write back to interface
        new WriteResult().start();
    }

    private class WriteResult extends AlgorithmPart {

        private int pointCount;

        @Override
        protected void run() {
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

        @Override
        protected void printStatistics() {
            System.out.println("Number of nodes: " + nodes.size());
            System.out.println("Number of points: " + pointCount);
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

    private class AssignXCoordinates extends AlgorithmPart {

        private ArrayList<Integer>[] space;
        private ArrayList<LayoutNode>[] downProcessingOrder;
        private ArrayList<LayoutNode>[] upProcessingOrder;

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

        @Override
        protected void run() {
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

    private class CrossingReduction extends AlgorithmPart {

        @Override
        public void preCheck() {
            for (LayoutNode n : nodes) {
                assert n.layer < layerCount;
            }
        }

        @SuppressWarnings("unchecked")
        private void createLayers() {
            layers = new List[layerCount];

            for (int i = 0; i < layerCount; i++) {
                layers[i] = new ArrayList<>();
            }
        }

        @Override
        protected void run() {
            createLayers();

            // Generate initial ordering
            HashSet<LayoutNode> visited = new HashSet<>();
            for (LayoutNode n : nodes) {
                layers[n.layer].add(n);
                visited.add(n);
            }

            updatePositions();
            initX();
        }

        private void initX() {

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

        @Override
        public void postCheck() {

            HashSet<LayoutNode> visited = new HashSet<>();
            for (int i = 0; i < layers.length; i++) {
                for (LayoutNode n : layers[i]) {
                    assert !visited.contains(n);
                    assert n.layer == i;
                    visited.add(n);
                }
            }

        }
    }

    private class AssignYCoordinates extends AlgorithmPart {

        @Override
        protected void run() {
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
    }

    private class AssignLayers extends AlgorithmPart {

        @Override
        public void preCheck() {
            for (LayoutNode n : nodes) {
                assert n.layer == -1;
            }
        }

        @Override
        protected void run() {
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

        @Override
        public void postCheck() {
            for (LayoutNode n : nodes) {
                assert n.layer >= 0;
                assert n.layer < layerCount;
            }
        }
    }

    private class BuildDatastructure extends AlgorithmPart {

        @Override
        protected void run() {
            // Set up nodes
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

            assert (graph.getLinks().isEmpty());
        }

        @Override
        public void postCheck() {

            assert vertexToLayoutNode.keySet().size() == nodes.size();
            assert nodes.size() == graph.getVertices().size();

            for (Vertex v : graph.getVertices()) {

                LayoutNode node = vertexToLayoutNode.get(v);
                assert node != null;

            }
        }
    }

    @Override
    public void doRouting(LayoutGraph graph) {
        // Do nothing for now
    }
}

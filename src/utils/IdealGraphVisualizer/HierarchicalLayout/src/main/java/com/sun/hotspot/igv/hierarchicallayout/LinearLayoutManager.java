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
    private Set<Link> reversedLinks;
    private List<LayoutNode> nodes;
    private HashMap<Vertex, LayoutNode> vertexToLayoutNode;
    private HashMap<Link, List<Point>> reversedLinkStartPoints;
    private HashMap<Link, List<Point>> reversedLinkEndPoints;
    private HashMap<Link, List<Point>> splitStartPoints;
    private HashMap<Link, List<Point>> splitEndPoints;
    private LayoutGraph graph;
    private List<LayoutNode>[] layers;
    private int layerCount;
    private Set<? extends Link> importantLinks;
    private Set<Link> linksToFollow;
    private Map<? extends Vertex, Integer> vertexRank;

    private class LayoutNode {

        public int x;
        public int y;
        public int width;
        public int height;
        public int layer = -1;
        public int xOffset;
        public int yOffset;
        public int bottomYOffset;
        public Vertex vertex;

        public List<LayoutEdge> preds = new ArrayList<>();
        public List<LayoutEdge> succs = new ArrayList<>();
        public HashMap<Integer, Integer> outOffsets = new HashMap<>();
        public HashMap<Integer, Integer> inOffsets = new HashMap<>();
        public int pos = -1; // Position within layer

        @Override
        public String toString() {
            return "Node " + vertex;
        }
    }

    private class LayoutEdge {

        public LayoutNode from;
        public LayoutNode to;
        public int relativeFrom;
        public int relativeTo;
        public Link link;
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
        this.linksToFollow = new HashSet<>();
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
        this.importantLinks = importantLinks;
        this.graph = graph;

        vertexToLayoutNode = new HashMap<>();
        reversedLinks = new HashSet<>();
        reversedLinkStartPoints = new HashMap<>();
        reversedLinkEndPoints = new HashMap<>();
        nodes = new ArrayList<>();
        splitStartPoints = new HashMap<>();
        splitEndPoints = new HashMap<>();

        // #############################################################
        // Step 1: Build up data structure
        new BuildDatastructure().start();

        for (LayoutNode n : nodes) {
            ArrayList<LayoutEdge> tmpArr = new ArrayList<>();
            for (LayoutEdge e : n.succs) {
                if (importantLinks.contains(e.link)) {
                    tmpArr.add(e);
                }
            }

            for (LayoutEdge e : tmpArr) {
                e.from.succs.remove(e);
                e.to.preds.remove(e);
            }
        }

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

            HashMap<Vertex, Point> vertexPositions = new HashMap<>();
            HashMap<Link, List<Point>> linkPositions = new HashMap<>();
            for (Vertex v : graph.getVertices()) {
                LayoutNode n = vertexToLayoutNode.get(v);
                assert !vertexPositions.containsKey(v);
                vertexPositions.put(v, new Point(n.x + n.xOffset, n.y + n.yOffset));
            }

            for (LayoutNode n : nodes) {

                for (LayoutEdge e : n.preds) {
                    if (e.link != null) {
                        ArrayList<Point> points = new ArrayList<>();

                        Point p = new Point(e.to.x + e.relativeTo, e.to.y + e.to.yOffset + e.link.getTo().getRelativePosition().y);
                        points.add(p);
                        if (e.to.inOffsets.containsKey(e.relativeTo)) {
                            points.add(new Point(p.x, p.y + e.to.inOffsets.get(e.relativeTo) + e.link.getTo().getRelativePosition().y));
                        }

                        LayoutNode cur = e.from;
                        LayoutNode other = e.to;
                        LayoutEdge curEdge = e;
                        while (cur.vertex == null && cur.preds.size() != 0) {
                            if (points.size() > 1 && points.get(points.size() - 1).x == cur.x + cur.width / 2 && points.get(points.size() - 2).x == cur.x + cur.width / 2) {
                                points.remove(points.size() - 1);
                            }
                            points.add(new Point(cur.x + cur.width / 2, cur.y + cur.height));
                            if (points.size() > 1 && points.get(points.size() - 1).x == cur.x + cur.width / 2 && points.get(points.size() - 2).x == cur.x + cur.width / 2) {
                                points.remove(points.size() - 1);
                            }
                            points.add(new Point(cur.x + cur.width / 2, cur.y));
                            assert cur.preds.size() == 1;
                            curEdge = cur.preds.get(0);
                            cur = curEdge.from;
                        }

                        p = new Point(cur.x + curEdge.relativeFrom, cur.y + cur.height - cur.bottomYOffset + (curEdge.link == null ? 0 : curEdge.link.getFrom().getRelativePosition().y));
                        if (curEdge.from.outOffsets.containsKey(curEdge.relativeFrom)) {
                            points.add(new Point(p.x, p.y + curEdge.from.outOffsets.get(curEdge.relativeFrom) + (curEdge.link == null ? 0 : curEdge.link.getFrom().getRelativePosition().y)));
                        }
                        points.add(p);

                        Collections.reverse(points);

                        if (cur.vertex == null && cur.preds.size() == 0) {

                            if (reversedLinkEndPoints.containsKey(e.link)) {
                                for (Point p1 : reversedLinkEndPoints.get(e.link)) {
                                    points.add(new Point(p1.x + e.to.x, p1.y + e.to.y));
                                }
                            }

                            if (splitStartPoints.containsKey(e.link)) {
                                points.add(0, null);
                                points.addAll(0, splitStartPoints.get(e.link));

                                //checkPoints(points);
                                if (reversedLinks.contains(e.link)) {
                                    Collections.reverse(points);
                                }
                                assert !linkPositions.containsKey(e.link);
                                linkPositions.put(e.link, points);
                            } else {
                                splitEndPoints.put(e.link, points);
                            }

                        } else {
                            if (reversedLinks.contains(e.link)) {
                                Collections.reverse(points);
                            }
                            if (reversedLinkStartPoints.containsKey(e.link)) {
                                for (Point p1 : reversedLinkStartPoints.get(e.link)) {
                                    points.add(new Point(p1.x + cur.x, p1.y + cur.y));
                                }
                            }

                            if (reversedLinkEndPoints.containsKey(e.link)) {
                                for (Point p1 : reversedLinkEndPoints.get(e.link)) {
                                    points.add(0, new Point(p1.x + other.x, p1.y + other.y));
                                }
                            }

                            assert !linkPositions.containsKey(e.link);
                            linkPositions.put(e.link, points);
                        }
                        pointCount += points.size();

                        // No longer needed!
                        e.link = null;
                    }
                }

                for (LayoutEdge e : n.succs) {
                    if (e.link != null) {
                        ArrayList<Point> points = new ArrayList<>();
                        Point p = new Point(e.from.x + e.relativeFrom, e.from.y + e.from.height - e.from.bottomYOffset + e.link.getFrom().getRelativePosition().y);
                        points.add(p);
                        if (e.from.outOffsets.containsKey(e.relativeFrom)) {
                            points.add(new Point(p.x, p.y + e.from.outOffsets.get(e.relativeFrom) + e.link.getFrom().getRelativePosition().y));
                        }

                        LayoutNode cur = e.to;
                        LayoutNode other = e.from;
                        LayoutEdge curEdge = e;
                        while (cur.vertex == null && !cur.succs.isEmpty()) {
                            if (points.size() > 1 && points.get(points.size() - 1).x == cur.x + cur.width / 2 && points.get(points.size() - 2).x == cur.x + cur.width / 2) {
                                points.remove(points.size() - 1);
                            }
                            points.add(new Point(cur.x + cur.width / 2, cur.y));
                            if (points.size() > 1 && points.get(points.size() - 1).x == cur.x + cur.width / 2 && points.get(points.size() - 2).x == cur.x + cur.width / 2) {
                                points.remove(points.size() - 1);
                            }
                            points.add(new Point(cur.x + cur.width / 2, cur.y + cur.height));
                            if (cur.succs.isEmpty()) {
                                break;
                            }
                            assert cur.succs.size() == 1;
                            curEdge = cur.succs.get(0);
                            cur = curEdge.to;
                        }

                        p = new Point(cur.x + curEdge.relativeTo, cur.y + cur.yOffset + ((curEdge.link == null) ? 0 : curEdge.link.getTo().getRelativePosition().y));
                        points.add(p);
                        if (curEdge.to.inOffsets.containsKey(curEdge.relativeTo)) {
                            points.add(new Point(p.x, p.y + curEdge.to.inOffsets.get(curEdge.relativeTo) + ((curEdge.link == null) ? 0 : curEdge.link.getTo().getRelativePosition().y)));
                        }

                        if (cur.succs.isEmpty() && cur.vertex == null) {
                            if (reversedLinkStartPoints.containsKey(e.link)) {
                                for (Point p1 : reversedLinkStartPoints.get(e.link)) {
                                    points.add(0, new Point(p1.x + other.x, p1.y + other.y));
                                }
                            }

                            if (splitEndPoints.containsKey(e.link)) {
                                points.add(null);
                                points.addAll(splitEndPoints.get(e.link));

                                //checkPoints(points);
                                if (reversedLinks.contains(e.link)) {
                                    Collections.reverse(points);
                                }
                                assert !linkPositions.containsKey(e.link);
                                linkPositions.put(e.link, points);
                            } else {
                                splitStartPoints.put(e.link, points);
                            }
                        } else {

                            if (reversedLinkStartPoints.containsKey(e.link)) {
                                for (Point p1 : reversedLinkStartPoints.get(e.link)) {
                                    points.add(0, new Point(p1.x + other.x, p1.y + other.y));
                                }
                            }
                            if (reversedLinkEndPoints.containsKey(e.link)) {
                                for (Point p1 : reversedLinkEndPoints.get(e.link)) {
                                    points.add(new Point(p1.x + cur.x, p1.y + cur.y));
                                }
                            }
                            if (reversedLinks.contains(e.link)) {
                                Collections.reverse(points);
                            }
                            //checkPoints(points);
                            assert !linkPositions.containsKey(e.link);
                            linkPositions.put(e.link, points);
                        }

                        pointCount += points.size();
                        e.link = null;
                    }
                }
            }

            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            for (Vertex v : vertexPositions.keySet()) {
                Point p = vertexPositions.get(v);
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
            }

            for (Link l : linkPositions.keySet()) {
                List<Point> points = linkPositions.get(l);
                for (Point p : points) {
                    if (p != null) {
                        minX = Math.min(minX, p.x);
                        minY = Math.min(minY, p.y);
                    }
                }

            }

            for (Vertex v : vertexPositions.keySet()) {
                Point p = vertexPositions.get(v);
                p.x -= minX;
                p.y -= minY;
                v.setPosition(p);
            }

            for (Link l : linkPositions.keySet()) {
                List<Point> points = linkPositions.get(l);
                for (Point p : points) {
                    if (p != null) {
                        p.x -= minX;
                        p.y -= minY;
                    }
                }
                l.setControlPoints(points);

            }
        }

        @Override
        protected void printStatistics() {
            System.out.println("Number of nodes: " + nodes.size());
            int edgeCount = 0;
            for (LayoutNode n : nodes) {
                edgeCount += n.succs.size();
            }
            System.out.println("Number of edges: " + edgeCount);
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
            return n1.preds.size() - n2.preds.size();
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
            return n1.succs.size() - n2.succs.size();
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

        private int calculateOptimalDown(LayoutNode n) {
            int size = n.preds.size();
            if (size == 0) {
                return n.x;
            }
            int[] values = new int[size];
            for (int i = 0; i < size; i++) {
                LayoutEdge e = n.preds.get(i);
                values[i] = e.from.x + e.relativeFrom - e.relativeTo;
            }
            return median(values);
        }

        private int calculateOptimalUp(LayoutNode n) {
            int size = n.succs.size();
            if (size == 0) {
                return n.x;
            }
            int[] values = new int[size];
            for (int i = 0; i < size; i++) {
                LayoutEdge e = n.succs.get(i);
                values[i] = e.to.x + e.relativeTo - e.relativeFrom;
            }
            return median(values);
        }

        private int median(int[] values) {
            Arrays.sort(values);
            if (values.length % 2 == 0) {
                return (values[values.length / 2 - 1] + values[values.length / 2]) / 2;
            } else {
                return values[values.length / 2];
            }
        }

        private void sweepUp() {
            for (int i = layers.length - 1; i >= 0; i--) {
                NodeRow r = new NodeRow(space[i]);
                for (LayoutNode n : upProcessingOrder[i]) {
                    int optimal = calculateOptimalUp(n);
                    r.insert(n, optimal);
                }
            }
        }

        private void sweepDown() {
            for (int i = 1; i < layers.length; i++) {
                NodeRow r = new NodeRow(space[i]);
                for (LayoutNode n : downProcessingOrder[i]) {
                    int optimal = calculateOptimalDown(n);
                    r.insert(n, optimal);
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
                int baseLine = 0;
                int bottomBaseLine = 0;
                for (LayoutNode n : layers[i]) {
                    maxHeight = Math.max(maxHeight, n.height - n.yOffset - n.bottomYOffset);
                    baseLine = Math.max(baseLine, n.yOffset);
                    bottomBaseLine = Math.max(bottomBaseLine, n.bottomYOffset);
                }

                int maxXOffset = 0;
                for (LayoutNode n : layers[i]) {
                    assert (n.vertex != null);
                    n.y = curY + baseLine + (maxHeight - (n.height - n.yOffset - n.bottomYOffset)) / 2 - n.yOffset;
                    for (LayoutEdge e : n.succs) {
                        int curXOffset = Math.abs(n.x - e.to.x);
                        maxXOffset = Math.max(curXOffset, maxXOffset);
                    }
                }

                curY += maxHeight + baseLine + bottomBaseLine;
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
                for (LayoutEdge e : n.succs) {
                    assert e.from.layer < e.to.layer;
                }
            }
        }
    }

    private Comparator<Link> linkComparator = new Comparator<Link>() {

        @Override
        public int compare(Link l1, Link l2) {
            int result = l1.getFrom().getVertex().compareTo(l2.getFrom().getVertex());
            if (result != 0) {
                return result;
            }
            result = l1.getTo().getVertex().compareTo(l2.getTo().getVertex());
            if (result != 0) {
                return result;
            }
            result = l1.getFrom().getRelativePosition().x - l2.getFrom().getRelativePosition().x;
            if (result != 0) {
                return result;
            }
            result = l1.getTo().getRelativePosition().x - l2.getTo().getRelativePosition().x;
            return result;
        }
    };

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

            // Set up edges
            List<Link> links = new ArrayList<>(graph.getLinks());
            Collections.sort(links, linkComparator);
            for (Link l : links) {
                LayoutEdge edge = new LayoutEdge();
                assert vertexToLayoutNode.containsKey(l.getFrom().getVertex());
                assert vertexToLayoutNode.containsKey(l.getTo().getVertex());
                edge.from = vertexToLayoutNode.get(l.getFrom().getVertex());
                edge.to = vertexToLayoutNode.get(l.getTo().getVertex());
                edge.relativeFrom = l.getFrom().getRelativePosition().x;
                edge.relativeTo = l.getTo().getRelativePosition().x;
                edge.link = l;
                edge.from.succs.add(edge);
                edge.to.preds.add(edge);
                //assert edge.from != edge.to; // No self-loops allowed
            }

            for (Link l : importantLinks) {
                if (!vertexToLayoutNode.containsKey(l.getFrom().getVertex())
                        || vertexToLayoutNode.containsKey(l.getTo().getVertex())) {
                    continue;
                }
                LayoutNode from = vertexToLayoutNode.get(l.getFrom().getVertex());
                LayoutNode to = vertexToLayoutNode.get(l.getTo().getVertex());
                for (LayoutEdge e : from.succs) {
                    if (e.to == to) {
                        linksToFollow.add(e.link);
                    }
                }
            }
        }

        @Override
        public void postCheck() {

            assert vertexToLayoutNode.keySet().size() == nodes.size();
            assert nodes.size() == graph.getVertices().size();

            for (Vertex v : graph.getVertices()) {

                LayoutNode node = vertexToLayoutNode.get(v);
                assert node != null;

                for (LayoutEdge e : node.succs) {
                    assert e.from == node;
                }

                for (LayoutEdge e : node.preds) {
                    assert e.to == node;
                }

            }
        }
    }

    @Override
    public void doRouting(LayoutGraph graph) {
        // Do nothing for now
    }
}

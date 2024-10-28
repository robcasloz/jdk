/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.view.widgets;

import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.graph.LiveRangeSegment;
import com.sun.hotspot.igv.util.PropertiesConverter;
import com.sun.hotspot.igv.util.PropertiesSheet;
import com.sun.hotspot.igv.view.DiagramScene;
import java.awt.*;
import org.netbeans.api.visual.widget.Widget;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;

public class LiveRangeWidget extends Widget implements Properties.Provider {

    private final LiveRangeSegment liveRangeSegment;
    private final DiagramScene scene;
    private final Point start;
    private final Point end;
    private final Rectangle clientArea;
    private final Node node;

    private static final int RANGE_WIDTH = 4;

    public LiveRangeWidget(LiveRangeSegment liveRangeSegment, DiagramScene scene, Point start, Point end) {
        super(scene);
        this.liveRangeSegment = liveRangeSegment;
        this.scene = scene;
        this.start = start;
        this.end = end;

        int x = start.x;
        int minY = start.y;
        int maxY = end.y;

        setBackground(Color.BLACK);

        clientArea = new Rectangle(x, minY, 1, maxY - minY + 1);
        clientArea.grow(RANGE_WIDTH * 2, 5);

        // Initialize node for property sheet
        node = new AbstractNode(Children.LEAF) {
            @Override
            protected Sheet createSheet() {
                Sheet s = super.createSheet();
                PropertiesSheet.initializeSheet(getProperties(), s);
                return s;
            }
        };
        node.setDisplayName("L" + liveRangeSegment.getLiveRange().getId());

        this.setToolTipText(PropertiesConverter.convertToHTML(getProperties()));
    }

    @Override
    protected Rectangle calculateClientArea() {
        return clientArea;
    }

    @Override
    protected void paintWidget() {
        if (scene.getZoomFactor() < 0.1) {
            return;
        }

        Graphics2D g = getScene().getGraphics();
        g.setPaint(this.getBackground());

        g.setStroke(new BasicStroke(1.4f));
        g.drawLine(start.x - RANGE_WIDTH, start.y, start.x + RANGE_WIDTH, start.y);
        if (start.y != end.y) {
            g.drawLine(start.x, start.y, end.x, end.y);
            g.drawLine(end.x - RANGE_WIDTH, end.y, end.x + RANGE_WIDTH, end.y);
        }
    }

    @Override
    public Properties getProperties() {
        return liveRangeSegment.getLiveRange().getProperties();
    }
}

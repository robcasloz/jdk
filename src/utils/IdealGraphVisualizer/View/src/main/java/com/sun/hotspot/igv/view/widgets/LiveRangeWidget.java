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

import com.sun.hotspot.igv.graph.Block;
import com.sun.hotspot.igv.graph.Connection;
import com.sun.hotspot.igv.graph.Figure;
import com.sun.hotspot.igv.graph.OutputSlot;
import com.sun.hotspot.igv.layout.Vertex;
import com.sun.hotspot.igv.util.StringUtils;
import com.sun.hotspot.igv.view.DiagramScene;
import com.sun.hotspot.igv.view.actions.CustomSelectAction;
import java.awt.*;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.netbeans.api.visual.action.ActionFactory;
import org.netbeans.api.visual.action.PopupMenuProvider;
import org.netbeans.api.visual.action.SelectProvider;
import org.netbeans.api.visual.model.ObjectState;
import org.netbeans.api.visual.widget.Widget;

public class LiveRangeWidget extends Widget {

    private final DiagramScene scene;
    private final Point start;
    private final Point end;
    private final Rectangle clientArea;

    public LiveRangeWidget(DiagramScene scene, Point start, Point end) {
        super(scene);
        this.scene = scene;
        this.start = start;
        this.end = end;

        int minX = start.x;
        int minY = start.y;
        int maxX = end.x;
        int maxY = end.y;
        if (minX > maxX) {
            int tmp = minX;
            minX = maxX;
            maxX = tmp;
        }

        if (minY > maxY) {
            int tmp = minY;
            minY = maxY;
            maxY = tmp;
        }

        setBackground(Color.BLACK);

        clientArea = new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
        clientArea.grow(5, 5);
        System.out.println("LiveRangeWidget::clientArea: " + clientArea);

    }

    @Override
    protected Rectangle calculateClientArea() {
        return clientArea;
    }

    @Override
    protected void paintWidget() {
        System.out.println("LiveRangeWidget::paintWidget() start = " + start + ", end = " + end);
        if (scene.getZoomFactor() < 0.1) {
            return;
        }

        Graphics2D g = getScene().getGraphics();
        g.setPaint(this.getBackground());
        float width = 1.0f;

        g.setStroke(new BasicStroke(2));
        g.drawLine(start.x, start.y, end.x, end.y);
    }
}

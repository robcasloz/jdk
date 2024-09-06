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
package com.sun.hotspot.igv.graph;

import com.sun.hotspot.igv.data.InputBlock;
import com.sun.hotspot.igv.data.InputLiveRange;
import com.sun.hotspot.igv.layout.Segment;
import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

public class LiveRangeSegment implements Segment {

    private InputLiveRange liveRange;
    private InputBlock block;
    private Point start;
    private Point end;

    protected LiveRangeSegment(InputLiveRange liveRange, InputBlock block) {
        this.liveRange = liveRange;
        this.block = block;
    }

    public InputLiveRange getLiveRange() {
        return liveRange;
    }

    public InputBlock getBlock() {
        return block;
    }

    public Point getStart() {
        return start;
    }

    public Point getEnd() {
        return end;
    }

    @Override
    public String toString() {
        return "LiveRangeSegment('" + liveRange + "', " + block.getName() + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LiveRangeSegment)) {
            return false;
        }
        return getLiveRange().equals(((LiveRangeSegment)o).getLiveRange())
            && getBlock().equals(((LiveRangeSegment)o).getBlock());
    }

}

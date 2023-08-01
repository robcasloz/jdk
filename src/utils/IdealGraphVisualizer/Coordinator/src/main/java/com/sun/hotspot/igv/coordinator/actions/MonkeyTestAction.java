/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */
package com.sun.hotspot.igv.coordinator.actions;

import com.sun.hotspot.igv.view.EditorTopComponent;
import javax.swing.Action;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class MonkeyTestAction extends CallableSystemAction {

    @Override
    public void performAction() {
        for (int i = 0; i < 1000; i++) {
            List<EditorTopComponent> topComponents = new ArrayList<>();
            WindowManager manager = WindowManager.getDefault();
            for (Mode m : manager.getModes()) {
                for (TopComponent t : manager.getOpenedTopComponents(m)) {
                    if (t instanceof EditorTopComponent) {
                        topComponents.add((EditorTopComponent) t);
                    }
                }
            }
            if (topComponents.isEmpty()) {
                return;
            }
            EditorTopComponent etc = topComponents.get(ThreadLocalRandom.current().nextInt(topComponents.size()));
            etc.doSomethingRandom();
        }
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(MonkeyTestAction.class, "CTL_MonkeyTestAction");
    }

    public MonkeyTestAction() {
        putValue(Action.SHORT_DESCRIPTION, "Go bananas...");
        putValue(Action.ACCELERATOR_KEY, Utilities.stringToKey("D-B"));
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }
}

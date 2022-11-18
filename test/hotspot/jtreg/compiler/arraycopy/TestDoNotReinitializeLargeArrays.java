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
 */

/**
 * @test
 * @bug 8295937
 * @summary Tests that arrays tightly coupled to copies are not
 *          double-initialized when they are known to be allocated in the slow
 *          path. This is the case if the arrays' size exceeds
 *          FastAllocateSizeLimit or TLAB allocation is globally disabled.
 * @library /test/lib /
 * @run driver compiler.arraycopy.TestDoNotReinitializeLargeArrays
 */

package compiler.arraycopy;

import compiler.lib.ir_framework.*;

public class TestDoNotReinitializeLargeArrays {

    public static final int S = 16;
    // This size must be smaller than FastAllocateSizeLimit*2 for C2 to expand a
    // fast allocation path.
    public static final int M = 512;
    public static final int FAST_SIZE_LIMIT = 1024;
    // This size must be greater than FastAllocateSizeLimit*2 for C2 to expand
    // only the slow allocation path.
    public static final int L = 4096;

    public static int[] source = new int[S];

    static public void main(String[] args) throws Exception {
        TestFramework framework = new TestFramework();
        // Reduce the fast-path allocation size limit to speed up the tests.
        framework.addFlags("-XX:FastAllocateSizeLimit=" + FAST_SIZE_LIMIT);
        Scenario[] scenarios = new Scenario[2];
        scenarios[0] = new Scenario(0, "-XX:-UseTLAB");
        scenarios[1] = new Scenario(1, "-XX:+UseTLAB");
        framework.addScenarios(scenarios);
        framework.start();
    }

    @Test
    @IR(applyIf = {"UseTLAB", "false"}, failOn = IRNode.CLEAR_ARRAY)
    @IR(applyIf = {"UseTLAB", "true"},  counts = {IRNode.CLEAR_ARRAY, ">= 1"})
    public static void testSmallAllocation() {
        int[] destination = new int[M];
        java.lang.System.arraycopy(source, 0, destination, 0, S);
    }

    @Test
    @IR(failOn = IRNode.CLEAR_ARRAY)
    public static void testLargeAllocation() {
        int[] destination = new int[L];
        java.lang.System.arraycopy(source, 0, destination, 0, S);
    }
}

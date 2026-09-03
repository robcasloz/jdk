/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.ciTypeFlow;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/**
 * @test
 * @summary TBD.
 * @library /test/lib /
 * @compile TestNodeSplitting.jasm
 * @run driver ${test.main.class}
 */

public class TestNodeSplittingMain {
    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-CINodeSplitting",
                                   "-XX:CompileCommand=inline,compiler.ciTypeFlow.TestNodeSplitting::*");
        TestFramework.runWithFlags("-XX:+CINodeSplitting",
                                   "-XX:CompileCommand=inline,compiler.ciTypeFlow.TestNodeSplitting::*");
    }

    @Test
    // This test contains an empty irreducible loop with no side effects. Making
    // it reducible allows C2 to optimize it as an empty method.
    @IR(applyIf = {"CINodeSplitting", "false"},
        counts = {IRNode.REGION, "> 0"})
    @IR(applyIf = {"CINodeSplitting", "true"},
        failOn = {IRNode.REGION})
    static void testBasic(boolean c) {
        TestNodeSplitting.testBasic(c);
    }

    @Test
    // This test performs a reduction over an array of integers, and includes a
    // secondary branch right into the middle of the reduction loop. Making it
    // reducible allows C2 to vectorize the reduction.
    @IR(applyIf = {"CINodeSplitting", "false"},
        failOn = {IRNode.ADD_REDUCTION_V})
    @IR(applyIf = {"CINodeSplitting", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"},
        counts = {IRNode.ADD_REDUCTION_V, "> 0"})
    static int testUnrollAndVectorize(int[] a, boolean c) {
        return TestNodeSplitting.testUnrollAndVectorize(a, c);
    }

    @Run(test = {"testBasic",
                 "testUnrollAndVectorize"},
         mode = RunMode.STANDALONE)
    static void runTests() {
        for (int i = 0; i < 10_000; i++) {
            testBasic(i % 2 == 0);
        }
        {
            int[] a = new int[10];
            for (int i = 0; i < a.length; i++) {
                a[i] = 1;
            }
            for (int i = 0; i < 10_000; i++) {
                boolean enterViaOriginalHeader = i % 2 == 0;
                int sum = testUnrollAndVectorize(a, enterViaOriginalHeader);
                Asserts.assertTrue(!enterViaOriginalHeader || sum == a.length);
            }
        }
    }

}

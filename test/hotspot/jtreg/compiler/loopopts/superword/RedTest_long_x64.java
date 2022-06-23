/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8240248
 * @summary Add C2 x86 Superword support for scalar logical reduction optimizations : long test
 * @library /test/lib /
 * @requires os.simpleArch == "x64"
 * @run driver compiler.loopopts.superword.RedTest_long_x64
 */

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;

public class RedTest_long_x64 {
    static final int NUM = 512;
    static final int ITER = 8000;
    public static void main(String[] args) throws Exception {
        TestFramework framework = new TestFramework();
        framework.addFlags("-XX:+IgnoreUnrecognizedVMOptions",
                           "-XX:LoopUnrollLimit=250",
                           "-XX:CompileThresholdScaling=0.1",
                           "-XX:-TieredCompilation",
                           "-XX:+RecomputeReductions");
        int i = 0;
        Scenario[] scenarios = new Scenario[8];
        for (String reductionSign : new String[] {"+", "-"}) {
            for (int maxUnroll : new int[] {2, 4, 8, 16}) {
                scenarios[i] = new Scenario(i, "-XX:" + reductionSign + "SuperWordReductions",
                                               "-XX:LoopMaxUnroll=" + maxUnroll);
                i++;
            }
        }
        framework.addScenarios(scenarios);
        framework.start();
    }

    @Run(test = {"sumReductionImplement",
                 "orReductionImplement",
                 "andReductionImplement",
                 "xorReductionImplement",
                 "mulReductionImplement"},
        mode = RunMode.STANDALONE)
    public static void runTests() throws Exception {
        long[] a = new long[NUM];
        long[] b = new long[NUM];
        long[] c = new long[NUM];
        long[] d = new long[NUM];
        reductionInit1(a, b, c);
        long total = 0;
        long valid = 0;
        for (int j = 0; j < ITER; j++) {
            total = sumReductionImplement(a, b, c, d);
        }
        for (int j = 0; j < d.length; j++) {
            valid += d[j];
        }
        testCorrectness(total, valid, "Add Reduction");

        valid = 0;
        for (int j = 0; j < ITER; j++) {
            total = orReductionImplement(a, b, c, d);
        }
        for (int j = 0; j < d.length; j++) {
            valid |= d[j];
        }
        testCorrectness(total, valid, "Or Reduction");

        valid = -1;
        for (int j = 0; j < ITER; j++) {
            total = andReductionImplement(a, b, c, d);
        }
        for (int j = 0; j < d.length; j++) {
            valid &= d[j];
        }
        testCorrectness(total, valid, "And Reduction");

        valid = -1;
        for (int j = 0; j < ITER; j++) {
            total = xorReductionImplement(a, b, c, d);
        }
        for (int j = 0; j < d.length; j++) {
            valid ^= d[j];
        }
        testCorrectness(total, valid, "Xor Reduction");

        reductionInit2(a, b, c);
        valid = 1;
        for (int j = 0; j < ITER; j++) {
            total = mulReductionImplement(a, b, c, d);
        }
        for (int j = 0; j < d.length; j++) {
            valid *= d[j];
        }
        testCorrectness(total, valid, "Mul Reduction");
    }

    public static void reductionInit1(
            long[] a,
            long[] b,
            long[] c) {
        for (int i = 0; i < a.length; i++) {
           a[i] = (i%2) + 0x4099;
           b[i] = (i%2) + 0x1033;
           c[i] = (i%2) + 0x455;
        }
    }

    public static void reductionInit2(
            long[] a,
            long[] b,
            long[] c) {
        for (int i = 0; i < a.length; i++) {
           a[i] = 0x11;
           b[i] = 0x12;
           c[i] = 0x13;
        }
    }

    @Test
    @IR(applyIf = {"SuperWordReductions", "false"},
        failOn = {IRNode.ADD_REDUCTION_V_L})
    @IR(applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", ">= 8",  "UseSSE", ">= 3"},
        counts = {IRNode.ADD_REDUCTION_V_L, ">= 1"})
    public static long sumReductionImplement(
            long[] a,
            long[] b,
            long[] c,
            long[] d) {
        long total = 0;
        for (int i = 0; i < a.length; i++) {
            d[i] = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total += d[i];
        }
        return total;
    }

    @Test
    @IR(applyIf = {"SuperWordReductions", "false"},
        failOn = {IRNode.OR_REDUCTION_V})
    @IR(applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", ">= 8", "UseSSE", ">= 3"},
        counts = {IRNode.OR_REDUCTION_V, ">= 1"})
    public static long orReductionImplement(
            long[] a,
            long[] b,
            long[] c,
            long[] d) {
        long total = 0;
        for (int i = 0; i < a.length; i++) {
            d[i] = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total |= d[i];
        }
        return total;
    }

    @Test
    @IR(applyIf = {"SuperWordReductions", "false"},
        failOn = {IRNode.AND_REDUCTION_V})
    @IR(applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", ">= 8", "UseSSE", ">= 3"},
        counts = {IRNode.AND_REDUCTION_V, ">= 1"})
    public static long andReductionImplement(
            long[] a,
            long[] b,
            long[] c,
            long[] d) {
        long total = -1;
        for (int i = 0; i < a.length; i++) {
            d[i] = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total &= d[i];
        }
        return total;
    }

    @Test
    @IR(applyIf = {"SuperWordReductions", "false"},
        failOn = {IRNode.XOR_REDUCTION_V})
    @IR(applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", ">= 8", "UseSSE", ">= 3"},
        counts = {IRNode.XOR_REDUCTION_V, ">= 1"})
    public static long xorReductionImplement(
            long[] a,
            long[] b,
            long[] c,
            long[] d) {
        long total = -1;
        for (int i = 0; i < a.length; i++) {
            d[i] = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total ^= d[i];
        }
        return total;
    }

    @Test
    @IR(applyIf = {"SuperWordReductions", "false"},
        failOn = {IRNode.MUL_REDUCTION_V_L})
    @IR(applyIfAnd = {"SuperWordReductions", "true", "LoopMaxUnroll", ">= 8", "UseAVX", ">= 3"},
        counts = {IRNode.MUL_REDUCTION_V_L, ">= 1"})
    public static long mulReductionImplement(
            long[] a,
            long[] b,
            long[] c,
            long[] d) {
        long total = 1;
        for (int i = 0; i < a.length; i++) {
            d[i] = (a[i] * b[i]) + (a[i] * c[i]) + (b[i] * c[i]);
            total = total*d[i];
        }
        return total;
    }

    public static void testCorrectness(
            long total,
            long valid,
            String op) throws Exception {
        if (total == valid) {
            System.out.println(op + ": Success");
        } else {
            System.out.println("Invalid total: " + total);
            System.out.println("Expected value = " + valid);
            throw new Exception(op + ": Failed");
        }
    }
}


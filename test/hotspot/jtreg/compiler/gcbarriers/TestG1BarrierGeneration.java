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
 */

package compiler.gcbarriers;

import compiler.lib.ir_framework.*;

/**
 * @test
 * @summary Test that G1 barriers are generated and optimized as expected.
 * @library /test/lib /
 * @requires vm.gc.G1
 * @run driver compiler.gcbarriers.TestG1BarrierGeneration
 */

public class TestG1BarrierGeneration {
    static final String PRE_ONLY = "pre";
    static final String PRE_AND_POST = "pre post";
    static final String PRE_AND_POST_NOT_NULL = "pre post notnull";

    static class Outer {
        Object f;
    }

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        Scenario[] scenarios = new Scenario[2];
        scenarios[0] = new Scenario(0, "-XX:-UseCompressedOops");
        scenarios[1] = new Scenario(1, "-XX:+UseCompressedOops");
        framework.addScenarios(scenarios);
        framework.start();
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    public static void testStore(Outer o, Object o1) {
        o.f = o1;
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_STORE_N_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    public static void testStoreNull(Outer o) {
        o.f = null;
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_AND_POST_NOT_NULL, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, PRE_AND_POST_NOT_NULL, "1"},
        phase = CompilePhase.FINAL_CODE)
    public static void testStoreNotNull(Outer o, Object o1) {
        if (o1.hashCode() == 42) {
            return;
        }
        o.f = o1;
    }

    @Run(test = {"testStore",
                 "testStoreNull",
                 "testStoreNotNull"})
    public void runGenerationTests() {
        Outer o = new Outer();
        Object o1 = new Object();
        testStore(o, o1);
        testStoreNull(o);
        testStoreNotNull(o, o1);
    }

}

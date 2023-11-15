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
import java.lang.invoke.VarHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

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

    static final VarHandle fVarHandle;
    static {
        MethodHandles.Lookup l = MethodHandles.lookup();
        try {
            fVarHandle = l.findVarHandle(Outer.class, "f", Object.class);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        Scenario[] scenarios = new Scenario[2];
        for (int i = 0; i < 2; i++) {
            scenarios[i] = new Scenario(i, "-XX:CompileCommand=inline,java.lang.ref.*::*",
                                        "-XX:" + (i == 0 ? "-" : "+") + "UseCompressedOops");
        }
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

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_AND_POST, "2"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, PRE_AND_POST, "2"},
        phase = CompilePhase.FINAL_CODE)
    public static void testStoreTwice(Outer o, Outer p, Object o1) {
        o.f = o1;
        p.f = o1;
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        failOn = {IRNode.G1_STORE_P},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        failOn = {IRNode.G1_STORE_N, IRNode.G1_ENCODE_P_AND_STORE_N},
        phase = CompilePhase.FINAL_CODE)
    public static Outer testStoreOnNewObject(Object o1) {
        Outer o = new Outer();
        o.f = o1;
        return o;
    }

    @Run(test = {"testStore",
                 "testStoreNull",
                 "testStoreNotNull",
                 "testStoreTwice",
                 "testStoreOnNewObject"})
    public void runStoreTests() {
        {
            Outer o = new Outer();
            Object o1 = new Object();
            testStore(o, o1);
            assert(o.f == o1);
        }
        {
            Outer o = new Outer();
            testStoreNull(o);
            assert(o.f == null);
        }
        {
            Outer o = new Outer();
            Object o1 = new Object();
            testStoreNotNull(o, o1);
            assert(o.f == o1);
        }
        {
            Outer o = new Outer();
            Outer p = new Outer();
            Object o1 = new Object();
            testStoreTwice(o, p, o1);
            assert(o.f == o1);
            assert(p.f == o1);
        }
        {
            Object o1 = new Object();
            Outer o = testStoreOnNewObject(o1);
            assert(o.f == o1);
        }
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    public static void testArrayStore(Object[] a, int index, Object o1) {
        a[index] = o1;
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_STORE_N_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    public static void testArrayStoreNull(Object[] a, int index) {
        a[index] = null;
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_AND_POST_NOT_NULL, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, PRE_AND_POST_NOT_NULL, "1"},
        phase = CompilePhase.FINAL_CODE)
    public static void testArrayStoreNotNull(Object[] a, int index, Object o1) {
        if (o1.hashCode() == 42) {
            return;
        }
        a[index] = o1;
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_STORE_P_WITH_BARRIER_FLAG, PRE_AND_POST, "2"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_ENCODE_P_AND_STORE_N_WITH_BARRIER_FLAG, PRE_AND_POST, "2"},
        phase = CompilePhase.FINAL_CODE)
    public static void testArrayStoreTwice(Object[] a, Object[] b, int index, Object o1) {
        a[index] = o1;
        b[index] = o1;
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        failOn = {IRNode.G1_STORE_P},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        failOn = {IRNode.G1_STORE_N, IRNode.G1_ENCODE_P_AND_STORE_N},
        phase = CompilePhase.FINAL_CODE)
    public static Object[] testStoreOnNewArray(Object o1) {
        Object[] a = new Object[10];
        // The index needs to be concrete for C2 to detect that it is safe to
        // remove the pre-barrier.
        a[4] = o1;
        return a;
    }

    @Run(test = {"testArrayStore",
                 "testArrayStoreNull",
                 "testArrayStoreNotNull",
                 "testArrayStoreTwice",
                 "testStoreOnNewArray"})
    public void runArrayStoreTests() {
        {
            Object[] a = new Object[10];
            Object o1 = new Object();
            testArrayStore(a, 4, o1);
            assert(a[4] == o1);
        }
        {
            Object[] a = new Object[10];
            testArrayStoreNull(a, 4);
            assert(a[4] == null);
        }
        {
            Object[] a = new Object[10];
            Object o1 = new Object();
            testArrayStoreNotNull(a, 4, o1);
            assert(a[4] == o1);
        }
        {
            Object[] a = new Object[10];
            Object[] b = new Object[10];
            Object o1 = new Object();
            testArrayStoreTwice(a, b, 4, o1);
            assert(a[4] == o1);
            assert(b[4] == o1);
        }
        {
            Object o1 = new Object();
            Object[] a = testStoreOnNewArray(o1);
            assert(a[4] == o1);
        }
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_COMPARE_AND_EXCHANGE_P_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_COMPARE_AND_EXCHANGE_N_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    static Object testCompareAndExchange(Outer o, Object oldVal, Object newVal) {
        return fVarHandle.compareAndExchange(o, oldVal, newVal);
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_COMPARE_AND_SWAP_P_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_COMPARE_AND_SWAP_N_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    static boolean testCompareAndSwap(Outer o, Object oldVal, Object newVal) {
        return fVarHandle.compareAndSet(o, oldVal, newVal);
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_GET_AND_SET_P_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_GET_AND_SET_N_WITH_BARRIER_FLAG, PRE_AND_POST, "1"},
        phase = CompilePhase.FINAL_CODE)
    static Object testGetAndSet(Outer o, Object newVal) {
        return fVarHandle.getAndSet(o, newVal);
    }

    @Run(test = {"testCompareAndExchange",
                 "testCompareAndSwap",
                 "testGetAndSet"})
    public void runAtomicTests() {
        {
            Outer o = new Outer();
            Object oldVal = new Object();
            o.f = oldVal;
            Object newVal = new Object();
            Object oldVal2 = testCompareAndExchange(o, oldVal, newVal);
            assert(oldVal == oldVal2);
            assert(o.f == newVal);
        }
        {
            Outer o = new Outer();
            Object oldVal = new Object();
            o.f = oldVal;
            Object newVal = new Object();
            boolean b = testCompareAndSwap(o, oldVal, newVal);
            assert(b);
            assert(o.f == newVal);
        }
        {
            Outer o = new Outer();
            Object oldVal = new Object();
            o.f = oldVal;
            Object newVal = new Object();
            Object oldVal2 = testGetAndSet(o, newVal);
            assert(oldVal == oldVal2);
            assert(o.f == newVal);
        }
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_LOAD_P_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_LOAD_N_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    static Object testLoadSoftReference(SoftReference<Object> ref) {
        return ref.get();
    }

    @Test
    @IR(applyIf = {"UseCompressedOops", "false"},
        counts = {IRNode.G1_LOAD_P_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    @IR(applyIf = {"UseCompressedOops", "true"},
        counts = {IRNode.G1_LOAD_N_WITH_BARRIER_FLAG, PRE_ONLY, "1"},
        phase = CompilePhase.FINAL_CODE)
    static Object testLoadWeakReference(WeakReference<Object> ref) {
        return ref.get();
    }

    @Run(test = {"testLoadSoftReference",
                 "testLoadWeakReference"})
    public void runReferenceTests() {
        {
            Object o1 = new Object();
            SoftReference<Object> sref = new SoftReference<Object>(o1);
            Object o2 = testLoadSoftReference(sref);
            assert(o2 == o1 || o2 == null);
        }
        {
            Object o1 = new Object();
            WeakReference<Object> wref = new WeakReference<Object>(o1);
            Object o2 = testLoadWeakReference(wref);
            assert(o2 == o1 || o2 == null);
        }
    }
}

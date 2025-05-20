/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.loopopts;

import compiler.lib.compile_framework.*;
import compiler.lib.template_framework.Template;
import static compiler.lib.template_framework.Template.body;

/**
 * @test
 * @bug 8351468
 * @summary Test that loads anti-dependent on array fill intrinsics are
 *          scheduled correctly, for different load and array fill types.
 *          See detailed comments in testShort() below.
 * @library /test/lib /
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions
 *                   -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileOnly=compiler.loopopts.TestArrayFillAntiDependence::test*
 *                   -XX:CompileCommand=quiet -XX:LoopUnrollLimit=0 -XX:+OptimizeFill
 *                   compiler.loopopts.TestArrayFillAntiDependence
 * @run main compiler.loopopts.TestArrayFillAntiDependenceTemplated
 */

public class TestArrayFillAntiDependenceTemplated {

    public static void main(String[] args) {
        CompileFramework comp = new CompileFramework();
        comp.addJavaSourceCode("TestArrayFillAntiDependence", generate(comp));
        comp.compile();
        comp.invoke("TestArrayFillAntiDependence", "main", new Object[] {});
    }

    public static String generate(CompileFramework comp) {

        // Template for a test.
        var testTemplate = Template.make("type", "operation", "value", (String type, String operation, String value) -> body(
        """
        static #type test#type(int pos, int samePos) {
            assert pos == samePos;
            #type total = #value;
            #type[] array = new #type[N];
            array[pos] = VAL_#type;
            for (int i = 0; i < M; i++) {
                total #operation array[samePos];
                for (int t = 0; t < array.length; t++) {
                    array[t] = #value;
                }
            }
            return total;
        }
        """
        ));

        // Template for a test run.
        var runTemplate = Template.make("type", (String type) -> body(
        // In the token below, it would be useful to be able to write something like this,
        // to match the existing code in TestArrayFillAntiDependence.java:
        // Asserts.assertEquals(#toUpperCase(#type)_VAL, result);
        // toUpperCase could be part of a collection of util String manipulation
        // functions, or (preferably) a user-defined one.
            """
            for (int i = 0; i < 10_000; i++) {
                #type result = test#type(0, 0);
                Asserts.assertEquals(VAL_#type, result);
            }
            """
        ));

        // Template for the class.
        var classTemplate = Template.make(() -> body(
            """
            import jdk.test.lib.Asserts;

            public class TestArrayFillAntiDependence {

                static int N = 10;
                static short M = 4;
                static boolean VAL_boolean = true;
                static char VAL_char = 42;
                static float VAL_float = 42.0f;
                static double VAL_double = 42.0;
                static byte VAL_byte = 42;
                static short VAL_short = 42;
                static int VAL_int = 42;
                static long VAL_long = 42;

            """,
                testTemplate.asToken("boolean", "|=", "false"),
                testTemplate.asToken("char", "+=", "0"),
                testTemplate.asToken("float", "+=", "0.0f"),
                testTemplate.asToken("double", "+=", "0.0"),
                testTemplate.asToken("byte", "+=", "0"),
                testTemplate.asToken("short", "+=", "0"),
                testTemplate.asToken("int", "+=", "0"),
                testTemplate.asToken("long", "+=", "0"),
            """
                public static void main() {
            """,
                runTemplate.asToken("boolean"),
                runTemplate.asToken("char"),
                runTemplate.asToken("float"),
                runTemplate.asToken("double"),
                runTemplate.asToken("byte"),
                runTemplate.asToken("short"),
                runTemplate.asToken("int"),
                runTemplate.asToken("long"),
            """
                }
            }
            """
        ));

        return classTemplate.render();
    }

}

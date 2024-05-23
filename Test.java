/*
  Derived from test/hotspot/jtreg/compiler/arraycopy/TestObjectArrayClone.java. Run with:
  ./build/linux-x64-debug/jdk/bin/javac Test.java && ./build/linux-x64-debug/jdk/bin/java --add-opens java.base/java.lang=ALL-UNNAMED -Xbatch -XX:-TieredCompilation -XX:-UseTypeProfile -XX:+UseZGC -XX:-PrintCompilation -XX:-CICompileOSR Test
 */

import java.lang.invoke.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Test {

    public static Object testCloneObject(Method clone, Object obj) throws Exception {
        return clone.invoke(obj);
    }

    public static void main(String[] args) throws Exception {
        Method clone = Object.class.getDeclaredMethod("clone");
        clone.setAccessible(true);

        for (int i = 0; i < 50_000; i++) {
            String[] arr1 = new String[42];
            for (int j = 0; j < arr1.length; j++) {
                arr1[j] = new String(Integer.toString(j));
            }
            String[] arr2 = (String[]) testCloneObject(clone, arr1);
            verifyStr(arr1, arr2);
        }
    }

    public static void verifyStr(String[] arr1, String[] arr2) {
        if (arr1 == arr2) {
            throw new RuntimeException("Must not be the same");
        }
        if (arr1.length != arr2.length) {
            throw new RuntimeException("Must have the same length");
        }
        for (int i = 0; i < arr1.length; i++) {
            if (arr1[i] != arr2[i]) {
                throw new RuntimeException("Fail cloned element not the same: " + i);
            }
            if (!arr1[i].equals(arr2[i])) {
                throw new RuntimeException("Fail cloned element content not the same");
            }
        }
    }
}

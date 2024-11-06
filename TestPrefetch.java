public class TestPrefetch {
    static int[] testNewArray() {
        return new int[14];
    }
    static Object testNewInstance() {
        return new Object();
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            testNewArray();
        }
        for (int i = 0; i < 10_000; i++) {
            testNewInstance();
        }
    }
}

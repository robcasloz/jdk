public class TestAddPChain {
    static void test(int[] arr) {
        for (long i = 6; i < 14; i++) {
            arr[(int)i] = 1;
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            test(new int[14]);
        }
    }
}

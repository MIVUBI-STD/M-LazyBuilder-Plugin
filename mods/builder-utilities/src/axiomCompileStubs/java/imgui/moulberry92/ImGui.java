package imgui.moulberry92;

/**
 * Compile-only signatures for the ImGui surface exposed by Axiom 5.3.0.
 * The produced Builder Utilities JAR does not contain this class.
 */
public final class ImGui {
    private ImGui() {}
    public static void textWrapped(String text) {}
    public static void separator() {}
    public static boolean button(String label) { return false; }
    public static boolean sliderInt(String label, int[] value, int min, int max) { return false; }
    public static boolean sliderFloat(String label, float[] value, float min, float max) { return false; }
}

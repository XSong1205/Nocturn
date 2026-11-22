package dev.nocturn.app.ui.theme;

import com.materialkolor.quantize.QuantizerWsmeans;
import java.util.Map;

/**
 * Isolates the JVM-public MCU refinement until Celebi's Wu moment overflow is fixed upstream.
 * MaterialKolor keeps this class internal to Kotlin, so use its Java ABI with the pinned 6.0.0
 * dependency instead of suppressing Kotlin visibility errors with unspecified compiler behavior.
 * Artwork regression tests cover this boundary when the dependency is upgraded.
 */
final class ArtworkQuantizer {
    private ArtworkQuantizer() {}

    static Map<Integer, Integer> refine(int[] pixels, int[] clusters, int maxColors) {
        return QuantizerWsmeans.INSTANCE.quantize(pixels, clusters, maxColors);
    }
}

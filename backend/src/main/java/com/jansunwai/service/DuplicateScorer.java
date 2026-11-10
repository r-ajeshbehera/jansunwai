package com.jansunwai.service;

import com.jansunwai.config.AppProperties;

import java.util.Optional;

/**
 * Pure scoring (unit-tested): total = 0.60 x semantic similarity + 0.25 x closeness + 0.15 x same category.
 *  - closeness = 1 - distance/radius; a known distance beyond the radius means "different place" -> no candidate;
 *    unknown location (no GPS on one side) -> neutral 0.5.
 *  - category: same = 1, different = 0, unknown = 0.5.
 * A candidate needs semantic >= minSemantic AND total >= minTotal. A human still decides.
 */
public final class DuplicateScorer {
    public static final double W_SEMANTIC = 0.60, W_GEO = 0.25, W_CATEGORY = 0.15;

    private DuplicateScorer() { }

    public record Score(double semantic, double geo, double category, double total) { }

    public static Optional<Score> score(double semantic, Integer distanceMeters, String categoryA, String categoryB,
                                        AppProperties.Duplicates cfg) {
        if (semantic < cfg.minSemantic()) return Optional.empty();
        double geo;
        if (distanceMeters == null) geo = 0.5;
        else if (distanceMeters > cfg.radiusMeters()) return Optional.empty();
        else geo = 1.0 - (double) distanceMeters / cfg.radiusMeters();
        double cat = categoryA == null || categoryB == null ? 0.5 : categoryA.equals(categoryB) ? 1.0 : 0.0;
        double total = W_SEMANTIC * semantic + W_GEO * geo + W_CATEGORY * cat;
        if (total < cfg.minTotal()) return Optional.empty();
        return Optional.of(new Score(round(semantic), round(geo), cat, round(total)));
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}

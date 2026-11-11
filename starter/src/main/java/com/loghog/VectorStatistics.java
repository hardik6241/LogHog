package com.loghog;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

public class VectorStatistics {
    private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;

    public static double[] calculateMeanAndStdDev(int[] data) {
        if (data == null || data.length == 0) {
            return new double[]{0.0, 0.0};
        }

        int n = data.length;
        long sum = 0;

        int i = 0;
        int upperBound = SPECIES.loopBound(n);

        if (upperBound > 0) {
            IntVector vecSum = IntVector.zero(SPECIES);
            for (; i < upperBound; i += SPECIES.length()) {
                IntVector v = IntVector.fromArray(SPECIES, data, i);
                vecSum = vecSum.add(v);
            }
            sum = vecSum.reduceLanesToLong(VectorOperators.ADD);
        }

        // Scalar remainder loop for elements outside vector lanes
        for (; i < n; i++) {
            sum += data[i];
        }

        double mean = (double) sum / n;

        double sumSquaredDiff = 0.0;
        for (int j = 0; j < n; j++) {
            double diff = data[j] - mean;
            sumSquaredDiff += diff * diff;
        }

        double variance = sumSquaredDiff / n;
        double stdDev = Math.sqrt(variance);

        return new double[]{mean, stdDev};
    }
}
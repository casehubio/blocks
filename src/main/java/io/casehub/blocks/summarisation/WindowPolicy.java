package io.casehub.blocks.summarisation;

public record WindowPolicy(long maxAge, int maxCount) {
    public WindowPolicy {
        if (maxAge < 0) throw new IllegalArgumentException("maxAge must be >= 0, was: " + maxAge);
        if (maxCount < 0) throw new IllegalArgumentException("maxCount must be >= 0, was: " + maxCount);
        if (maxAge == 0 && maxCount == 0) throw new IllegalArgumentException("At least one of maxAge or maxCount must be positive");
    }

    public static WindowPolicy ofCount(int maxCount) {
        return new WindowPolicy(0, maxCount);
    }

    public static WindowPolicy ofAge(long maxAgeMs) {
        return new WindowPolicy(maxAgeMs, 0);
    }

    public static WindowPolicy of(long maxAgeMs, int maxCount) {
        return new WindowPolicy(maxAgeMs, maxCount);
    }
}

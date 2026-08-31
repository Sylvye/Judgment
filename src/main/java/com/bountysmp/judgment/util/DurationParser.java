package com.bountysmp.judgment.util;

public final class DurationParser {
    private DurationParser() {
    }

    public static Long parseMillis(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase();
        if (value.isBlank()) {
            return null;
        }

        long multiplier = 1L;
        if (value.endsWith("ms")) {
            value = value.substring(0, value.length() - 2);
        } else if (value.endsWith("s")) {
            multiplier = 1_000L;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("m")) {
            multiplier = 60_000L;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("h")) {
            multiplier = 3_600_000L;
            value = value.substring(0, value.length() - 1);
        }

        try {
            long amount = Long.parseLong(value);
            if (amount < 0) {
                return null;
            }
            return Math.multiplyExact(amount, multiplier);
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }

    public static String formatMillis(long millis) {
        if (millis % 3_600_000L == 0L) {
            return millis / 3_600_000L + "h";
        }
        if (millis % 60_000L == 0L) {
            return millis / 60_000L + "m";
        }
        if (millis % 1_000L == 0L) {
            return millis / 1_000L + "s";
        }
        return millis + "ms";
    }
}

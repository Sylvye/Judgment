package com.bountysmp.judgment.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DurationParserTest {
    @Test
    void parsesSupportedDurations() {
        assertEquals(0L, DurationParser.parseMillis("0s"));
        assertEquals(500L, DurationParser.parseMillis("500ms"));
        assertEquals(30_000L, DurationParser.parseMillis("30s"));
        assertEquals(300_000L, DurationParser.parseMillis("5m"));
        assertEquals(3_600_000L, DurationParser.parseMillis("1h"));
    }

    @Test
    void rejectsInvalidDurations() {
        assertNull(DurationParser.parseMillis(""));
        assertNull(DurationParser.parseMillis("-1s"));
        assertNull(DurationParser.parseMillis("abc"));
    }
}

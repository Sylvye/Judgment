package com.bountysmp.judgment.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatItemInputTest {
    @Test void acceptsSupportedValues() {
        assertEquals(-1.0, SettingsGui.parseCombatItemSeconds("-1"));
        assertEquals(0.0, SettingsGui.parseCombatItemSeconds("0"));
        assertEquals(2.75, SettingsGui.parseCombatItemSeconds(" 2.75 "));
    }

    @Test void rejectsUnsupportedValues() {
        assertNull(SettingsGui.parseCombatItemSeconds("-2"));
        assertNull(SettingsGui.parseCombatItemSeconds("NaN"));
        assertNull(SettingsGui.parseCombatItemSeconds("Infinity"));
        assertNull(SettingsGui.parseCombatItemSeconds("later"));
    }
}

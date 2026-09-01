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

    @Test void validatesExplosiveDamageModifiers() {
        assertEquals(0.0, SettingsGui.parseDamageModifier("0"));
        assertEquals(0.5, SettingsGui.parseDamageModifier("0.5"));
        assertEquals(12.0, SettingsGui.parseDamageModifier("12"));
        assertNull(SettingsGui.parseDamageModifier("-0.1"));
        assertNull(SettingsGui.parseDamageModifier("NaN"));
        assertNull(SettingsGui.parseDamageModifier("Infinity"));
    }
}

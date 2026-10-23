package org.duckcatmine.apocalypse.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsServiceTest {
    @Test
    void valuesAreCheckedAgainstTheirBounds() {
        SettingsService.Setting density = SettingsService.find("density");
        assertEquals(2.5, SettingsService.parse(density, "2,5"));
        assertNull(SettingsService.parse(density, "9"));
        assertNull(SettingsService.parse(density, "beaucoup"));
        SettingsService.Setting cap = SettingsService.find("global-cap");
        assertEquals(120, SettingsService.parse(cap, "120"));
        assertNull(SettingsService.parse(cap, "12.5"));
        SettingsService.Setting fauna = SettingsService.find("fauna");
        assertEquals(true, SettingsService.parse(fauna, "on"));
        assertEquals(false, SettingsService.parse(fauna, "non"));
        assertNull(SettingsService.parse(fauna, "peut-être"));
        assertNull(SettingsService.find("database.path"));
    }

    @Test
    void presetsOnlyUseKnownSettingsWithinBounds() {
        assertEquals(3, SettingsService.PRESETS.size());
        for (Map.Entry<String, Map<String, Object>> preset : SettingsService.PRESETS.entrySet()) {
            assertNotNull(SettingsService.PRESET_LABELS.get(preset.getKey()));
            preset.getValue().forEach((key, value) -> {
                SettingsService.Setting setting = SettingsService.find(key);
                assertNotNull(setting, preset.getKey() + " uses " + key);
                assertNotNull(SettingsService.parse(setting, String.valueOf(value)), preset.getKey() + " " + key + "=" + value);
            });
        }
        double relaxed = ((Number) SettingsService.PRESETS.get("relaxed").get("density")).doubleValue();
        double nightmare = ((Number) SettingsService.PRESETS.get("nightmare").get("density")).doubleValue();
        assertTrue(relaxed < nightmare);
    }

    @Test
    void everySettingPathExistsInTheShippedConfiguration() throws Exception {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        try (var reader = new java.io.InputStreamReader(java.util.Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")),
                java.nio.charset.StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        ApocalypseConfig config = ApocalypseConfig.parse(yaml);
        for (SettingsService.Setting setting : SettingsService.CATALOG) {
            assertTrue(yaml.contains(setting.path()), setting.path());
            assertNotNull(setting.current().apply(config), setting.key());
        }
    }
}

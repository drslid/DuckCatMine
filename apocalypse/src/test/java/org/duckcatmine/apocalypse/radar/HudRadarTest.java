package org.duckcatmine.apocalypse.radar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;

class HudRadarTest {
    private static int advance(String text) {
        int total = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0xE300 && c <= 0xE307) total -= 1 << (c - 0xE300);
            else if (c >= 0xE310 && c <= 0xE317) total += 1 << (c - 0xE310);
            else if (c >= 0xE200 && c < 0xE200 + HudRadarService.SIZE) total += 3;
        }
        return total;
    }

    @Test void spacesAddUpExactly() {
        for (int n : new int[] {-255, -69, -1, 1, 37, 269}) assertEquals(n, advance(HudRadarService.space(n)));
    }

    @Test void gridRowsReturnToTheirStartAndKeepTheOffset() {
        int[][] colors = new int[HudRadarService.SIZE][HudRadarService.SIZE];
        colors[3][4] = HudRadarService.RED;
        Component title = HudRadarService.compose(colors);
        StringBuilder text = new StringBuilder();
        int runs = 0;
        for (Component child : title.children()) { text.append(((TextComponent) child).content()); runs++; }
        int width = HudRadarService.SIZE * 3;
        assertEquals(2 * HudRadarService.OFFSET + width + width, advance(text.toString()));
        assertTrue(runs < HudRadarService.SIZE * 4, "colours are merged into runs");
        assertEquals(HudRadarService.SIZE * HudRadarService.SIZE, text.chars().filter(c -> c >= 0xE200 && c < 0xE200 + HudRadarService.SIZE).count());
    }
}

package org.duckcatmine.apocalypse.radar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;

class HudRadarTest {
    /** Advances of the radar font: 1 px glyphs advance 2 (vanilla adds 1), digits advance 4. */
    private static int advance(String text) {
        int total = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0xE300 && c <= 0xE307) total -= 1 << (c - 0xE300);
            else if (c >= 0xE310 && c <= 0xE317) total += 1 << (c - 0xE310);
            else if (c >= 0xE200 && c < 0xE200 + HudRadarService.ROWS) total += 2;
            else if (c == 0xE40A) total += 3;
            else if (c >= 0xE400 && c < 0xE40D) total += 4;
        }
        return total;
    }

    private static String flatten(Component title) {
        StringBuilder text = new StringBuilder();
        for (Component child : title.children()) text.append(((TextComponent) child).content());
        return text.toString();
    }

    @Test void spacesAddUpExactly() {
        for (int n : new int[] {-255, -57, -1, 1, 37, 300}) assertEquals(n, advance(HudRadarService.space(n)));
    }

    @Test void pixelsTouchAndRowsReturnSoTheTitleWidthIsConstant() {
        Integer[][] pixels = new Integer[HudRadarService.ROWS][HudRadarService.DIAMETER];
        pixels[3][4] = HudRadarService.RED;
        pixels[3][5] = HudRadarService.RED;
        pixels[10][20] = HudRadarService.BLUE;
        String plain = flatten(HudRadarService.compose(pixels, null)), info = flatten(HudRadarService.compose(pixels, "▣ 4:32 230m"));
        assertEquals(2 * HudRadarService.OFFSET, advance(plain));
        assertEquals(2 * HudRadarService.OFFSET, advance(info));
        assertEquals(3, plain.chars().filter(c -> c >= 0xE200 && c < 0xE200 + HudRadarService.ROWS).count());
        assertTrue(info.chars().anyMatch(c -> c == 0xE40C), "crate icon");
    }

    @Test void forwardIsUpAndRightIsRight() {
        double[] ahead = HudRadarService.screen(0, 10, 0f), east = HudRadarService.screen(10, 0, 180f);
        assertTrue(ahead[1] < 0 && Math.abs(ahead[0]) < 1e-9, "looking south, south is up");
        assertTrue(east[0] > 0 && Math.abs(east[1]) < 1e-9, "looking north, east is on the right");
    }
}

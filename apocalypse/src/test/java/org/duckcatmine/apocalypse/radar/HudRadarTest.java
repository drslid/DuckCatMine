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
            else if (c == 0xE40A || c == 0xE42A) total += 3;
            else if (c >= 0xE400 && c < 0xE40F || c >= 0xE420 && c < 0xE42F) total += 4;
            else if (c >= 0xE500 && c < 0xE580) total += HudRadarService.RING_ADVANCE;
            else if (c >= 0xE600 && c < 0xE680) total += 4;
            else if (c >= 0xE680 && c < 0xE6F0) total += 6;
            else if (c == 0xE6F0) total += 10;
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

    @Test void sharpMarksAndTextLinesKeepTheWidthConstant() {
        Integer[][] pixels = new Integer[HudRadarService.ROWS][HudRadarService.DIAMETER];
        var marks = java.util.List.of(HudRadarService.arrow(40, -3, HudRadarService.PURPLE), HudRadarService.dot(-5, 7, false, HudRadarService.RED),
                HudRadarService.dot(20, -20, true, HudRadarService.BLUE), new HudRadarService.Mark(24, (char) 0xE6F0, 10, HudRadarService.WHITE));
        String text = flatten(HudRadarService.compose(pixels, marks, "▣ 1:05 80m", "42ms 1h05"));
        assertEquals(2 * HudRadarService.OFFSET, advance(text));
        assertEquals(0, HudRadarService.step(0, -5));
        assertEquals(16, HudRadarService.step(5, 0));
        assertEquals(32, HudRadarService.step(0, 5));
        assertEquals(48, HudRadarService.step(-5, 0));
    }

    @Test void forwardIsUpAndRightIsRight() {
        double[] ahead = HudRadarService.screen(0, 10, 0f), east = HudRadarService.screen(10, 0, 180f);
        assertTrue(ahead[1] < 0 && Math.abs(ahead[0]) < 1e-9, "looking south, south is up");
        assertTrue(east[0] > 0 && Math.abs(east[1]) < 1e-9, "looking north, east is on the right");
    }
}

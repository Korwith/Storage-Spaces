package net.minecraft.src;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * The look of the Storage Spaces screens: vanilla's container style (the grey inventory panel, sunken
 * slots, gui.png buttons) drawn at any size, the creative menu's tabs and scroll knob, and pie charts.
 *
 * Kirmod's Dark Theme (Interface tab, Kirmod 1.30) applies here like on every plugin screen: the
 * surfaces (panel, slots, wells, rows, buttons, tabs, the knob, bars and pie slices) are drawn through
 * KirTheme, which coats them when the theme is on. Items and text are drawn after them and are not
 * darkened; the text colors below are lightened by KirTheme so they read on the darker panel. This
 * replaces Storage Spaces' own Light/Dark theme (Storage Spaces plugin 1.0).
 */
public class StorageUI
{
    public static final int WHITE = 0xFFFFFFFF;
    public static final int BUTTON_TEXT = 0xFFE0E0E0;
    public static final int BUTTON_HOT = 0xFFFFFFA0;
    public static final int ACCENT = 0xFF7FE8DC;

    // Surfaces: vanilla's colors. The drawing methods below (and screens drawing these directly) pass
    // them through KirTheme.surface.
    public static final int PANEL = 0xFFC6C6C6;
    public static final int PANEL_LIGHT = 0xFFFFFFFF;
    public static final int PANEL_SHADE = 0xFF555555;
    public static final int SLOT = 0xFF8B8B8B;
    public static final int SLOT_DARK = 0xFF373737;
    public static final int SLOT_LIGHT = 0xFFFFFFFF;
    /** The sunken box behind lists and the chart. */
    public static final int WELL = 0xFFB8B8B8;
    public static final int ROW_ALT = 0xFFB0B0B0;
    public static final int ROW_HOT = 0xFFA8A8A8;
    /** Laid over item slots behind a message. */
    public static final int OVERLAY = 0xC0C6C6C6;
    /** An item slot holding the located item. */
    public static final int LOCATED = 0xFF5FBF6F;
    public static final int PIE_RIM = 0xFF373737;
    public static final int PIE_LINE = 0xE6D9D9D9;
    public static final int PIE_OTHER = 0x8F8F8F;
    public static final int PIE_EMPTY = 0xE8E8E8;

    // Text: set by refresh(), lightened while the Dark Theme is on.
    /** Headings and values. */
    public static int LABEL;
    /** Captions, hints and secondary text. */
    public static int MUTED;
    /** Placeholder text. */
    public static int FAINT;
    /** Numbers that should stand out (legend percentages). */
    public static int STRONG;
    public static int GOOD;
    public static int BAD;
    private static boolean dark;

    static
    {
        dark = !KirTheme.isDark();
        refresh();
    }

    /** Follows Kirmod's Dark Theme; cheap when it hasn't changed, so screens call it every frame. */
    public static void refresh()
    {
        boolean d = KirTheme.isDark();

        if (d == dark)
        {
            return;
        }

        dark = d;
        LABEL = KirTheme.text(0xFF404040);
        MUTED = KirTheme.text(0xFF505050);
        FAINT = KirTheme.text(0xFF707070);
        STRONG = KirTheme.text(0xFF303030);
        GOOD = KirTheme.text(0xFF1F6F2F);
        BAD = KirTheme.text(0xFF8F3F3F);
    }

    /** Formatting codes written for the light panel (dark gray, dark green, dark red) lightened for the Dark Theme. */
    public static String themed(String s)
    {
        return KirTheme.text(s);
    }

    private static final RenderItem itemRenderer = new RenderItem();
    private static final Gui gui = new Gui();

    /** The inventory's panel: black rim with cut corners, white light top-left, grey shade bottom-right. */
    public static void panel(int x1, int y1, int x2, int y2)
    {
        refresh();
        Gui.drawRect(x1 + 2, y1, x2 - 2, y1 + 1, 0xFF000000);
        Gui.drawRect(x1 + 2, y2 - 1, x2 - 2, y2, 0xFF000000);
        Gui.drawRect(x1, y1 + 2, x1 + 1, y2 - 2, 0xFF000000);
        Gui.drawRect(x2 - 1, y1 + 2, x2, y2 - 2, 0xFF000000);
        Gui.drawRect(x1 + 1, y1 + 1, x1 + 2, y1 + 2, 0xFF000000);
        Gui.drawRect(x2 - 2, y1 + 1, x2 - 1, y1 + 2, 0xFF000000);
        Gui.drawRect(x1 + 1, y2 - 2, x1 + 2, y2 - 1, 0xFF000000);
        Gui.drawRect(x2 - 2, y2 - 2, x2 - 1, y2 - 1, 0xFF000000);
        Gui.drawRect(x1 + 2, y1 + 1, x2 - 2, y2 - 1, KirTheme.surface(PANEL));
        Gui.drawRect(x1 + 1, y1 + 2, x2 - 1, y2 - 2, KirTheme.surface(PANEL));
        Gui.drawRect(x1 + 2, y1 + 1, x2 - 3, y1 + 3, KirTheme.surface(PANEL_LIGHT));
        Gui.drawRect(x1 + 1, y1 + 2, x1 + 3, y2 - 3, KirTheme.surface(PANEL_LIGHT));
        Gui.drawRect(x1 + 3, y2 - 3, x2 - 2, y2 - 1, KirTheme.surface(PANEL_SHADE));
        Gui.drawRect(x2 - 3, y1 + 3, x2 - 1, y2 - 2, KirTheme.surface(PANEL_SHADE));
    }

    /** A sunken box, like an inventory slot. */
    public static void slot(int x1, int y1, int x2, int y2, int fill)
    {
        Gui.drawRect(x1, y1, x2, y2, KirTheme.surface(fill));
        Gui.drawRect(x1, y1, x2 - 1, y1 + 1, KirTheme.surface(SLOT_DARK));
        Gui.drawRect(x1, y1, x1 + 1, y2 - 1, KirTheme.surface(SLOT_DARK));
        Gui.drawRect(x1 + 1, y2 - 1, x2, y2, KirTheme.surface(SLOT_LIGHT));
        Gui.drawRect(x2 - 1, y1 + 1, x2, y2, KirTheme.surface(SLOT_LIGHT));
    }

    /**
     * A vanilla button from gui.png at any size: the texture's four corners are stitched together.
     * state 0 is disabled, 1 normal, 2 hovered (vanilla's getHoverState).
     */
    public static void button(Minecraft mc, int x, int y, int w, int h, int state)
    {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, mc.renderEngine.getTexture("/gui/gui.png"));
        KirTheme.textureColor();
        int v = 46 + state * 20;
        int lw = w / 2;
        int rw = w - lw;
        int th = h / 2;
        int bh = h - th;
        gui.drawTexturedModalRect(x, y, 0, v, lw, th);
        gui.drawTexturedModalRect(x + lw, y, 200 - rw, v, rw, th);
        gui.drawTexturedModalRect(x, y + th, 0, v + 20 - bh, lw, bh);
        gui.drawTexturedModalRect(x + lw, y + th, 200 - rw, v + 20 - bh, rw, bh);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void buttonText(FontRenderer font, String s, int x, int y, int w, int h, int state)
    {
        int color = state == 0 ? 0xFFA0A0A0 : (state == 2 ? BUTTON_HOT : BUTTON_TEXT);
        s = fit(font, s, w - 4);
        font.drawStringWithShadow(s, x + (w - font.getStringWidth(s)) / 2, y + (h - 8) / 2, color);
    }

    public static void text(FontRenderer font, String s, int x, int y, int color)
    {
        font.drawString(themed(s), x, y, KirTheme.text(color));
    }

    public static void centered(FontRenderer font, String s, int cx, int y, int color)
    {
        font.drawString(themed(s), cx - font.getStringWidth(s) / 2, y, KirTheme.text(color));
    }

    /** Draws text scaled up, for the block count. */
    public static void big(FontRenderer font, String s, int x, int y, float scale, int color)
    {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);
        font.drawStringWithShadow(s, 0, 0, color);
        GL11.glPopMatrix();
    }

    public static boolean inside(int mx, int my, int x, int y, int w, int h)
    {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public static void item(Minecraft mc, ItemStack stack, int x, int y)
    {
        if (stack == null || stack.getItem() == null)
        {
            return;
        }

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableGUIStandardItemLighting();

        try
        {
            itemRenderer.renderItemIntoGUI(mc.fontRenderer, mc.renderEngine, stack, x, y);
        }
        catch (Exception e)
        {
            // A broken modded item shouldn't take the screen down.
        }

        RenderHelper.disableStandardItemLighting();
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    /** Cuts a string to fit a width, ending it with "..". */
    public static String fit(FontRenderer font, String s, int width)
    {
        if (font.getStringWidth(s) <= width)
        {
            return s;
        }

        while (s.length() > 1 && font.getStringWidth(s + "..") > width)
        {
            s = s.substring(0, s.length() - 1);
        }

        return s + "..";
    }
    /**
     * A creative menu tab from allitems.png, standing on the panel's top edge. column 0 is the
     * leftmost (its side runs into the panel's), 5 the rightmost, anything between a middle tab.
     */
    public static void tab(Minecraft mc, int x, int panelTop, int column, boolean selected)
    {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, mc.renderEngine.getTexture("/gui/allitems.png"));
        KirTheme.textureColor();
        GL11.glDisable(GL11.GL_LIGHTING);
        gui.drawTexturedModalRect(x, panelTop - 28, column * 28, selected ? 32 : 0, 28, 32);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** The creative menu's scroll knob, 12 x 15. */
    public static void knob(Minecraft mc, int x, int y, boolean enabled)
    {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, mc.renderEngine.getTexture("/gui/allitems.png"));
        KirTheme.textureColor();
        gui.drawTexturedModalRect(x, y, enabled ? 232 : 244, 0, 12, 15);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** A sunken bar filled to a fraction, green to red as it fills. */
    public static void bar(int x, int y, int w, int h, float fraction)
    {
        slot(x, y, x + w, y + h, SLOT_DARK);
        fraction = Math.max(0.0F, Math.min(1.0F, fraction));
        int fill = (int)((w - 2) * fraction);

        if (fill > 0)
        {
            int color = fraction < 0.6F ? 0xFF3FB34F : (fraction < 0.85F ? 0xFFD9A426 : 0xFFD94040);
            Gui.drawRect(x + 1, y + 1, x + 1 + fill, y + h - 1, KirTheme.surface(color));
            Gui.drawRect(x + 1, y + 1, x + 1 + fill, y + 2, 0x40FFFFFF);
        }
    }

    /**
     * A count in the corner of an item, like a stack size. A longer count ("999", "1.2k", "350k")
     * would run into the next slot's, so it is drawn smaller, on a soft dark tag tucked into
     * the corner, with its k / M in a color of its own so the size reads at a glance. The smaller text
     * keeps a whole number of screen pixels per font pixel (2/3 at GUI scale 3, 1/2 at scale 2), so
     * it stays sharp. At GUI scale 1 the font can't shrink, so the count is drawn in a 3x5 pixel font.
     */
    public static void count(Minecraft mc, FontRenderer font, long n, int x, int y)
    {
        String s = StorageData.compact(n);
        int scale = new ScaledResolution(mc.gameSettings, mc.displayWidth, mc.displayHeight).getScaleFactor();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        // Up to two digits, like a vanilla stack size; anything longer touches the next slot's count.
        if (font.getStringWidth(s) <= 12)
        {
            font.drawStringWithShadow(s, x + 17 - font.getStringWidth(s), y + 9, 0xFFFFFF);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            return;
        }

        if (scale < 2)
        {
            tinyCount(s, x + 16, y + 16);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            return;
        }

        float f = (float)(scale - 1) / (float)scale;
        int split = s.length();

        while (split > 0 && !Character.isDigit(s.charAt(split - 1)))
        {
            split--;
        }

        String digits = s.substring(0, split);
        String unit = s.substring(split);
        int w = font.getStringWidth(s);
        GL11.glPushMatrix();
        // Scaled about the slot's inner corner, so the tag sits on whole screen pixels.
        GL11.glTranslatef((float)(x + 16), (float)(y + 16), 0.0F);
        GL11.glScalef(f, f, 1.0F);
        // A tag with a rounded top-left corner, flush with the slot's bottom-right edge.
        Gui.drawRect(-w - 1, -8, 0, 0, COUNT_TAG);
        Gui.drawRect(-w, -9, 0, -8, COUNT_TAG);
        font.drawStringWithShadow(digits, -w, -8, 0xFFFFFF);
        font.drawStringWithShadow(unit, -w + font.getStringWidth(digits), -8, unit.startsWith("M") ? 0x7FD4FF : 0xFFD24F);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    /** Behind a shrunk item count: dark and see-through, so the item still shows around it. */
    private static final int COUNT_TAG = 0x9A101418;

    /** 3x5 pixel glyphs (5 for M) for counts at GUI scale 1: rows top to bottom, # is lit. */
    private static final String TINY_CHARS = "0123456789.kM";
    private static final String[] TINY = new String[] {
        "###,#.#,#.#,#.#,###", ".#.,##.,.#.,.#.,###", "##.,..#,.#.,#..,###", "##.,..#,.#.,..#,##.", "#.#,#.#,###,..#,..#",
        "###,#..,##.,..#,##.", ".##,#..,###,#.#,###", "###,..#,.#.,.#.,.#.", "###,#.#,###,#.#,###", "###,#.#,###,..#,##.",
        ".,.,.,.,#", "#..,#.#,##.,#.#,#.#", "#...#,##.##,#.#.#,#...#,#...#"};

    /** A count in the 3x5 font on a count tag, its bottom-right corner at (right, bottom). */
    private static void tinyCount(String s, int right, int bottom)
    {
        int w = -1;

        for (int i = 0; i < s.length(); i++)
        {
            int g = TINY_CHARS.indexOf(s.charAt(i));
            w += (g < 0 ? 3 : TINY[g].indexOf(',')) + 1;
        }

        int left = right - w - 1;
        Gui.drawRect(left - 1, bottom - 6, right, bottom, COUNT_TAG);
        Gui.drawRect(left, bottom - 7, right, bottom - 6, COUNT_TAG);
        int cx = left;

        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            int g = TINY_CHARS.indexOf(c);

            if (g < 0)
            {
                cx += 4;
                continue;
            }

            String[] rows = TINY[g].split(",");
            int color = c == 'k' ? 0xFFFFD24F : (c == 'M' ? 0xFF7FD4FF : 0xFFFFFFFF);

            for (int r = 0; r < rows.length; r++)
            {
                for (int k = 0; k < rows[r].length(); k++)
                {
                    if (rows[r].charAt(k) == '#')
                    {
                        Gui.drawRect(cx + k, bottom - 6 + r, cx + k + 1, bottom - 5 + r, color);
                    }
                }
            }

            cx += rows[0].length() + 1;
        }
    }

    /** Slice colours: distinct in hue and lightness, readable with the Dark Theme on or off. */
    public static final int[] PIE_COLORS = new int[] {0x3F7FD9, 0xE0763A, 0x4FB34F, 0xD9C326, 0x9B59D0, 0x2FB8B0, 0xD94F7F, 0x8C6A3F, 0x6F8FAF, 0xB0D94F, 0xD98FD9, 0x5F5F5F};

    /**
     * A pie chart: slices from fractions (summing to 1 or less), starting at 12 o'clock and going
     * clockwise. The hot slice is pulled out a little. A dark rim and lines between slices keep
     * neighbours apart.
     */
    public static void pie(int cx, int cy, int radius, float[] fractions, int[] colors, int hot)
    {
        Tessellator t = Tessellator.instance;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // Shadow
        disc(cx + 2, cy + 2, radius + 1, 0x40000000);
        disc(cx, cy, radius + 1, KirTheme.surface(PIE_RIM));
        double start = 0.0D;

        for (int i = 0; i < fractions.length; i++)
        {
            double sweep = fractions[i] * Math.PI * 2.0D;

            if (sweep <= 0.0D)
            {
                continue;
            }

            double mid = start + sweep / 2.0D;
            double ox = i == hot ? Math.sin(mid) * 3.0D : 0.0D;
            double oy = i == hot ? -Math.cos(mid) * 3.0D : 0.0D;
            int c = KirTheme.surface(0xFF000000 | colors[i]);
            float r = (c >> 16 & 255) / 255.0F;
            float g = (c >> 8 & 255) / 255.0F;
            float b = (c & 255) / 255.0F;

            if (i == hot)
            {
                r = Math.min(1.0F, r * 1.2F + 0.08F);
                g = Math.min(1.0F, g * 1.2F + 0.08F);
                b = Math.min(1.0F, b * 1.2F + 0.08F);
            }

            int steps = Math.max(2, (int)(sweep * radius / 3.0D));
            t.startDrawing(GL11.GL_TRIANGLE_FAN);
            t.setColorRGBA_F(r, g, b, 1.0F);
            t.addVertex(cx + ox, cy + oy, 0.0D);
            // The rim a little darker, for depth.
            t.setColorRGBA_F(r * 0.82F, g * 0.82F, b * 0.82F, 1.0F);

            for (int k = steps; k >= 0; k--)
            {
                double a = start + sweep * k / steps;
                t.addVertex(cx + ox + Math.sin(a) * radius, cy + oy - Math.cos(a) * radius, 0.0D);
            }

            t.draw();
            start += sweep;
        }

        if (fractions.length > 1)
        {
            GL11.glLineWidth(1.0F);
            t.startDrawing(GL11.GL_LINES);
            int line = KirTheme.surface(PIE_LINE);
            t.setColorRGBA(line >> 16 & 255, line >> 8 & 255, line & 255, line >>> 24);
            start = 0.0D;

            for (int i = 0; i < fractions.length; i++)
            {
                if (fractions[i] <= 0.0F || fractions[i] >= 0.999F)
                {
                    start += fractions[i] * Math.PI * 2.0D;
                    continue;
                }

                t.addVertex(cx, cy, 0.0D);
                t.addVertex(cx + Math.sin(start) * radius, cy - Math.cos(start) * radius, 0.0D);
                start += fractions[i] * Math.PI * 2.0D;
            }

            t.draw();
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void disc(int cx, int cy, int radius, int argb)
    {
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_TRIANGLE_FAN);
        t.setColorRGBA((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, argb >>> 24);
        t.addVertex(cx, cy, 0.0D);

        for (int k = 64; k >= 0; k--)
        {
            double a = Math.PI * 2.0D * k / 64.0D;
            t.addVertex(cx + Math.sin(a) * radius, cy - Math.cos(a) * radius, 0.0D);
        }

        t.draw();
    }

    /** Which slice (or -1) a point is over. */
    public static int sliceAt(int mx, int my, int cx, int cy, int radius, float[] fractions)
    {
        double dx = mx - cx;
        double dy = my - cy;

        if (dx * dx + dy * dy > (radius + 3) * (radius + 3))
        {
            return -1;
        }

        double a = Math.atan2(dx, -dy);

        if (a < 0.0D)
        {
            a += Math.PI * 2.0D;
        }

        double f = a / (Math.PI * 2.0D);
        double sum = 0.0D;

        for (int i = 0; i < fractions.length; i++)
        {
            sum += fractions[i];

            if (f < sum)
            {
                return i;
            }
        }

        return -1;
    }

    /** A vanilla GuiButton drawn with the theme's buttons, for screens built from controlList. */
    public static class ThemedButton extends GuiButton
    {
        public ThemedButton(int id, int x, int y, int w, int h, String label)
        {
            super(id, x, y, w, h, label);
        }

        public void drawButton(Minecraft mc, int mx, int my)
        {
            if (!drawButton)
            {
                return;
            }

            boolean over = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            int state = getHoverState(over);
            StorageUI.button(mc, xPosition, yPosition, width, height, state);
            StorageUI.buttonText(mc.fontRenderer, displayString, xPosition, yPosition, width, height, state);
        }
    }
}

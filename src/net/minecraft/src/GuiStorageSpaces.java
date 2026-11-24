package net.minecraft.src;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The Storage Spaces screen (I), in the creative menu's style: a tab per space along the top, with
 * Total first, + to select a new space, and Settings at the right end. A space shows its items
 * (added up over every container in it), its containers, a pie chart and its info; Total does the
 * same over all spaces and charts how storage is shared between them.
 */
public class GuiStorageSpaces extends GuiScreen
{
    public static final int TAB_TOTAL = -1;
    public static final int TAB_SETTINGS = -2;
    private static final int SLOT = 18;
    private static final int TAB_W = 29;
    private static final int ROW_H = 20;
    private static final long CLOSE_GUARD = 300L;
    private static final String[] SPACE_VIEWS = new String[] {"Items", "Chests", "Chart", "Info"};
    private static final String[] TOTAL_VIEWS = new String[] {"Items", "Spaces", "Chart", "Chests"};
    private static final String[] CHEST_SORTS = new String[] {"Items", "Fill", "Distance"};

    /** The tab open when the screen was last closed. */
    static int lastTab = TAB_TOTAL;
    static long closedAt;
    private static int spaceView;
    private static int totalView;
    private static int chestSort;

    private final long openedAt = System.currentTimeMillis();
    private int tab;
    private int page;

    private int px;
    private int py;
    private int pw;
    private int ph;
    private int cx;
    private int cy;
    private int cw;
    private int ch;
    private int scroll;
    private boolean draggingBar;
    private GuiTextField search;
    private GuiTextField rename;
    private final List controls = new ArrayList();
    private Control hovered;
    private long hoverStart;
    private Control dragging;
    private String status;
    private long statusTime;
    private boolean confirmDelete;
    private Control deleteButton;

    /** What the content shows: item keys or records, rebuilt when the stats or filters change. */
    private List shown = new ArrayList();
    private Object shownStats;
    private String shownFilter;
    private int shownSort = -1;
    private int shownView = -1;

    // The pie chart as last drawn.
    private float[] pieFractions = new float[0];
    private int[] pieColors = new int[0];
    private Object[] pieWhat = new Object[0];
    private String[] pieNames = new String[0];
    private long[] pieValues = new long[0];
    private int pieX;
    private int pieY;
    private int pieR;
    private int legendX;
    private int legendY;
    private int legendW;

    public GuiStorageSpaces(int tab)
    {
        this.tab = tab;
    }

    public void initGui()
    {
        Keyboard.enableRepeatEvents(true);

        if (tab >= 0 && StorageData.getSpace(tab) == null)
        {
            tab = TAB_TOTAL;
        }

        pw = Math.min(width - 16, 360);
        ph = Math.min(height - 44, 240);
        px = (width - pw) / 2;
        py = (height - ph) / 2 + 13;
        cx = px + 8;
        cy = py + 48;
        cw = pw - 16;
        ch = ph - 48 - 16;
        String old = search != null ? search.getText() : "";
        search = new GuiTextField(fontRenderer, px + 8, py + 32, 110, 12);
        search.setMaxStringLength(32);
        search.setText(old);
        int tabsPerPage = tabsPerPage();
        int index = tabIndex(tab);

        if (index >= 0)
        {
            page = index / tabsPerPage;
        }

        rebuild();
    }

    public void onGuiClosed()
    {
        Keyboard.enableRepeatEvents(false);
        commitRename();
        StorageData.saveWorld();
        StorageData.saveSettings();
        lastTab = tab;
        closedAt = System.currentTimeMillis();
    }

    public boolean doesGuiPauseGame()
    {
        // Singleplayer keeps ticking so the server-side scan stays live.
        return false;
    }

    private StorageData.Space space()
    {
        return tab >= 0 ? StorageData.getSpace(tab) : null;
    }

    private int view()
    {
        return tab == TAB_TOTAL ? totalView : spaceView;
    }

    private String viewName()
    {
        return tab == TAB_SETTINGS ? "Settings" : (tab == TAB_TOTAL ? TOTAL_VIEWS[totalView] : SPACE_VIEWS[spaceView]);
    }

    private void setStatus(String s)
    {
        status = s;
        statusTime = System.currentTimeMillis();
    }

    // ---------------------------------------------------------------- tabs

    /** Tabs in order: Total, each space, then "+". Ids: -1, space id, -3. */
    private List tabIds()
    {
        List ids = new ArrayList();
        ids.add(Integer.valueOf(TAB_TOTAL));

        for (int i = 0; i < StorageData.spaces.size(); i++)
        {
            ids.add(Integer.valueOf(((StorageData.Space)StorageData.spaces.get(i)).id));
        }

        ids.add(Integer.valueOf(-3));
        return ids;
    }

    private int tabsPerPage()
    {
        return Math.max(2, (pw - 28 - 4) / TAB_W);
    }

    private int pages()
    {
        return (tabIds().size() + tabsPerPage() - 1) / tabsPerPage();
    }

    private int tabIndex(int id)
    {
        return tabIds().indexOf(Integer.valueOf(id));
    }

    /** {x, id} of the tabs on this page, and the settings tab. */
    private List visibleTabs()
    {
        List ids = tabIds();
        int per = tabsPerPage();
        page = Math.max(0, Math.min(page, pages() - 1));
        List out = new ArrayList();

        for (int i = page * per; i < Math.min(ids.size(), (page + 1) * per); i++)
        {
            out.add(new int[] {px + (i - page * per) * TAB_W, ((Integer)ids.get(i)).intValue(), i - page * per == 0 ? 0 : 1});
        }

        out.add(new int[] {px + pw - 28, TAB_SETTINGS, 5});
        return out;
    }

    private int tabAt(int mx, int my)
    {
        if (my < py - 28 || my >= py)
        {
            return Integer.MIN_VALUE;
        }

        List tabs = visibleTabs();

        for (int i = 0; i < tabs.size(); i++)
        {
            int[] t = (int[])tabs.get(i);

            if (mx >= t[0] && mx < t[0] + 28)
            {
                return t[1];
            }
        }

        return Integer.MIN_VALUE;
    }

    private void openTab(int id)
    {
        if (id == -3)
        {
            mc.displayGuiScreen(null);
            StorageSelection.start(mc, null);
            return;
        }

        commitRename();
        tab = id;
        scroll = 0;
        confirmDelete = false;
        rebuild();
    }

    private void drawTab(int[] t, int mx, int my)
    {
        boolean selected = t[1] == tab;
        StorageUI.tab(mc, t[0], py, t[2], selected);
        int ix = t[0] + 6;
        int iy = py - 28 + 9 + (selected ? 0 : 1);

        if (t[1] == -3)
        {
            StorageUI.big(fontRenderer, "+", t[0] + 9, py - 21, 2.0F, 0xFFFFFF);
            return;
        }

        StorageUI.item(mc, tabIcon(t[1]), ix, iy);
    }

    private ItemStack tabIcon(int id)
    {
        if (id == TAB_TOTAL)
        {
            return new ItemStack(Block.chest);
        }

        if (id == TAB_SETTINGS)
        {
            return new ItemStack(Item.redstoneRepeater);
        }

        StorageData.Space s = StorageData.getSpace(id);
        int key = s == null ? -1 : (s.icon != -1 ? s.icon : StorageSpacesMod.cachedStats(s).topKey());
        ItemStack stack = key != -1 ? StorageData.stack(key) : null;
        return stack != null ? stack : new ItemStack(Block.chest);
    }

    private String tabTip(int id)
    {
        if (id == TAB_TOTAL)
        {
            StorageData.Stats st = StorageSpacesMod.cachedStats(null);
            return "All Storage\n" + StorageData.exact(st.total) + " items in " + StorageData.spaces.size() + " space" + (StorageData.spaces.size() == 1 ? "" : "s");
        }

        if (id == TAB_SETTINGS)
        {
            return "Settings";
        }

        if (id == -3)
        {
            return "New Storage Space\nSelect an area in the world\n(key: " + Keyboard.getKeyName(StorageSpacesMod.KEY_SELECT.keyCode) + ")";
        }

        StorageData.Space s = StorageData.getSpace(id);

        if (s == null)
        {
            return null;
        }

        StorageData.Stats st = StorageSpacesMod.cachedStats(s);
        return s.name + "\n" + StorageData.exact(st.total) + " items, " + st.records.size() + " containers\n" + StorageData.dimName(s.dim);
    }

    // ---------------------------------------------------------------- controls

    abstract class Control
    {
        int x;
        int y;
        int w;
        int h;
        String tip;

        Control(int x, int y, int w, int h, String tip)
        {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.tip = tip;
        }

        abstract void draw(int mx, int my);

        abstract void click(int mx, int my, int button);

        void drag(int mx, int my) {}

        boolean wheel(int dir)
        {
            return false;
        }

        boolean over(int mx, int my)
        {
            return StorageUI.inside(mx, my, x, y, w, h);
        }
    }

    abstract class Button extends Control
    {
        Button(int x, int y, int w, int h, String tip)
        {
            super(x, y, w, h, tip);
        }

        abstract String label();

        boolean enabled()
        {
            return true;
        }

        void draw(int mx, int my)
        {
            int state = !enabled() ? 0 : (over(mx, my) ? 2 : 1);
            StorageUI.button(mc, x, y, w, h, state);
            StorageUI.buttonText(fontRenderer, label(), x, y, w, h, state);
        }

        void click(int mx, int my, int button)
        {
            if (enabled())
            {
                press(button);
            }
        }

        abstract void press(int button);
    }

    /** A row of buttons, one pressed in: picks one of a few options. */
    abstract class Segment extends Control
    {
        final String[] names;

        Segment(int x, int y, int w, int h, String[] names, String tip)
        {
            super(x, y, w, h, tip);
            this.names = names;
        }

        abstract int get();

        abstract void set(int v);

        int left(int i)
        {
            return x + w * i / names.length;
        }

        void draw(int mx, int my)
        {
            for (int i = 0; i < names.length; i++)
            {
                int bx = left(i);
                int bw = left(i + 1) - bx;
                boolean on = get() == i;
                int state = on ? 0 : (StorageUI.inside(mx, my, bx, y, bw, h) ? 2 : 1);
                StorageUI.button(mc, bx, y, bw, h, state);
                String s = StorageUI.fit(fontRenderer, names[i], bw - 4);
                fontRenderer.drawStringWithShadow(s, bx + (bw - fontRenderer.getStringWidth(s)) / 2, y + (h - 8) / 2, on ? 0xFFFFFFA0 : (state == 2 ? 0xFFFFFFA0 : 0xFFE0E0E0));

                if (on)
                {
                    Gui.drawRect(bx + 2, y + h - 2, bx + bw - 2, y + h - 1, StorageUI.ACCENT);
                }
            }
        }

        void click(int mx, int my, int button)
        {
            for (int i = 0; i < names.length; i++)
            {
                if (mx >= left(i) && mx < left(i + 1))
                {
                    set(i);
                    return;
                }
            }
        }

        boolean wheel(int dir)
        {
            set(Math.max(0, Math.min(names.length - 1, get() - dir)));
            return true;
        }
    }

    /** Vanilla's option slider. */
    abstract class Slider extends Control
    {
        final String label;
        final int min;
        final int max;

        Slider(int x, int y, int w, String label, int min, int max, String tip)
        {
            super(x, y, w, 14, tip);
            this.label = label;
            this.min = min;
            this.max = max;
        }

        abstract int get();

        abstract void set(int v);

        String format(int v)
        {
            return String.valueOf(v);
        }

        void draw(int mx, int my)
        {
            StorageUI.button(mc, x, y, w, h, 0);
            boolean hot = dragging == this || over(mx, my);
            int kx = x + (w - 8) * (get() - min) / Math.max(1, max - min);
            StorageUI.button(mc, kx, y, 8, h, hot ? 2 : 1);
            StorageUI.buttonText(fontRenderer, label + ": " + format(get()), x, y, w, h, hot ? 2 : 1);
        }

        void click(int mx, int my, int button)
        {
            dragging = this;
            drag(mx, my);
        }

        void drag(int mx, int my)
        {
            int v = min + Math.round((mx - x - 4) * (max - min) / (float)Math.max(1, w - 8));
            set(Math.max(min, Math.min(max, v)));
        }

        boolean wheel(int dir)
        {
            set(Math.max(min, Math.min(max, get() + dir)));
            return true;
        }
    }

    /** Lays out the controls of the open tab and view. */
    private void rebuild()
    {
        controls.clear();
        dragging = null;
        rename = null;
        shownStats = null;

        if (tab == TAB_SETTINGS)
        {
            buildSettings();
            return;
        }

        final String[] views = tab == TAB_TOTAL ? TOTAL_VIEWS : SPACE_VIEWS;
        int vw = Math.min(200, pw - 140);
        controls.add(new Segment(px + pw - 8 - vw, py + 5, vw, 12, views, null)
        {
            int get() { return view(); }
            void set(int v)
            {
                if (tab == TAB_TOTAL) totalView = v; else spaceView = v;
                scroll = 0;
                rebuild();
            }
        });
        String view = viewName();

        if (view.equals("Items"))
        {
            int sw = Math.min(176, pw - 130);
            controls.add(new Segment(px + pw - 8 - sw, py + 32, sw, 12, StorageData.SORT_NAMES, "Sort\nCount: most first. Change: most\ngained since the baseline first.")
            {
                int get() { return StorageData.sort; }
                void set(int v) { StorageData.sort = v; scroll = 0; }
            });
        }
        else if (view.equals("Chests"))
        {
            int sw = Math.min(150, pw - 130);
            controls.add(new Segment(px + pw - 8 - sw, py + 32, sw, 12, CHEST_SORTS, "Sort\nItems: fullest first. Fill: by\nslots used. Distance: nearest first.")
            {
                int get() { return chestSort; }
                void set(int v) { chestSort = v; scroll = 0; }
            });
        }
        else if (view.equals("Chart"))
        {
            buildChartControls();
        }
        else if (view.equals("Info"))
        {
            buildInfo();
        }
    }

    private void buildChartControls()
    {
        int y = py + 32;

        if (tab == TAB_TOTAL)
        {
            controls.add(new Segment(px + 8, y, 120, 12, new String[] {"By Item", "By Space"}, "Slices\nBy Space: each space's share.\nBy Item: what is stored, over all spaces.")
            {
                int get() { return StorageData.chartBy; }
                void set(int v) { StorageData.chartBy = v; }
            });
        }

        controls.add(new Segment(px + pw - 8 - 130, y, 130, 12, new String[] {"Items", "Slots"}, "Measure\nItems: item counts.\nSlots: stacks, the room things take\n(empty slots as a slice when Show\nEmpty Space is on in Settings).")
        {
            int get() { return StorageData.chartMeasure; }
            void set(int v) { StorageData.chartMeasure = v; }
        });
    }

    private void buildInfo()
    {
        final StorageData.Space s = space();

        if (s == null)
        {
            return;
        }

        rename = new GuiTextField(fontRenderer, px + 36, py + 18, Math.min(140, pw - 230), 12);
        rename.setMaxStringLength(40);
        rename.setText(s.name);
        int bw = (cw - 8) / 3;
        int bx1 = cx;
        int bx2 = cx + bw + 4;
        int bx3 = cx + (bw + 4) * 2;
        int by = py + 88;
        int bh = 16;
        int gap = 18;
        controls.add(new Button(bx1, by, bw, bh, "Select the area again\nThe old box shows until it is saved.")
        {
            String label() { return "Reselect Area"; }
            void press(int b) { mc.displayGuiScreen(null); StorageSelection.start(mc, s); }
        });
        controls.add(new Button(bx2, by, bw, bh, "Outline\nThe space's box and name in the world\n(Settings > Outlines says when).")
        {
            String label() { return "Outline " + (s.outline ? "On" : "Off"); }
            void press(int b) { s.outline = !s.outline; StorageData.changed(); }
        });
        controls.add(new Button(bx3, by, bw, bh, "Color\nThe outline's color. Right-click goes back.")
        {
            String label() { return "Color"; }
            void press(int b)
            {
                int i = 0;

                for (int k = 0; k < StorageData.COLORS.length; k++)
                {
                    if (StorageData.COLORS[k] == s.color) i = k;
                }

                s.color = StorageData.COLORS[(i + (b == 1 ? StorageData.COLORS.length - 1 : 1)) % StorageData.COLORS.length];
                StorageData.changed();
            }
            void draw(int mx, int my)
            {
                super.draw(mx, my);
                StorageUI.slot(x + w - 16, y + 3, x + w - 4, y + h - 3, 0xFF000000 | s.color);
            }
        });
        controls.add(new Button(bx1, by + gap, bw, bh, "Tab icon\nClick with an item in hand to use it,\nwith an empty hand for the most\ncommon item (Auto).")
        {
            String label() { return "Icon: " + (s.icon == -1 ? "Auto" : StorageData.itemName(s.icon)); }
            void press(int b)
            {
                ItemStack held = mc.thePlayer.inventory.getCurrentItem();
                s.icon = held != null ? StorageData.itemKey(held) : -1;
                StorageData.changed();
            }
        });
        controls.add(new Button(bx2, by + gap, bw, bh, "Baseline\nRemembers today's counts, so the\nItems view and tooltips show what\nwas added or taken since.")
        {
            String label() { return "Set Baseline"; }
            void press(int b) { StorageData.setBaseline(s); setStatus("Baseline set: changes are counted from now"); }
        });
        controls.add(new Button(bx3, by + gap, bw, bh, null)
        {
            String label() { return "Clear Baseline"; }
            boolean enabled() { return s.baseline != null; }
            void press(int b) { s.baseline = null; s.baselineTime = 0L; StorageData.changed(); setStatus("Baseline cleared"); }
        });
        controls.add(new Button(bx1, by + gap * 2, bw, bh, "Export\nWrites every item, its count, stacks\nand containers to a CSV file in\n.minecraft/storagespaces/.")
        {
            String label() { return "Export CSV"; }
            void press(int b) { export(s); }
        });
        controls.add(new Button(bx2, by + gap * 2, bw, bh, "Copy\nCopies a text summary (top items)\nto the clipboard.")
        {
            String label() { return "Copy Summary"; }
            void press(int b) { copySummary(s); }
        });
        controls.add(new Button(bx3, by + gap * 2, bw / 2 - 2, bh, "Move this tab left")
        {
            String label() { return "<"; }
            boolean enabled() { return StorageData.spaces.indexOf(s) > 0; }
            void press(int b) { moveSpace(s, -1); }
        });
        controls.add(new Button(bx3 + bw / 2 + 2, by + gap * 2, bw - bw / 2 - 2, bh, "Move this tab right")
        {
            String label() { return ">"; }
            boolean enabled() { return StorageData.spaces.indexOf(s) < StorageData.spaces.size() - 1; }
            void press(int b) { moveSpace(s, 1); }
        });
        controls.add(deleteButton = new Button(bx1, by + gap * 3, bw, bh, "Delete\nForgets this space (not the chests).\nClick twice.")
        {
            String label() { return confirmDelete ? "\u00a7cClick again to delete" : "Delete Space"; }
            void press(int b)
            {
                if (!confirmDelete)
                {
                    confirmDelete = true;
                    return;
                }

                StorageData.removeSpace(s);
                StorageSpacesMod.spacesChanged();
                setStatus("Deleted " + s.name);
                openTab(TAB_TOTAL);
            }
        });
    }

    private void buildSettings()
    {
        int colW = (cw - 8) / 2;
        int x1 = cx;
        int x2 = cx + colW + 8;
        int w3 = (cw - 8) / 3;
        int y = py + 20;
        controls.add(new Segment(x1, y + 10, colW, 13, StorageData.CONTAINER_NAMES, "Containers counted\nChests: chests only.\n+ Dispensers: also dispensers.\nAll: furnaces, brewing stands and\nmodded containers too.")
        {
            int get() { return StorageData.containers; }
            void set(int v) { StorageData.containers = v; }
        });
        controls.add(new Segment(x2, y + 10, colW, 13, StorageData.NUMBER_NAMES, "Numbers\nExact: 12,345. Compact: 12k.\nStacks: 192 st + 57.")
        {
            int get() { return StorageData.numbers; }
            void set(int v) { StorageData.numbers = v; }
        });
        y += 27;
        controls.add(new Segment(x1, y + 10, colW, 13, StorageData.OUTLINE_NAMES, "Outlines in the world\nSelecting: only while selecting an\narea or locating an item.")
        {
            int get() { return StorageData.outlines; }
            void set(int v) { StorageData.outlines = v; }
        });
        controls.add(new Segment(x2, y + 10, colW, 13, StorageData.SORT_NAMES, "Item order\nThe Items view's sort.")
        {
            int get() { return StorageData.sort; }
            void set(int v) { StorageData.sort = v; }
        });
        y += 29;
        controls.add(toggle(x1, y, colW, "Name Labels", "Name labels\nEach space's name and item count\nfloat above it.", 0));
        controls.add(toggle(x2, y, colW, "See-Through Outlines", "See-through outlines\nSpace outlines show faintly through\nblocks.", 1));
        y += 17;
        controls.add(toggle(x1, y, colW, "Locate Closes Menu", "Locate closes the menu\nClicking an item closes this screen to\nshow its containers. Right-click\nalways keeps it open.", 2));
        controls.add(toggle(x2, y, colW, "Locate Readout", "Locate readout\nAbove the hotbar: the located item,\nits total and the nearest container.", 3));
        y += 17;
        controls.add(toggle(x1, y, colW, "Show Changes", "Show changes\nGreen and red marks on items that went\nup or down since the space's baseline.", 4));
        controls.add(toggle(x2, y, colW, "Selection Help", "Selection help\nThe panel at the top of the screen\nwhile selecting.", 5));
        y += 17;
        controls.add(toggle(x1, y, colW, "Show Empty Space", "Show empty space\nPie charts measured in Slots get an\n'Empty slots' slice, showing how full\nstorage is. Off: only what is stored.", 6));
        controls.add(new Slider(x2, y, colW, "Nudge Step", 1, 16, "Nudge step\nBlocks the selection grow, shrink and\nmove keys (and Alt + wheel) go per\npress. Shift: 5.")
        {
            int get() { return StorageData.nudgeStep; }
            void set(int v) { StorageData.nudgeStep = v; }
        });
        y += 20;
        controls.add(new Slider(x1, y, w3, "Refresh", 1, 30, "Refresh\nSeconds between server-side scans of\nloaded chests (singleplayer). Unloaded\nchunks are read from the save every 20 s.")
        {
            int get() { return StorageData.refresh; }
            void set(int v) { StorageData.refresh = v; }
            String format(int v) { return v + " s"; }
        });
        controls.add(new Slider(x1 + w3 + 4, y, w3, "Locate", 0, 300, "Locate for\nHow long located containers stay\nhighlighted. 0: until stopped.")
        {
            int get() { return StorageData.locateSeconds; }
            void set(int v) { StorageData.locateSeconds = v - v % 5; }
            String format(int v) { return v == 0 ? "Always" : v + " s"; }
        });
        controls.add(new Slider(x1 + (w3 + 4) * 2, y, w3, "Reach", 5, 128, "Selection reach\nHow far away a corner can be clicked\nin selection mode, in blocks.")
        {
            int get() { return StorageData.reach; }
            void set(int v) { StorageData.reach = v; }
        });
        y += 17;
        controls.add(new Button(x1, y, w3, 14, "Clear located item\nStops highlighting containers.")
        {
            String label() { return "Stop Locating"; }
            boolean enabled() { return StorageSpacesMod.locateKey != -1; }
            void press(int b) { StorageSpacesMod.clearLocate(); setStatus("Locating stopped"); }
        });
        controls.add(new Button(x1 + w3 + 4, y, w3, 14, "Rescan now\nScans loaded chests and reads the\nsave again right away.")
        {
            String label() { return "Rescan Now"; }
            void press(int b) { StorageScanner.poke(); setStatus("Rescanning"); }
        });
        controls.add(new Button(x1 + (w3 + 4) * 2, y, w3, 14, "Export all\nEvery item over all spaces, as CSV\nin .minecraft/storagespaces/.")
        {
            String label() { return "Export All"; }
            void press(int b) { export(null); }
        });
    }

    private Control toggle(int x, int y, int w, final String label, String tip, final int which)
    {
        return new Button(x, y, w, 14, tip)
        {
            String label() { return label + ": " + (get() ? "On" : "Off"); }
            boolean get()
            {
                switch (which)
                {
                    case 0: return StorageData.labels;
                    case 1: return StorageData.seeThrough;
                    case 2: return StorageData.locateCloses;
                    case 3: return StorageData.locateHud;
                    case 4: return StorageData.deltas;
                    case 5: return StorageData.selectHud;
                    default: return StorageData.showEmpty;
                }
            }
            void press(int b)
            {
                boolean v = !get();

                switch (which)
                {
                    case 0: StorageData.labels = v; break;
                    case 1: StorageData.seeThrough = v; break;
                    case 2: StorageData.locateCloses = v; break;
                    case 3: StorageData.locateHud = v; break;
                    case 4: StorageData.deltas = v; break;
                    case 5: StorageData.selectHud = v; break;
                    default: StorageData.showEmpty = v;
                }
            }
        };
    }

    private void moveSpace(StorageData.Space s, int dir)
    {
        int i = StorageData.spaces.indexOf(s);
        int j = i + dir;

        if (i < 0 || j < 0 || j >= StorageData.spaces.size())
        {
            return;
        }

        Collections.swap(StorageData.spaces, i, j);
        StorageData.changed();
        page = tabIndex(tab) / tabsPerPage();
    }

    private void commitRename()
    {
        StorageData.Space s = space();

        if (rename != null && s != null)
        {
            String n = rename.getText().trim();

            if (n.length() > 0 && !n.equals(s.name))
            {
                s.name = n;
                StorageData.changed();
                setStatus("Renamed to " + n);
            }
        }
    }

    private void export(StorageData.Space s)
    {
        try
        {
            File f = StorageData.exportCsv(s, StorageSpacesMod.cachedStats(s));
            setStatus("Saved storagespaces/" + f.getName());
        }
        catch (Exception e)
        {
            setStatus("Export failed: " + e.getMessage());
        }
    }

    private void copySummary(StorageData.Space s)
    {
        StorageData.Stats st = StorageSpacesMod.cachedStats(s);
        StringBuilder b = new StringBuilder();
        b.append(s != null ? s.name : "All Storage").append(": ").append(StorageData.exact(st.total)).append(" items, ").append(st.items.size()).append(" kinds, ").append(st.records.size()).append(" containers, ").append(st.used).append("/").append(st.slots).append(" slots used\n");
        List keys = sortedKeys(st, 0, "");

        for (int i = 0; i < Math.min(20, keys.size()); i++)
        {
            int k = ((Integer)keys.get(i)).intValue();
            b.append(StorageData.itemName(k)).append(": ").append(StorageData.exact(st.count(k))).append('\n');
        }

        setClipboardString(b.toString());
        setStatus("Summary copied to the clipboard");
    }

    // ---------------------------------------------------------------- content lists

    private List sortedKeys(final StorageData.Stats st, final int sort, String filter)
    {
        List keys = new ArrayList();
        Iterator it = st.items.keySet().iterator();

        while (it.hasNext())
        {
            Integer k = (Integer)it.next();

            if (matches(k.intValue(), filter))
            {
                keys.add(k);
            }
        }

        final StorageData.Space s = space();
        Collections.sort(keys, new Comparator()
        {
            public int compare(Object a, Object b)
            {
                int ka = ((Integer)a).intValue();
                int kb = ((Integer)b).intValue();
                int c;

                if (sort == 1)
                {
                    c = StorageData.itemName(ka).compareToIgnoreCase(StorageData.itemName(kb));
                }
                else if (sort == 2)
                {
                    c = ka < kb ? -1 : (ka > kb ? 1 : 0);
                }
                else if (sort == 3)
                {
                    long da = StorageData.delta(s, st, ka);
                    long db = StorageData.delta(s, st, kb);
                    c = da > db ? -1 : (da < db ? 1 : 0);
                }
                else
                {
                    long na = st.count(ka);
                    long nb = st.count(kb);
                    c = na > nb ? -1 : (na < nb ? 1 : 0);
                }

                return c != 0 ? c : (ka < kb ? -1 : (ka > kb ? 1 : 0));
            }
        });
        return keys;
    }

    private static boolean matches(int key, String filter)
    {
        if (filter.length() == 0)
        {
            return true;
        }

        return StorageData.itemName(key).toLowerCase().indexOf(filter) >= 0 || StorageData.idString(key).equals(filter);
    }

    /** Item keys matching the search, for the Chests view. */
    private List matchingKeys(StorageData.Stats st, String filter)
    {
        return filter.length() == 0 ? null : sortedKeys(st, 0, filter);
    }

    private static long matchingCount(StorageData.Record r, List keys)
    {
        long n = 0L;

        for (int i = 0; i < keys.size(); i++)
        {
            n += r.count(((Integer)keys.get(i)).intValue());
        }

        return n;
    }

    /** Rebuilds the shown list when the stats, the search, the sort or the view changed. */
    private void refreshShown(StorageData.Stats st)
    {
        String filter = search.getText().trim().toLowerCase();
        int sort = viewName().equals("Chests") ? 10 + chestSort : StorageData.sort;

        if (shownStats == st && filter.equals(shownFilter) && sort == shownSort && view() == shownView)
        {
            return;
        }

        shownStats = st;
        shownFilter = filter;
        shownSort = sort;
        shownView = view();
        String view = viewName();

        if (view.equals("Items"))
        {
            shown = sortedKeys(st, StorageData.sort, filter);
        }
        else if (view.equals("Chests"))
        {
            final List keys = matchingKeys(st, filter);
            List recs = new ArrayList();

            for (int i = 0; i < st.records.size(); i++)
            {
                StorageData.Record r = (StorageData.Record)st.records.get(i);

                if (keys == null || matchingCount(r, keys) > 0)
                {
                    recs.add(r);
                }
            }

            final double px0 = mc.thePlayer.posX;
            final double pz0 = mc.thePlayer.posZ;
            final double py0 = mc.thePlayer.posY;
            Collections.sort(recs, new Comparator()
            {
                public int compare(Object a, Object b)
                {
                    StorageData.Record ra = (StorageData.Record)a;
                    StorageData.Record rb = (StorageData.Record)b;
                    double va;
                    double vb;

                    if (chestSort == 1)
                    {
                        va = -ra.used / (double)Math.max(1, ra.slots);
                        vb = -rb.used / (double)Math.max(1, rb.slots);
                    }
                    else if (chestSort == 2)
                    {
                        va = sq(ra.x - px0) + sq(ra.y - py0) + sq(ra.z - pz0);
                        vb = sq(rb.x - px0) + sq(rb.y - py0) + sq(rb.z - pz0);
                    }
                    else
                    {
                        va = -(keys != null ? matchingCount(ra, keys) : ra.total());
                        vb = -(keys != null ? matchingCount(rb, keys) : rb.total());
                    }

                    return va < vb ? -1 : (va > vb ? 1 : ra.key().compareTo(rb.key()));
                }
            });
            shown = recs;
        }
        else if (view.equals("Spaces"))
        {
            shown = new ArrayList(StorageData.spaces);
        }
        else
        {
            shown = new ArrayList();
        }
    }

    private static double sq(double d)
    {
        return d * d;
    }

    private int cols()
    {
        return Math.max(1, (cw - 14) / SLOT);
    }

    private int visibleRows()
    {
        return viewName().equals("Items") ? Math.max(1, ch / SLOT) : Math.max(1, ch / ROW_H);
    }

    private int totalRows()
    {
        return viewName().equals("Items") ? (shown.size() + cols() - 1) / cols() : shown.size();
    }

    private int maxScroll()
    {
        return Math.max(0, totalRows() - visibleRows());
    }

    private int barX()
    {
        return cx + cw - 12;
    }

    private boolean scrollable()
    {
        String v = viewName();
        return v.equals("Items") || v.equals("Chests") || v.equals("Spaces");
    }

    /** The item key under the mouse in the Items grid, or -1. */
    private int itemAt(int mx, int my)
    {
        if (!viewName().equals("Items") || !StorageUI.inside(mx, my, cx, cy, cols() * SLOT, visibleRows() * SLOT))
        {
            return -1;
        }

        int i = (scroll + (my - cy) / SLOT) * cols() + (mx - cx) / SLOT;
        return i >= 0 && i < shown.size() ? ((Integer)shown.get(i)).intValue() : -1;
    }

    /** The row under the mouse in the Chests and Spaces lists, or -1. */
    private int rowAt(int mx, int my)
    {
        String v = viewName();

        if (!(v.equals("Chests") || v.equals("Spaces")) || !StorageUI.inside(mx, my, cx, cy, cw - 14, visibleRows() * ROW_H))
        {
            return -1;
        }

        int i = scroll + (my - cy) / ROW_H;
        return i < shown.size() ? i : -1;
    }

    // ---------------------------------------------------------------- input

    protected void mouseClicked(int mx, int my, int button)
    {
        if (rename != null)
        {
            boolean was = rename.isFocused();
            rename.mouseClicked(mx, my, button);

            if (was && !rename.isFocused())
            {
                commitRename();
            }
        }

        if (scrollable() && !viewName().equals("Spaces"))
        {
            search.mouseClicked(mx, my, button);
        }

        int t = tabAt(mx, my);

        if (t != Integer.MIN_VALUE)
        {
            mc.sndManager.playSoundFX("random.click", 1.0F, 1.0F);
            openTab(t);
            return;
        }

        if (pages() > 1 && my >= py - 42 && my < py - 30)
        {
            if (StorageUI.inside(mx, my, px, py - 42, 14, 12))
            {
                page = Math.max(0, page - 1);
            }
            else if (StorageUI.inside(mx, my, px + 60, py - 42, 14, 12))
            {
                page = Math.min(pages() - 1, page + 1);
            }

            return;
        }

        for (int i = controls.size() - 1; i >= 0; i--)
        {
            Control c = (Control)controls.get(i);

            if (c.over(mx, my) && (button == 0 || button == 1))
            {
                mc.sndManager.playSoundFX("random.click", 1.0F, 1.0F);
                if (c != deleteButton)
                {
                    confirmDelete = false;
                }

                c.click(mx, my, button);
                StorageData.saveSettings();
                return;
            }
        }

        confirmDelete = false;

        if (scrollable() && maxScroll() > 0 && StorageUI.inside(mx, my, barX(), cy, 12, ch))
        {
            draggingBar = true;
            return;
        }

        int key = itemAt(mx, my);

        if (key != -1 && (button == 0 || button == 1))
        {
            mc.sndManager.playSoundFX("random.click", 1.0F, 1.0F);

            if (StorageSpacesMod.locateKey == key && StorageSpacesMod.locateRecord == null && button == 1)
            {
                StorageSpacesMod.clearLocate();
                setStatus("Locating stopped");
                return;
            }

            StorageSpacesMod.locate(key, space());

            if (button == 0 && StorageData.locateCloses)
            {
                mc.displayGuiScreen(null);
            }
            else
            {
                setStatus("Locating " + StorageData.itemName(key) + (StorageData.outlines == 0 ? "" : ": see the green boxes"));
            }

            return;
        }

        int row = rowAt(mx, my);

        if (row >= 0 && button == 0)
        {
            mc.sndManager.playSoundFX("random.click", 1.0F, 1.0F);
            Object o = shown.get(row);

            if (o instanceof StorageData.Space)
            {
                openTab(((StorageData.Space)o).id);
            }
            else
            {
                StorageSpacesMod.locateContainer((StorageData.Record)o);

                if (StorageData.locateCloses)
                {
                    mc.displayGuiScreen(null);
                }
                else
                {
                    setStatus("Locating that container");
                }
            }

            return;
        }

        if (viewName().equals("Chart") && button == 0)
        {
            int slice = hotSlice(mx, my);

            if (slice >= 0)
            {
                Object w = pieWhat[slice];

                if (w instanceof StorageData.Space)
                {
                    openTab(((StorageData.Space)w).id);
                }
                else if (w instanceof Integer)
                {
                    StorageSpacesMod.locate(((Integer)w).intValue(), space());
                    setStatus("Locating " + StorageData.itemName(((Integer)w).intValue()));
                }
            }
        }

        if (tab == TAB_TOTAL && StorageData.spaces.isEmpty() && button == 0 && StorageUI.inside(mx, my, px + pw / 2 - 80, cy + ch / 2 + 10, 160, 20))
        {
            openTab(-3);
        }
    }

    protected void mouseMovedOrUp(int mx, int my, int button)
    {
        if (button == 0)
        {
            if (dragging != null)
            {
                StorageData.saveSettings();
            }

            draggingBar = false;
            dragging = null;
        }
    }

    public void handleMouseInput()
    {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();

        if (wheel == 0)
        {
            return;
        }

        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int dir = wheel > 0 ? 1 : -1;

        if (my >= py - 28 && my < py)
        {
            page = Math.max(0, Math.min(pages() - 1, page - dir));
            return;
        }

        for (int i = controls.size() - 1; i >= 0; i--)
        {
            Control c = (Control)controls.get(i);

            if (c.over(mx, my) && c.wheel(dir))
            {
                StorageData.saveSettings();
                return;
            }
        }

        if (scrollable())
        {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - dir));
        }
    }

    protected void keyTyped(char c, int key)
    {
        if (rename != null && rename.isFocused())
        {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER || key == Keyboard.KEY_ESCAPE)
            {
                if (key != Keyboard.KEY_ESCAPE)
                {
                    commitRename();
                }
                else
                {
                    rename.setText(space() != null ? space().name : "");
                }

                rename.setFocused(false);
            }
            else
            {
                rename.textboxKeyTyped(c, key);
            }

            return;
        }

        if (search.isFocused())
        {
            if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN)
            {
                search.setFocused(false);
            }
            else if (search.textboxKeyTyped(c, key))
            {
                scroll = 0;
            }

            return;
        }

        if (key == StorageSpacesMod.KEY_MENU.keyCode || key == mc.gameSettings.keyBindInventory.keyCode)
        {
            if (System.currentTimeMillis() - openedAt >= CLOSE_GUARD)
            {
                mc.displayGuiScreen(null);
            }

            return;
        }

        // Ctrl+F or / jumps to the search box.
        if ((key == Keyboard.KEY_F && isCtrlKeyDown() || key == Keyboard.KEY_SLASH) && scrollable() && !viewName().equals("Spaces"))
        {
            search.setFocused(true);
            return;
        }

        // Tab / Shift+Tab walk the tabs.
        if (key == Keyboard.KEY_TAB)
        {
            List ids = tabIds();
            ids.remove(ids.size() - 1);
            ids.add(Integer.valueOf(TAB_SETTINGS));
            int i = ids.indexOf(Integer.valueOf(tab));
            i = (i + (isShiftKeyDown() ? ids.size() - 1 : 1)) % ids.size();
            openTab(((Integer)ids.get(i)).intValue());
            int index = tabIndex(tab);

            if (index >= 0)
            {
                page = index / tabsPerPage();
            }

            return;
        }

        super.keyTyped(c, key);
    }

    public void updateScreen()
    {
        search.updateCursorCounter();

        if (rename != null)
        {
            rename.updateCursorCounter();
        }
    }

    // ---------------------------------------------------------------- drawing

    public void drawScreen(int mx, int my, float partialTicks)
    {
        StorageUI.refresh();

        if (tab >= 0 && space() == null)
        {
            openTab(TAB_TOTAL);
        }

        if (dragging != null)
        {
            if (Mouse.isButtonDown(0))
            {
                dragging.drag(mx, my);
            }
            else
            {
                dragging = null;
            }
        }

        if (draggingBar)
        {
            if (Mouse.isButtonDown(0) && maxScroll() > 0)
            {
                scroll = Math.max(0, Math.min(maxScroll(), Math.round((my - cy - 7) * maxScroll() / (float)Math.max(1, ch - 15))));
            }
            else
            {
                draggingBar = false;
            }
        }

        drawDefaultBackground();
        List tabs = visibleTabs();

        for (int i = 0; i < tabs.size(); i++)
        {
            int[] t = (int[])tabs.get(i);

            if (t[1] != tab)
            {
                drawTab(t, mx, my);
            }
        }

        StorageUI.panel(px, py, px + pw, py + ph);

        for (int i = 0; i < tabs.size(); i++)
        {
            int[] t = (int[])tabs.get(i);

            if (t[1] == tab)
            {
                drawTab(t, mx, my);
            }
        }

        if (pages() > 1)
        {
            int y = py - 42;
            StorageUI.button(mc, px, y, 14, 12, page > 0 ? (StorageUI.inside(mx, my, px, y, 14, 12) ? 2 : 1) : 0);
            StorageUI.buttonText(fontRenderer, "<", px, y, 14, 12, 1);
            StorageUI.centered(fontRenderer, (page + 1) + " / " + pages(), px + 37, y + 2, 0xFFFFFFFF);
            StorageUI.button(mc, px + 60, y, 14, 12, page < pages() - 1 ? (StorageUI.inside(mx, my, px + 60, y, 14, 12) ? 2 : 1) : 0);
            StorageUI.buttonText(fontRenderer, ">", px + 60, y, 14, 12, 1);
        }

        StorageData.Space s = space();
        StorageData.Stats st = tab == TAB_SETTINGS ? null : StorageSpacesMod.cachedStats(s);
        String title = tab == TAB_SETTINGS ? "Settings" : (s != null ? s.name : "All Storage");
        StorageUI.text(fontRenderer, StorageUI.fit(fontRenderer, title, pw - 230), px + 8, py + 6, StorageUI.LABEL);
        List tips = new ArrayList();

        if (st != null)
        {
            refreshShown(st);
            scroll = Math.min(scroll, maxScroll());
            drawSummary(s, st, mx, my, tips);
        }

        String view = viewName();

        if (tab == TAB_SETTINGS)
        {
            drawSettings();
        }
        else if (tab == TAB_TOTAL && StorageData.spaces.isEmpty())
        {
            drawEmpty(mx, my);
        }
        else if (view.equals("Items"))
        {
            drawItems(s, st, mx, my, tips);
        }
        else if (view.equals("Chests"))
        {
            drawChests(st, mx, my, tips);
        }
        else if (view.equals("Spaces"))
        {
            StorageUI.text(fontRenderer, "Each space's share of all stored items. Click one to open it.", px + 8, py + 34, StorageUI.MUTED);
            drawSpaces(st, mx, my, tips);
        }
        else if (view.equals("Chart"))
        {
            drawChart(s, st, mx, my, tips);
        }
        else if (view.equals("Info"))
        {
            drawInfo(s, st);
        }

        if (scrollable() && st != null && !(tab == TAB_TOTAL && StorageData.spaces.isEmpty()))
        {
            if (!view.equals("Spaces"))
            {
                search.drawTextBox();

                if (search.getText().length() == 0 && !search.isFocused())
                {
                    StorageUI.text(fontRenderer, "Search...", px + 12, py + 34, StorageUI.FAINT);
                }
            }

            drawScrollbar();
        }

        Control over = null;

        for (int i = 0; i < controls.size(); i++)
        {
            Control c = (Control)controls.get(i);
            c.draw(mx, my);

            if (c.over(mx, my))
            {
                over = c;
            }
        }

        // Footer: a message, or where the numbers come from.
        String foot;

        if (status != null && System.currentTimeMillis() - statusTime < 4000L)
        {
            foot = status;
        }
        else if (st != null)
        {
            foot = source(st);
        }
        else
        {
            foot = "Keys: " + Keyboard.getKeyName(StorageSpacesMod.KEY_MENU.keyCode) + " menu, " + Keyboard.getKeyName(StorageSpacesMod.KEY_SELECT.keyCode) + " select (Options > Controls)";
        }

        StorageUI.text(fontRenderer, StorageUI.fit(fontRenderer, foot, pw - 16), px + 8, py + ph - 12, StorageUI.MUTED);

        if (over != hovered)
        {
            hovered = over;
            hoverStart = System.currentTimeMillis();
        }

        int t = tabAt(mx, my);

        if (t != Integer.MIN_VALUE)
        {
            String tip = tabTip(t);

            if (tip != null)
            {
                tooltip(tip, mx, my);
            }
        }
        else if (!tips.isEmpty())
        {
            tooltip((String)tips.get(0), mx, my);
        }
        else if (hovered != null && hovered.tip != null && dragging == null && System.currentTimeMillis() - hoverStart > 500L)
        {
            tooltip(hovered.tip, mx, my);
        }
    }

    /** Where the numbers come from: live, from the save, or (on servers) from chests opened. */
    private String source(StorageData.Stats st)
    {
        boolean sp = mc.getIntegratedServer() != null;
        int live = st.records.size() - st.cached;
        String s = live + " live";

        if (st.cached > 0)
        {
            s += ", " + st.cached + (sp ? " from the save" : " remembered") + " (oldest " + StorageData.ago(st.oldest) + ")";
        }

        if (st.unseen > 0)
        {
            s += ", \u00a7c" + st.unseen + " not opened yet\u00a7r";
        }

        if (!sp)
        {
            s += "  \u00a77Server: open chests to count them";
        }
        else if (!StorageScanner.live)
        {
            s += "  \u00a77(no server hook: from the save only)";
        }

        return s;
    }

    private static String plural(long n, String count, String word)
    {
        return count + " " + word + (n == 1 ? "" : "s");
    }

    /**
     * The stats line and the fill bar share one row. Each step down is shorter than the last
     * (grouped numbers, then compact ones, then fewer words), and the first one where the
     * stats end a gap before the slot count is drawn, so the two never run into each other.
     * Hovering the row shows everything exactly.
     */
    private void drawSummary(StorageData.Space s, StorageData.Stats st, int mx, int my, List tips)
    {
        int y = py + 19;
        boolean info = viewName().equals("Info");
        long items = st.total;
        int kinds = st.items.size();
        int boxes = st.records.size();
        String sep = "  \u00a78|\u00a7r  ";
        String tight = " \u00a78|\u00a7r ";
        String n = StorageData.number(items, -1);
        String c = StorageData.compact(items);
        String[] lefts = new String[] {
            plural(items, n, "item") + sep + plural(kinds, String.valueOf(kinds), "kind") + sep + plural(boxes, String.valueOf(boxes), "container"),
            plural(items, c, "item") + sep + plural(kinds, String.valueOf(kinds), "kind") + sep + plural(boxes, String.valueOf(boxes), "container"),
            plural(items, c, "item") + tight + plural(kinds, String.valueOf(kinds), "kind") + tight + plural(boxes, String.valueOf(boxes), "container"),
            plural(items, c, "item") + tight + plural(kinds, String.valueOf(kinds), "kind"),
            plural(items, c, "item")
        };
        String[] rights = new String[] {
            StorageData.exact(st.used) + " / " + StorageData.exact(st.slots) + " slots",
            StorageData.compact(st.used) + " / " + StorageData.compact(st.slots) + " slots",
            StorageData.compact(st.used) + "/" + StorageData.compact(st.slots),
            ""
        };
        int bw = 64;
        int bx = px + pw - 8 - bw;
        int lx = px + 8;
        String left = lefts[lefts.length - 1];
        String right = "";
        boolean found = false;

        // {stats, slot count} pairs, best first: all three stats are kept as long as the slot
        // count can still shrink, then the slot count goes before the stats lose a part.
        int[][] order = new int[][] {{0, 0}, {1, 0}, {0, 1}, {1, 1}, {1, 2}, {2, 2}, {1, 3}, {2, 3}, {3, 2}, {3, 3}, {4, 3}};

        for (int i = 0; i < order.length && !found; i++)
        {
            String l = lefts[order[i][0]];
            String r = rights[order[i][1]];
            int rw = r.length() > 0 ? fontRenderer.getStringWidth(r) + 6 : 0;
            int lw = info ? 0 : fontRenderer.getStringWidth(l) + 12;

            if (lx + lw <= bx - rw)
            {
                left = l;
                right = r;
                found = true;
            }
        }

        if (!info)
        {
            int room = bx - (right.length() > 0 ? fontRenderer.getStringWidth(right) + 6 : 0) - 12 - lx;
            StorageUI.text(fontRenderer, StorageUI.fit(fontRenderer, left, room), lx, y, StorageUI.LABEL);
        }

        float fill = st.slots > 0 ? st.used / (float)st.slots : 0.0F;
        StorageUI.bar(bx, y - 1, bw, 10, fill);
        String pct = st.slots > 0 ? Math.round(fill * 100.0F) + "%" : "-";

        if (right.length() > 0)
        {
            StorageUI.text(fontRenderer, right, bx - 6 - fontRenderer.getStringWidth(right), y, StorageUI.MUTED);
        }

        fontRenderer.drawStringWithShadow(pct, bx + (bw - fontRenderer.getStringWidth(pct)) / 2, y, 0xFFFFFF);

        if (StorageUI.inside(mx, my, px + 4, y - 2, pw - 8, 12))
        {
            tips.add((s != null ? s.name : "All Storage") + "\n\u00a77" + plural(items, StorageData.exact(items), "item") + " of " + plural(kinds, String.valueOf(kinds), "kind") + "\n\u00a77" + plural(boxes, StorageData.exact(boxes), "container") + "\n\u00a77" + StorageData.exact(st.used) + " / " + StorageData.exact(st.slots) + " slots used (" + pct + ")");
        }
    }

    private void drawScrollbar()
    {
        int x = barX();
        StorageUI.slot(x, cy, x + 12, cy + ch, StorageUI.SLOT);
        int max = maxScroll();
        int ky = cy + (max > 0 ? (ch - 15) * scroll / max : 0);
        StorageUI.knob(mc, x, ky, max > 0);
    }

    private void drawEmpty(int mx, int my)
    {
        int y = cy + ch / 2 - 30;
        StorageUI.centered(fontRenderer, "No storage spaces yet.", px + pw / 2, y, StorageUI.LABEL);
        StorageUI.centered(fontRenderer, "Select the area around your chests: left-click one", px + pw / 2, y + 14, StorageUI.MUTED);
        StorageUI.centered(fontRenderer, "corner, right-click the other, then press Enter.", px + pw / 2, y + 25, StorageUI.MUTED);
        int bx = px + pw / 2 - 80;
        int by = cy + ch / 2 + 10;
        int state = StorageUI.inside(mx, my, bx, by, 160, 20) ? 2 : 1;
        StorageUI.button(mc, bx, by, 160, 20, state);
        StorageUI.buttonText(fontRenderer, "Select a Storage Space (" + Keyboard.getKeyName(StorageSpacesMod.KEY_SELECT.keyCode) + ")", bx, by, 160, 20, state);
    }

    private void drawItems(StorageData.Space s, StorageData.Stats st, int mx, int my, List tips)
    {
        int cols = cols();
        int rows = visibleRows();

        for (int r = 0; r < rows; r++)
        {
            for (int c = 0; c < cols; c++)
            {
                int x = cx + c * SLOT;
                int y = cy + r * SLOT;
                StorageUI.slot(x, y, x + SLOT, y + SLOT, StorageUI.SLOT);
            }
        }

        if (shown.isEmpty())
        {
            String msg = search.getText().length() > 0 ? "Nothing matches \"" + search.getText() + "\"" : (st.records.isEmpty() ? (st.unseen > 0 ? "Open the chests here to count them" : "No containers in this space") : "These containers are empty");
            Gui.drawRect(cx + 1, cy + ch / 2 - 8, cx + cols * SLOT - 1, cy + ch / 2 + 10, KirTheme.surface(StorageUI.OVERLAY));
            StorageUI.centered(fontRenderer, msg, cx + cols * SLOT / 2, cy + ch / 2 - 3, StorageUI.LABEL);
            return;
        }

        int hotKey = itemAt(mx, my);

        for (int i = scroll * cols; i < Math.min(shown.size(), (scroll + rows) * cols); i++)
        {
            int k = ((Integer)shown.get(i)).intValue();
            int j = i - scroll * cols;
            int x = cx + j % cols * SLOT;
            int y = cy + j / cols * SLOT;

            if (k == StorageSpacesMod.locateKey && StorageSpacesMod.locateRecord == null)
            {
                Gui.drawRect(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, KirTheme.surface(StorageUI.LOCATED));
            }

            if (k == hotKey)
            {
                Gui.drawRect(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, KirTheme.surface(StorageUI.ROW_HOT));
            }

            StorageUI.item(mc, StorageData.stack(k), x + 1, y + 1);
            StorageUI.count(mc, fontRenderer, st.count(k), x + 1, y + 1);

            if (StorageData.deltas && s != null && s.baseline != null)
            {
                long d = StorageData.delta(s, st, k);

                if (d != 0)
                {
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    int color = d > 0 ? 0xFF40E040 : 0xFFE04040;
                    Gui.drawRect(x + 1, y + 1, x + 4, y + 4, color);
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                }
            }
        }

        if (hotKey != -1)
        {
            tips.add(itemTip(s, st, hotKey));
        }
    }

    private String itemTip(StorageData.Space s, StorageData.Stats st, int k)
    {
        long n = st.count(k);
        int per = StorageData.maxStack(k);
        StringBuilder b = new StringBuilder(StorageData.itemName(k));
        b.append("\n\u00a78ID ").append(StorageData.idString(k));
        b.append("\n\u00a7f").append(StorageData.exact(n)).append("\u00a77 in total");

        if (n >= per)
        {
            b.append(" (").append(StorageData.stacks(n, per)).append(")");
        }

        b.append("\n\u00a77In ").append(st.containersWith(k)).append(" of ").append(st.records.size()).append(" containers");
        b.append("\n\u00a77").append(percent(n, st.total)).append(" of the items here");

        if (s != null && s.baseline != null)
        {
            long d = StorageData.delta(s, st, k);
            b.append("\n\u00a77Since baseline: ").append(d > 0 ? "\u00a7a" : (d < 0 ? "\u00a7c" : "\u00a77")).append(StorageData.signed(d));
        }

        if (s == null && StorageData.spaces.size() > 1)
        {
            for (int i = 0; i < StorageData.spaces.size(); i++)
            {
                StorageData.Space sp = (StorageData.Space)StorageData.spaces.get(i);
                long c = StorageSpacesMod.cachedStats(sp).count(k);

                if (c > 0)
                {
                    b.append("\n\u00a78- \u00a77").append(sp.name).append(": ").append(StorageData.number(c, k));
                }
            }
        }

        b.append("\n\u00a7eClick: locate");
        return b.toString();
    }

    private static String percent(long part, long whole)
    {
        if (whole <= 0L)
        {
            return "0%";
        }

        double p = part * 100.0D / whole;
        return (p < 10.0D ? new java.text.DecimalFormat("0.0").format(p) : String.valueOf(Math.round(p))) + "%";
    }

    private void drawChests(StorageData.Stats st, int mx, int my, List tips)
    {
        int rows = visibleRows();
        int w = cw - 14;
        List keys = matchingKeys(st, search.getText().trim().toLowerCase());
        int hot = rowAt(mx, my);
        StorageUI.slot(cx, cy, cx + w, cy + ch, StorageUI.WELL);

        if (shown.isEmpty())
        {
            StorageUI.centered(fontRenderer, keys != null ? "No container holds that" : "No containers in this space", cx + w / 2, cy + ch / 2 - 4, StorageUI.LABEL);
        }

        for (int i = scroll; i < Math.min(shown.size(), scroll + rows); i++)
        {
            StorageData.Record r = (StorageData.Record)shown.get(i);
            int y = cy + (i - scroll) * ROW_H;

            if (i == hot)
            {
                Gui.drawRect(cx + 1, y + 1, cx + w - 1, y + ROW_H - 1, KirTheme.surface(StorageUI.ROW_HOT));
            }
            else if (i % 2 == 1)
            {
                Gui.drawRect(cx + 1, y + 1, cx + w - 1, y + ROW_H - 1, KirTheme.surface(StorageUI.ROW_ALT));
            }

            int top = keys != null ? ((Integer)keys.get(0)).intValue() : r.topKey();

            if (top != -1)
            {
                StorageUI.item(mc, StorageData.stack(top), cx + 2, y + 2);
            }

            String name = StorageData.KIND_NAMES[Math.min(r.kind, 4)] + "  \u00a78" + r.x + ", " + r.y + ", " + r.z;
            StorageUI.text(fontRenderer, name, cx + 22, y + 6, StorageUI.LABEL);
            long n = keys != null ? matchingCount(r, keys) : r.total();
            String count = StorageData.number(n, keys != null && keys.size() == 1 ? top : -1);
            int bw = 46;
            int bx = cx + w - 6 - bw;
            StorageUI.bar(bx, y + 5, bw, 10, r.used / (float)Math.max(1, r.slots));
            String fill = r.used + "/" + r.slots;
            fontRenderer.drawStringWithShadow(fill, bx + (bw - fontRenderer.getStringWidth(fill)) / 2, y + 6, 0xFFFFFF);
            boolean live = StorageData.isLive(r);
            String age = live ? "\u00a72live" : "\u00a78" + StorageData.ago(r.seen);
            int ax = bx - 6 - fontRenderer.getStringWidth(age);
            StorageUI.text(fontRenderer, age, ax, y + 6, StorageUI.MUTED);
            StorageUI.text(fontRenderer, count, ax - 8 - fontRenderer.getStringWidth(count), y + 6, StorageUI.LABEL);

            if (i == hot)
            {
                tips.add(chestTip(r));
            }
        }
    }

    private String chestTip(StorageData.Record r)
    {
        StringBuilder b = new StringBuilder(StorageData.KIND_NAMES[Math.min(r.kind, 4)] + " at " + r.x + ", " + r.y + ", " + r.z);
        b.append("\n\u00a77").append(StorageData.exact(r.total())).append(" items, ").append(r.used).append("/").append(r.slots).append(" slots");
        b.append("\n\u00a77").append(StorageData.isLive(r) ? "Live" : "Last seen " + StorageData.ago(r.seen));
        int[] order = new int[r.keys.length];

        for (int i = 0; i < order.length; i++)
        {
            order[i] = i;
        }

        // Biggest first, up to eight lines.
        for (int i = 0; i < order.length; i++)
        {
            for (int j = i + 1; j < order.length; j++)
            {
                if (r.counts[order[j]] > r.counts[order[i]])
                {
                    int t = order[i];
                    order[i] = order[j];
                    order[j] = t;
                }
            }
        }

        for (int i = 0; i < Math.min(8, order.length); i++)
        {
            b.append("\n\u00a78- \u00a7f").append(StorageData.itemName(r.keys[order[i]])).append(" \u00a77x").append(StorageData.exact(r.counts[order[i]]));
        }

        if (order.length > 8)
        {
            b.append("\n\u00a78and ").append(order.length - 8).append(" more");
        }

        b.append("\n\u00a7eClick: locate");
        return b.toString();
    }

    private void drawSpaces(StorageData.Stats all, int mx, int my, List tips)
    {
        int rows = visibleRows();
        int w = cw - 14;
        int hot = rowAt(mx, my);
        StorageUI.slot(cx, cy, cx + w, cy + ch, StorageUI.WELL);

        for (int i = scroll; i < Math.min(shown.size(), scroll + rows); i++)
        {
            StorageData.Space s = (StorageData.Space)shown.get(i);
            StorageData.Stats st = StorageSpacesMod.cachedStats(s);
            int y = cy + (i - scroll) * ROW_H;

            if (i == hot)
            {
                Gui.drawRect(cx + 1, y + 1, cx + w - 1, y + ROW_H - 1, KirTheme.surface(StorageUI.ROW_HOT));
            }
            else if (i % 2 == 1)
            {
                Gui.drawRect(cx + 1, y + 1, cx + w - 1, y + ROW_H - 1, KirTheme.surface(StorageUI.ROW_ALT));
            }

            Gui.drawRect(cx + 1, y + 1, cx + 3, y + ROW_H - 1, 0xFF000000 | s.color);
            StorageUI.item(mc, tabIcon(s.id), cx + 5, y + 2);
            StorageUI.text(fontRenderer, StorageUI.fit(fontRenderer, s.name, w / 3), cx + 25, y + 6, StorageUI.LABEL);
            int bw = 46;
            int bx = cx + w - 6 - bw;
            StorageUI.bar(bx, y + 5, bw, 10, st.slots > 0 ? st.used / (float)st.slots : 0.0F);
            String pct = st.slots > 0 ? Math.round(st.used * 100.0F / st.slots) + "%" : "-";
            fontRenderer.drawStringWithShadow(pct, bx + (bw - fontRenderer.getStringWidth(pct)) / 2, y + 6, 0xFFFFFF);
            String share = percent(st.total, all.total);
            String info = StorageData.number(st.total, -1) + " items  \u00a78" + st.records.size() + " cont.  " + share;
            StorageUI.text(fontRenderer, info, bx - 6 - fontRenderer.getStringWidth(info), y + 6, StorageUI.LABEL);

            if (i == hot)
            {
                tips.add(s.name + "\n\u00a77" + StorageData.exact(st.total) + " items, " + st.items.size() + " kinds\n\u00a77" + share + " of all stored items\n\u00a77" + StorageData.dimName(s.dim) + ", " + s.sizeX() + " x " + s.sizeY() + " x " + s.sizeZ() + "\n\u00a7eClick: open");
            }
        }
    }

    // ---------------------------------------------------------------- chart

    /** Fills the pie arrays from the stats, by the chart settings. */
    private void buildPie(StorageData.Space s, StorageData.Stats st)
    {
        List what = new ArrayList();
        List names = new ArrayList();
        List values = new ArrayList();
        List colors = new ArrayList();
        boolean bySpace = s == null && StorageData.chartBy == 1;
        boolean slots = StorageData.chartMeasure == 1;
        long sum = 0L;

        if (bySpace)
        {
            for (int i = 0; i < StorageData.spaces.size(); i++)
            {
                StorageData.Space sp = (StorageData.Space)StorageData.spaces.get(i);
                StorageData.Stats ss = StorageSpacesMod.cachedStats(sp);
                long v = slots ? ss.used : ss.total;
                what.add(sp);
                names.add(sp.name);
                values.add(Long.valueOf(v));
                colors.add(Integer.valueOf(sp.color));
            }
        }
        else
        {
            // Biggest items first; past ten, the rest is "Other".
            List keys = new ArrayList(st.items.keySet());
            final Map stackCounts = new java.util.HashMap();

            for (int i = 0; i < keys.size(); i++)
            {
                int k = ((Integer)keys.get(i)).intValue();
                long n = st.count(k);
                int per = StorageData.maxStack(k);
                stackCounts.put(keys.get(i), Long.valueOf(slots ? (n + per - 1) / per : n));
            }

            Collections.sort(keys, new Comparator()
            {
                public int compare(Object a, Object b)
                {
                    long va = ((Long)stackCounts.get(a)).longValue();
                    long vb = ((Long)stackCounts.get(b)).longValue();
                    return va > vb ? -1 : (va < vb ? 1 : 0);
                }
            });
            long other = 0L;
            int otherKinds = 0;

            for (int i = 0; i < keys.size(); i++)
            {
                long v = ((Long)stackCounts.get(keys.get(i))).longValue();

                if (i < 10)
                {
                    what.add(keys.get(i));
                    names.add(StorageData.itemName(((Integer)keys.get(i)).intValue()));
                    values.add(Long.valueOf(v));
                    colors.add(Integer.valueOf(StorageUI.PIE_COLORS[i]));
                }
                else
                {
                    other += v;
                    otherKinds++;
                }
            }

            if (other > 0L)
            {
                what.add("other");
                names.add("Other (" + otherKinds + " kinds)");
                values.add(Long.valueOf(other));
                colors.add(Integer.valueOf(StorageUI.PIE_OTHER));
            }
        }

        if (slots && StorageData.showEmpty)
        {
            // The empty slots, so the pie shows how full storage is.
            long free = st.slots - st.used;

            if (free > 0L)
            {
                what.add("empty");
                names.add("Empty slots");
                values.add(Long.valueOf(free));
                colors.add(Integer.valueOf(StorageUI.PIE_EMPTY));
            }
        }

        for (int i = 0; i < values.size(); i++)
        {
            sum += ((Long)values.get(i)).longValue();
        }

        int n = values.size();
        pieFractions = new float[n];
        pieColors = new int[n];
        pieWhat = what.toArray();
        pieNames = (String[])names.toArray(new String[n]);
        pieValues = new long[n];

        for (int i = 0; i < n; i++)
        {
            pieValues[i] = ((Long)values.get(i)).longValue();
            pieFractions[i] = sum > 0L ? pieValues[i] / (float)sum : 0.0F;
            pieColors[i] = ((Integer)colors.get(i)).intValue();
        }
    }

    private int hotSlice(int mx, int my)
    {
        int slice = StorageUI.sliceAt(mx, my, pieX, pieY, pieR, pieFractions);

        if (slice >= 0)
        {
            return slice;
        }

        if (mx >= legendX && mx < legendX + legendW && my >= legendY)
        {
            int i = (my - legendY) / 12;
            return i < pieFractions.length ? i : -1;
        }

        return -1;
    }

    private void drawChart(StorageData.Space s, StorageData.Stats st, int mx, int my, List tips)
    {
        buildPie(s, st);
        int areaY = cy;
        int areaH = ch;
        // Below the caption line.
        pieR = Math.max(16, Math.min((areaH - 18) / 2, (cw / 2) / 2 - 6));
        pieX = cx + 10 + pieR;
        pieY = areaY + 13 + (areaH - 13) / 2;
        legendX = pieX + pieR + 16;
        legendY = areaY + Math.max(0, (areaH - pieFractions.length * 12) / 2);
        legendW = cx + cw - legendX;
        StorageUI.slot(cx, cy, cx + cw, cy + ch, StorageUI.WELL);
        long sum = 0L;

        for (int i = 0; i < pieValues.length; i++)
        {
            sum += pieValues[i];
        }

        if (sum == 0L)
        {
            StorageUI.centered(fontRenderer, s == null && StorageData.chartBy == 1 && StorageData.spaces.isEmpty() ? "No spaces yet" : "Nothing stored here yet", cx + cw / 2, cy + ch / 2 - 4, StorageUI.LABEL);
            return;
        }

        int hot = hotSlice(mx, my);
        StorageUI.pie(pieX, pieY, pieR, pieFractions, pieColors, hot);

        // Percent labels on the big slices.
        double start = 0.0D;

        for (int i = 0; i < pieFractions.length; i++)
        {
            double sweep = pieFractions[i] * Math.PI * 2.0D;

            if (pieFractions[i] >= 0.07F)
            {
                double mid = start + sweep / 2.0D;
                int lx = pieX + (int)(Math.sin(mid) * pieR * 0.62D);
                int ly = pieY - (int)(Math.cos(mid) * pieR * 0.62D);
                String p = Math.round(pieFractions[i] * 100.0F) + "%";
                int c = KirTheme.surface(0xFF000000 | pieColors[i]) & 0xFFFFFF;

                // Dark text on light slices (the empty slots), white on the rest.
                if ((c >> 16 & 255) * 3 + (c >> 8 & 255) * 6 + (c & 255) > 1900)
                {
                    fontRenderer.drawString(p, lx - fontRenderer.getStringWidth(p) / 2, ly - 4, 0x404040);
                }
                else
                {
                    fontRenderer.drawStringWithShadow(p, lx - fontRenderer.getStringWidth(p) / 2, ly - 4, 0xFFFFFF);
                }
            }

            start += sweep;
        }

        // Legend: swatch, icon, name, percentage.
        for (int i = 0; i < pieFractions.length; i++)
        {
            int y = legendY + i * 12;

            if (i == hot)
            {
                Gui.drawRect(legendX - 2, y - 1, legendX + legendW - 2, y + 11, KirTheme.surface(StorageUI.ROW_HOT));
            }

            StorageUI.slot(legendX, y + 1, legendX + 9, y + 10, 0xFF000000 | pieColors[i]);
            int tx = legendX + 13;
            String pct = percent(pieValues[i], sum);
            int pw2 = fontRenderer.getStringWidth("100%");
            StorageUI.text(fontRenderer, StorageUI.fit(fontRenderer, pieNames[i], legendW - 20 - pw2 - 4), tx, y + 2, StorageUI.LABEL);
            StorageUI.text(fontRenderer, pct, legendX + legendW - 6 - fontRenderer.getStringWidth(pct), y + 2, StorageUI.STRONG);
        }

        String measure = StorageData.chartMeasure == 1 ? "slots" : "items";
        boolean overlap = s == null && StorageData.chartBy == 1 && sum > (StorageData.chartMeasure == 1 ? st.slots : st.total);
        String caption = (s == null && StorageData.chartBy == 1 ? "Share of " : "Breakdown of ") + StorageData.exact(sum) + " " + measure + (overlap ? " (spaces overlap: shared chests count in each)" : "");
        StorageUI.text(fontRenderer, StorageUI.fit(fontRenderer, caption, cw - 8), cx + 4, cy + 3, StorageUI.MUTED);

        if (hot >= 0)
        {
            Object w = pieWhat[hot];
            String tip = pieNames[hot] + "\n\u00a7f" + StorageData.exact(pieValues[hot]) + "\u00a77 " + measure + " (" + percent(pieValues[hot], sum) + ")";

            if (w instanceof StorageData.Space)
            {
                StorageData.Stats ss = StorageSpacesMod.cachedStats((StorageData.Space)w);
                tip += "\n\u00a77" + ss.records.size() + " containers, " + Math.round(ss.slots > 0 ? ss.used * 100.0F / ss.slots : 0.0F) + "% full\n\u00a7eClick: open";
            }
            else if (w instanceof Integer)
            {
                tip += "\n\u00a77In " + st.containersWith(((Integer)w).intValue()) + " containers\n\u00a7eClick: locate";
            }

            tips.add(tip);
        }
    }

    // ---------------------------------------------------------------- info and settings

    private void drawInfo(StorageData.Space s, StorageData.Stats st)
    {
        if (s == null)
        {
            return;
        }

        StorageUI.text(fontRenderer, "Name", px + 8, py + 20, StorageUI.MUTED);
        rename.drawTextBox();
        int y = py + 35;
        int lh = 10;
        StorageUI.text(fontRenderer, StorageUI.fit(fontRenderer, "Area  \u00a78x " + s.x1 + ".." + s.x2 + "  y " + s.y1 + ".." + s.y2 + "  z " + s.z1 + ".." + s.z2 + "  (" + s.sizeX() + " x " + s.sizeY() + " x " + s.sizeZ() + ", " + StorageData.dimName(s.dim) + ")", cw), cx, y, StorageUI.LABEL);
        StringBuilder kinds = new StringBuilder();

        for (int i = 0; i < st.kinds.length; i++)
        {
            if (st.kinds[i] > 0)
            {
                kinds.append(kinds.length() > 0 ? ", " : "").append(st.kinds[i]).append(' ').append(StorageData.KIND_NAMES[i].toLowerCase()).append(st.kinds[i] > 1 ? "s" : "");
            }
        }

        StorageUI.text(fontRenderer, StorageUI.fit(fontRenderer, "Holds  \u00a78" + StorageData.exact(st.total) + " items in " + (kinds.length() > 0 ? kinds.toString() : "no containers"), cw), cx, y + lh, StorageUI.LABEL);
        long free = st.slots - st.used;
        StorageUI.text(fontRenderer, "Free  \u00a78" + free + " slots (room for about " + StorageData.exact(free * 64L) + " items)", cx, y + lh * 2, StorageUI.LABEL);
        String base = s.baseline != null ? "Baseline  \u00a78set " + StorageData.ago(s.baselineTime) + ": " + baselineSummary(s, st) : "Baseline  \u00a78none";
        StorageUI.text(fontRenderer, StorageUI.fit(fontRenderer, base, cw), cx, y + lh * 3, StorageUI.LABEL);
        StorageUI.text(fontRenderer, "Created  \u00a78" + StorageData.ago(s.created), cx, y + lh * 4, StorageUI.LABEL);
    }

    private static String baselineSummary(StorageData.Space s, StorageData.Stats st)
    {
        long added = 0L;
        long taken = 0L;
        java.util.Set keys = new java.util.HashSet(st.items.keySet());
        keys.addAll(s.baseline.keySet());
        Iterator it = keys.iterator();

        while (it.hasNext())
        {
            long d = StorageData.delta(s, st, ((Integer)it.next()).intValue());

            if (d > 0L)
            {
                added += d;
            }
            else
            {
                taken -= d;
            }
        }

        return "\u00a72+" + StorageData.exact(added) + "\u00a78 added, \u00a74-" + StorageData.exact(taken) + "\u00a78 taken";
    }

    private void drawSettings()
    {
        int colW = (cw - 8) / 2;
        int y = py + 20;
        StorageUI.text(fontRenderer, "Containers counted", cx, y, StorageUI.MUTED);
        StorageUI.text(fontRenderer, "Numbers", cx + colW + 8, y, StorageUI.MUTED);
        y += 27;
        StorageUI.text(fontRenderer, "Outlines in the world", cx, y, StorageUI.MUTED);
        StorageUI.text(fontRenderer, "Item order", cx + colW + 8, y, StorageUI.MUTED);
    }

    /** Vanilla's item tooltip: dark purple box, first line white, the rest grey. */
    private void tooltip(String text, int mx, int my)
    {
        String[] lines = text.split("\n");
        int w = 0;

        for (int i = 0; i < lines.length; i++)
        {
            w = Math.max(w, fontRenderer.getStringWidth(lines[i]));
        }

        int h = lines.length > 1 ? 8 + (lines.length - 1) * 10 + 2 : 8;
        int x = mx + 12;
        int y = my - 12;

        if (x + w + 4 > width)
        {
            x = mx - w - 16;
        }

        y = Math.max(4, Math.min(y, height - h - 4));
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        zLevel = 300.0F;
        int bg = 0xF0100010;
        drawGradientRect(x - 3, y - 4, x + w + 3, y - 3, bg, bg);
        drawGradientRect(x - 3, y + h + 3, x + w + 3, y + h + 4, bg, bg);
        drawGradientRect(x - 3, y - 3, x + w + 3, y + h + 3, bg, bg);
        drawGradientRect(x - 4, y - 3, x - 3, y + h + 3, bg, bg);
        drawGradientRect(x + w + 3, y - 3, x + w + 4, y + h + 3, bg, bg);
        int b1 = 0x505000FF;
        int b2 = 0x5028007F;
        drawGradientRect(x - 3, y - 2, x - 2, y + h + 2, b1, b2);
        drawGradientRect(x + w + 2, y - 2, x + w + 3, y + h + 2, b1, b2);
        drawGradientRect(x - 3, y - 3, x + w + 3, y - 2, b1, b1);
        drawGradientRect(x - 3, y + h + 2, x + w + 3, y + h + 3, b2, b2);

        for (int i = 0; i < lines.length; i++)
        {
            fontRenderer.drawStringWithShadow(lines[i], x, y, i == 0 ? 0xFFFFFF : 0xAAAAAA);
            y += i == 0 ? 12 : 10;
        }

        zLevel = 0.0F;
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}

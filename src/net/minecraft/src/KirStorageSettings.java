package net.minecraft.src;

import net.minecraft.client.Minecraft;

/**
 * The Storage Spaces tab of the Storage Spaces plugin: every setting of Storage Spaces' own Settings tab, plus
 * Open, Select, Rescan and Stop Locating rows, in Kirmod's settings screen. The tab's groups are the ones
 * KirStorageNames lists.
 */
final class KirStorageSettings
{
    static final String TAB = KirStorageNames.TAB;
    static final String COUNTING = KirStorageNames.COUNTING;
    static final String WORLD = KirStorageNames.WORLD;
    static final String LOCATE = KirStorageNames.LOCATE;
    static final String SELECTION = KirStorageNames.SELECTION;
    static final String LOOK = KirStorageNames.LOOK;

    static KirStorageMenuKey menuKey;

    private KirStorageSettings() {}

    private static String keyName(KeyBinding key)
    {
        return KirHotkey.keyName(key.keyCode);
    }

    static void register(KirPlugin plugin)
    {
        menuKey = new KirStorageMenuKey("storageSpacesMenuKey", plugin, TAB);

        // ---------------------------------------------------------------- the tab itself
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Action("storageSpacesOpen", "Open Storage Spaces Screen", TAB, true,
            "Opens Storage Spaces' own screen: the Total and per-space tabs, item lists, pie charts and the Settings tab.",
            KirModule.note("The row shows how many spaces this world has."))
        {
            protected String getLabel()
            {
                int n = StorageData.spaces.size();
                return n == 1 ? "1 space" : n + " spaces";
            }

            protected void run(Minecraft mc)
            {
                menuKey.openOwnScreen(mc, new GuiStorageSpaces(GuiStorageSpaces.lastTab));
            }
        });
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Action("storageSpacesSelect", "Select a Storage Space", TAB, true,
            "Closes this screen and starts selecting a new area: click two corners, adjust the box, then press Enter and name it.",
            KirModule.note("Same as the Select Storage Space key."))
        {
            protected String getLabel()
            {
                return StorageSelection.active ? "Selecting" : "Start (" + keyName(StorageSpacesMod.KEY_SELECT) + ")";
            }

            protected void run(Minecraft mc)
            {
                mc.displayGuiScreen((GuiScreen)null);

                if (!StorageSelection.active)
                {
                    StorageSelection.start(mc, null);
                }
            }
        });
        mod_Kirmod.registerModule(plugin, menuKey);
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Action("storageSpacesRescan", "Rescan Now", TAB, true,
            "Scans loaded chests and reads the save again right away (singleplayer).")
        {
            protected String getLabel()
            {
                return "Rescan";
            }

            protected void run(Minecraft mc)
            {
                StorageScanner.poke();
            }
        });

        // ---------------------------------------------------------------- Counting
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Choice("storageSpacesContainers", "Containers Counted", 0, COUNTING, StorageData.CONTAINER_NAMES,
            "Which containers in a space are added up.",
            KirModule.describeValue("Chests", "chests only."),
            KirModule.describeValue("+ Dispensers", "also dispensers."),
            KirModule.describeValue("All", "furnaces, brewing stands and modded containers too."))
        {
            int get() { return StorageData.containers; }
            void set(int v) { StorageData.containers = v; }
        });
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Choice("storageSpacesNumbers", "Numbers", 0, COUNTING, StorageData.NUMBER_NAMES,
            "How item counts are written.",
            KirModule.describeValue("Exact", "12,345."),
            KirModule.describeValue("Compact", "12k."),
            KirModule.describeValue("Stacks", "192 st + 57."))
        {
            int get() { return StorageData.numbers; }
            void set(int v) { StorageData.numbers = v; }
        });
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Choice("storageSpacesSort", "Item Order", 0, COUNTING, StorageData.SORT_NAMES,
            "The order of the Items view.",
            KirModule.describeValue("Count", "most first."),
            KirModule.describeValue("Name", "alphabetical."),
            KirModule.describeValue("ID", "by item id."),
            KirModule.describeValue("Change", "most gained since the baseline first."))
        {
            int get() { return StorageData.sort; }
            void set(int v) { StorageData.sort = v; }
        });
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Toggle("storageSpacesDeltas", "Show Changes", true, COUNTING,
            "Green and red marks on items that went up or down since the space's baseline.")
        {
            boolean get() { return StorageData.deltas; }
            void set(boolean v) { StorageData.deltas = v; }
        });
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Range("storageSpacesRefresh", "Refresh", 1, 30, 1, 2, " s", null, COUNTING,
            "Seconds between server-side scans of loaded chests (singleplayer).",
            KirModule.note("Unloaded chunks are read from the save every 20 s."))
        {
            int get() { return StorageData.refresh; }
            void set(int v) { StorageData.refresh = v; }
        });

        // ---------------------------------------------------------------- In the world
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Choice("storageSpacesOutlines", "Outlines", 1, WORLD, StorageData.OUTLINE_NAMES,
            "Outlines of the spaces in the world.",
            KirModule.describeValue("Off", "never."),
            KirModule.describeValue("Selecting", "only while selecting an area or locating an item."),
            KirModule.describeValue("Always", "always."))
        {
            int get() { return StorageData.outlines; }
            void set(int v) { StorageData.outlines = v; }
        });
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Toggle("storageSpacesLabels", "Name Labels", true, WORLD,
            "Each space's name and item count float above it.")
        {
            boolean get() { return StorageData.labels; }
            void set(boolean v) { StorageData.labels = v; }
        });
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Toggle("storageSpacesSeeThrough", "See-Through Outlines", false, WORLD,
            "Space outlines show faintly through blocks.")
        {
            boolean get() { return StorageData.seeThrough; }
            void set(boolean v) { StorageData.seeThrough = v; }
        });

        // ---------------------------------------------------------------- Locate
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Action("storageSpacesStopLocate", "Stop Locating", LOCATE, false,
            "Stops highlighting the containers of the located item.",
            KirModule.note("Locate an item by clicking it in Storage Spaces' item list."))
        {
            protected String getLabel()
            {
                return StorageSpacesMod.locateKey != -1 ? "Stop" : "Nothing located";
            }

            protected void run(Minecraft mc)
            {
                StorageSpacesMod.clearLocate();
            }
        });
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Range("storageSpacesLocateSeconds", "Locate For", 0, 300, 5, 60, " s", "Always", LOCATE,
            "How long located containers stay highlighted.",
            KirModule.describeValue("Always", "until stopped."))
        {
            int get() { return StorageData.locateSeconds; }
            void set(int v) { StorageData.locateSeconds = v; }
        });
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Toggle("storageSpacesLocateCloses", "Locate Closes Menu", true, LOCATE,
            "Clicking an item closes Storage Spaces' screen to show its containers.",
            KirModule.note("Right-click always keeps it open."))
        {
            boolean get() { return StorageData.locateCloses; }
            void set(boolean v) { StorageData.locateCloses = v; }
        });
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Toggle("storageSpacesLocateHud", "Locate Readout", true, LOCATE,
            "Above the hotbar: the located item, its total and the nearest container.")
        {
            boolean get() { return StorageData.locateHud; }
            void set(boolean v) { StorageData.locateHud = v; }
        });

        // ---------------------------------------------------------------- Selection
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Toggle("storageSpacesSelectHud", "Selection Help", true, SELECTION,
            "The panel at the top of the screen while selecting, with the keys.")
        {
            boolean get() { return StorageData.selectHud; }
            void set(boolean v) { StorageData.selectHud = v; }
        });
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Range("storageSpacesNudgeStep", "Nudge Step", 1, 16, 1, 1, "", null, SELECTION,
            "Blocks the selection grow, shrink and move keys (and the wheel modifier + wheel) go per press.",
            KirModule.note("Shift: 5."))
        {
            int get() { return StorageData.nudgeStep; }
            void set(int v) { StorageData.nudgeStep = v; }
        });
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Range("storageSpacesReach", "Selection Reach", 5, 128, 1, 64, "", null, SELECTION,
            "How far away a corner can be clicked in selection mode, in blocks.")
        {
            int get() { return StorageData.reach; }
            void set(int v) { StorageData.reach = v; }
        });

        // ---------------------------------------------------------------- Charts
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Toggle("storageSpacesShowEmpty", "Show Empty Space", true, LOOK,
            "Pie charts measured in Slots get an 'Empty slots' slice, showing how full storage is.",
            KirModule.describeValue("Off", "only what is stored."))
        {
            boolean get() { return StorageData.showEmpty; }
            void set(boolean v) { StorageData.showEmpty = v; }
        });
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Choice("storageSpacesChartBy", "Total Chart By", 1, LOOK, new String[] {"Item", "Space"},
            "What the Total tab's pie chart shares out.",
            KirModule.describeValue("Item", "a slice per item."),
            KirModule.describeValue("Space", "a slice per space."))
        {
            int get() { return StorageData.chartBy; }
            void set(int v) { StorageData.chartBy = v; }
        });
        mod_Kirmod.registerModule(plugin, new KirStorageModule.Choice("storageSpacesChartMeasure", "Chart Measure", 0, LOOK, new String[] {"Items", "Slots"},
            "What the pie charts measure.",
            KirModule.describeValue("Items", "item counts."),
            KirModule.describeValue("Slots", "slots used (with Show Empty Space, the free ones too)."))
        {
            int get() { return StorageData.chartMeasure; }
            void set(int v) { StorageData.chartMeasure = v; }
        });
    }

    /** Lists the keys in Kirmod's Hotkeys panel. */
    static void registerHotkeys(KirPlugin plugin)
    {
        mod_Kirmod.registerPluginKey(plugin, null, StorageSpacesMod.KEY_MENU, "Opens Storage Spaces' screen (or its Kirmod tab, see Menu Key Opens).");
        mod_Kirmod.registerPluginKey(plugin, null, StorageSpacesMod.KEY_SELECT, "Starts selecting a new storage space, or cancels the selection.");
        String g = "Selection";
        mod_Kirmod.registerPluginKey(plugin, g, StorageSpacesMod.KEY_GROW, "While selecting: grows the box at the face you look at (Shift: 5 blocks).");
        mod_Kirmod.registerPluginKey(plugin, g, StorageSpacesMod.KEY_SHRINK, "While selecting: shrinks the box at the face you look at.");
        mod_Kirmod.registerPluginKey(plugin, g, StorageSpacesMod.KEY_FORWARD, "While selecting: moves the box the way you face.");
        mod_Kirmod.registerPluginKey(plugin, g, StorageSpacesMod.KEY_BACK, "While selecting: moves the box towards you.");
        mod_Kirmod.registerPluginKey(plugin, g, StorageSpacesMod.KEY_UP, "While selecting: moves the box up.");
        mod_Kirmod.registerPluginKey(plugin, g, StorageSpacesMod.KEY_DOWN, "While selecting: moves the box down.");
        mod_Kirmod.registerPluginKey(plugin, g, StorageSpacesMod.KEY_WHEEL, "While selecting: hold it and turn the mouse wheel to grow or shrink the box.");
    }
}

package net.minecraft.src;

import net.minecraft.client.Minecraft;

/**
 * "Menu Key Opens": which screen the Storage Spaces key (I) opens: Storage Spaces' own screen, as before,
 * or its tab in Kirmod's settings screen. The own screen is kept either way, and the "Open Storage Spaces
 * Screen" row always opens it.
 *
 * Storage Spaces still handles its key: when it opens its screen and this says Kirmod, the screen is
 * swapped for Kirmod's on the next frame. A screen opened through the Open row (openOwnScreen) is left alone.
 */
public class KirStorageMenuKey extends KirModule
{
    private static final String[] VALUES = new String[] {"Own Screen", "Kirmod"};

    private final KirPlugin plugin;
    private final String tab;

    /** The own screen opened on purpose, which is not swapped. */
    private GuiScreen allowed;

    public KirStorageMenuKey(String configKey, KirPlugin plugin, String tab)
    {
        super(configKey, "Menu Key Opens", "Menu Key Opens", 0, null);
        this.plugin = plugin;
        this.tab = tab;
    }

    public String[] getValueNames()
    {
        return VALUES;
    }

    public String getCategory()
    {
        return this.tab;
    }

    public String[] getTooltip()
    {
        return new String[] {
            "Which screen the Storage Spaces key (" + KirHotkey.keyName(StorageSpacesMod.KEY_MENU.keyCode) + ", see Hotkeys) opens.",
            describeValue("Own Screen", "Storage Spaces' screen with its item lists and charts, as before."),
            describeValue("Kirmod", "this tab of Kirmod's settings."),
            note("\"Open Storage Spaces Screen\" above always opens Storage Spaces' screen.")
        };
    }

    /** Opens the own screen without it being swapped. */
    public void openOwnScreen(Minecraft mc, GuiScreen screen)
    {
        this.allowed = screen;
        mc.displayGuiScreen(screen);
    }

    public void update(Minecraft mc)
    {
        GuiScreen screen = mc.currentScreen;

        if (this.getValue() == 1 && screen != null && screen != this.allowed && screen.getClass() == GuiStorageSpaces.class && mc.theWorld != null)
        {
            mc.displayGuiScreen(new GuiKirmod((GuiScreen)null, this.plugin, this.tab));
        }
    }
}

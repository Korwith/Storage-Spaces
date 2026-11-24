package net.minecraft.src;

import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * The Storage Spaces plugin: Storage Spaces as a Kirmod plugin (see KirPlugin). All of Storage Spaces' code is
 * in this zip (StorageSpacesMod, StorageData, GuiStorageSpaces...), so it needs only Kirmod (1.28 or later).
 *
 * Storage Spaces names areas of the world that hold chests and adds up what is in them. I opens its own
 * screen, O starts selecting an area. That screen is kept as it was. Kirmod's settings screen gets a
 * "Storage Spaces" section with a Storage Spaces tab (KirStorageSettings): Open, Select a Storage
 * Space, Menu Key Opens (which screen I opens), Rescan Now and groups of its settings, mirrored both ways
 * with the own Settings tab (KirStorageModule). Settings stay in config/StorageSpaces.cfg and the spaces in
 * config/StorageSpaces/, so the standalone mod's carry over. The keys are listed in Kirmod's Hotkeys panel.
 *
 * The standalone mod_StorageSpaces zip must not be installed next to this one (its classes have the same
 * names); if it is, this plugin leaves Storage Spaces to it and says so in chat.
 */
public class mod_KirmodStorageSpaces extends BaseMod
{
    public static final KirPlugin PLUGIN = new KirPlugin("Storage Spaces", "1.2", "Storage Spaces, built into Kirmod: name areas that hold chests and see what is in them. I opens its own screen, or set it up here.", 54)
    {
        public String getParentCategory(String category)
        {
            return KirStorageNames.isGroup(category) ? KirStorageNames.TAB : null;
        }

        public String getCategoryDescription(String category)
        {
            return KirStorageNames.TAB.equals(category) ? "Storage Spaces: chest contents added up per named area." : null;
        }
    };

    /** False when the standalone mod_StorageSpaces zip is installed too and runs Storage Spaces itself. */
    private static boolean run = true;

    /** A chat warning about the standalone zip, shown once a world is loaded. */
    private static String warning;

    public String getVersion()
    {
        return PLUGIN.getVersion() + " for 1.3.2";
    }

    public String getPriorities()
    {
        return "after:mod_Kirmod";
    }

    public void load()
    {
        StorageSpacesMod.load(this);
        ModLoader.setInGameHook(this, true, false);
        mod_Kirmod.registerPlugin(PLUGIN);
    }

    public void modsLoaded()
    {
        run = !ModLoader.isModLoaded("mod_StorageSpaces");

        if (!run)
        {
            warning = "The Storage Spaces plugin (mod_KirmodStorageSpaces) already includes the standalone Storage Spaces mod: remove the mod_StorageSpaces zip from the mods folder.";
            System.out.println("[Storage Spaces plugin] " + warning);
            return;
        }

        // Registered once the settings are read (in load), so the rows start with their values.
        KirStorageSettings.register(PLUGIN);
        KirStorageSettings.registerHotkeys(PLUGIN);
    }

    public void addRenderer(Map renderers)
    {
        StorageSpacesMod.addRenderer(renderers);
    }

    public void keyboardEvent(KeyBinding key)
    {
        if (run)
        {
            StorageSpacesMod.keyboardEvent(key);
        }
    }

    public boolean onTickInGame(float partialTicks, Minecraft mc)
    {
        if (run)
        {
            StorageSpacesMod.onTickInGame(partialTicks, mc);
        }

        if (warning != null && mc.theWorld != null && mc.ingameGUI != null)
        {
            mc.ingameGUI.getChatGUI().printChatMessage("\u00a7c" + warning);
            warning = null;
        }

        return true;
    }
}

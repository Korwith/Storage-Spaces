package net.minecraft.src;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

/**
 * Part of the Storage Spaces plugin (was mod_StorageSpaces, the standalone mod): driven by
 * mod_KirmodStorageSpaces, which passes on its load, keys, ticks and renderers.
 *
 * Storage Spaces: name areas of the world that hold chests and see what is in them, added up per
 * area and over all of them. I opens the screen, O starts selecting an area; while selecting,
 * ] and [ grow and shrink the face looked at, ' ; . , move the box and Left Alt + wheel grows or
 * shrinks too (all rebindable in Options > Controls).
 *
 * In singleplayer the contents are read on the server side (live tile entities, and the save's
 * region files for unloaded chunks), so no chest has to be opened. On a server only what the player
 * opens can be seen, so containers are remembered as they are opened. No Minecraft class is edited.
 */
final class StorageSpacesMod
{
    public static final KeyBinding KEY_MENU = new KeyBinding("Storage Spaces", 23);
    public static final KeyBinding KEY_SELECT = new KeyBinding("Select Storage Space", 24);
    public static final KeyBinding KEY_GROW = new KeyBinding("Storage Grow Face", 27);
    public static final KeyBinding KEY_SHRINK = new KeyBinding("Storage Shrink Face", 26);
    public static final KeyBinding KEY_FORWARD = new KeyBinding("Storage Move Forward", 40);
    public static final KeyBinding KEY_BACK = new KeyBinding("Storage Move Back", 39);
    public static final KeyBinding KEY_UP = new KeyBinding("Storage Move Up", 52);
    public static final KeyBinding KEY_DOWN = new KeyBinding("Storage Move Down", 51);
    /** Held, the mouse wheel grows and shrinks the hot face (it has no action of its own). */
    public static final KeyBinding KEY_WHEEL = new KeyBinding("Storage Wheel Modifier", 56);
    /** In the order StorageSelection.key takes them. */
    private static final KeyBinding[] NUDGE_KEYS = new KeyBinding[] {KEY_GROW, KEY_SHRINK, KEY_FORWARD, KEY_BACK, KEY_UP, KEY_DOWN};

    private static EntityStorageMarker marker;
    private static int markerTicks;
    private static int frame;
    private static long lastSave;
    private static long lastPresence;

    /** The located item (-1: none), the space it was located in (-1: all), and the containers holding it. */
    public static int locateKey = -1;
    public static int locateSpace = -1;
    public static String locateRecord;
    public static long locateUntil;
    public static List locateFound;
    private static int locateVersion = -1;

    private static final Map statsCache = new HashMap();
    private static int statsVersion = -1;
    private static int statsContainers = -1;

    /** Server fallback: the container block last looked at, and the open container screen bound to it. */
    private static int[] lastTarget;
    private static long lastTargetTime;
    private static GuiScreen boundScreen;
    private static int[] boundPos;

    private StorageSpacesMod() {}

    /** Reads the settings and registers the keys, with owner as the mod ModLoader sends them to. */
    static void load(BaseMod owner)
    {
        StorageData.loadSettings();
        ModLoader.registerKey(owner, KEY_MENU, false);
        ModLoader.registerKey(owner, KEY_SELECT, false);

        for (int i = 0; i < NUDGE_KEYS.length; i++)
        {
            ModLoader.registerKey(owner, NUDGE_KEYS[i], true);
        }

        ModLoader.registerKey(owner, KEY_WHEEL, false);
    }

    static void addRenderer(Map renderers)
    {
        renderers.put(EntityStorageMarker.class, new RenderStorageMarker());
    }

    public static boolean isMarker(Entity entity)
    {
        return entity == marker;
    }

    static void keyboardEvent(KeyBinding key)
    {
        Minecraft mc = ModLoader.getMinecraftInstance();

        if (mc.currentScreen != null || mc.theWorld == null)
        {
            return;
        }

        if (key == KEY_MENU)
        {
            // The press that just closed the screen would open it again.
            if (System.currentTimeMillis() - GuiStorageSpaces.closedAt > 300L)
            {
                mc.displayGuiScreen(new GuiStorageSpaces(GuiStorageSpaces.lastTab));
            }
        }
        else if (key == KEY_SELECT)
        {
            if (StorageSelection.active)
            {
                StorageSelection.cancel(mc);
                mc.ingameGUI.getChatGUI().printChatMessage("\u00a7eStorage selection canceled");
            }
            else if (System.currentTimeMillis() - GuiStorageName.closedAt > 300L)
            {
                StorageSelection.start(mc, null);
            }
        }

        for (int i = 0; i < NUDGE_KEYS.length; i++)
        {
            if (key == NUDGE_KEYS[i])
            {
                StorageSelection.key(mc, i);
            }
        }
    }

    /** Spaces were added, moved or removed: tell the scanner and scan right away. */
    public static void spacesChanged()
    {
        int[][] jobs = new int[StorageData.spaces.size()][];

        for (int i = 0; i < jobs.length; i++)
        {
            StorageData.Space s = (StorageData.Space)StorageData.spaces.get(i);
            jobs[i] = new int[] {s.dim, s.x1, s.y1, s.z1, s.x2, s.y2, s.z2};
        }

        StorageScanner.jobs = jobs;
        StorageScanner.poke();
        statsVersion = -1;
    }

    /** Stats of a space (null: all spaces), recomputed only when something changed. */
    public static StorageData.Stats cachedStats(StorageData.Space s)
    {
        if (statsVersion != StorageData.version || statsContainers != StorageData.containers)
        {
            statsCache.clear();
            statsVersion = StorageData.version;
            statsContainers = StorageData.containers;
        }

        Integer k = Integer.valueOf(s != null ? s.id : -1);
        StorageData.Stats st = (StorageData.Stats)statsCache.get(k);

        if (st == null)
        {
            st = StorageData.stats(s);
            statsCache.put(k, st);
        }

        return st;
    }

    /** Highlights the containers holding an item, in one space (or all with null). */
    public static void locate(int key, StorageData.Space s)
    {
        locateKey = key;
        locateSpace = s != null ? s.id : -1;
        locateRecord = null;
        locateUntil = StorageData.locateSeconds > 0 ? System.currentTimeMillis() + StorageData.locateSeconds * 1000L : Long.MAX_VALUE;
        locateVersion = -1;
    }

    /** Highlights one container. */
    public static void locateContainer(StorageData.Record r)
    {
        locate(r.topKey() != -1 ? r.topKey() : 0, null);
        locateRecord = r.key();
    }

    public static void clearLocate()
    {
        locateKey = -1;
        locateRecord = null;
        locateFound = null;
    }

    private static void updateLocate()
    {
        if (locateKey == -1)
        {
            return;
        }

        if (System.currentTimeMillis() > locateUntil)
        {
            clearLocate();
            return;
        }

        if (locateVersion == StorageData.version)
        {
            return;
        }

        locateVersion = StorageData.version;
        List found = new ArrayList();
        StorageData.Space s = StorageData.getSpace(locateSpace);
        Iterator it = StorageData.records.values().iterator();

        while (it.hasNext())
        {
            StorageData.Record r = (StorageData.Record)it.next();

            if (locateRecord != null ? r.key().equals(locateRecord) : (r.count(locateKey) > 0 && StorageData.counts(r.kind) && (s != null ? s.contains(r) : StorageData.spaceOf(r) != null)))
            {
                found.add(r);
            }
        }

        locateFound = found;
    }

    static boolean onTickInGame(float partialTicks, Minecraft mc)
    {
        if (mc.theWorld == null || mc.thePlayer == null)
        {
            return true;
        }

        String key = StorageData.currentWorldKey(mc);

        if (!key.equals(StorageData.worldKey))
        {
            StorageSelection.cancel(mc);
            clearLocate();
            StorageData.switchWorld(key);
            spacesChanged();
        }

        StorageScanner.install(mc);
        StorageScanner.interval = StorageData.refresh * 1000L;
        StorageScanner.Result res;

        while ((res = (StorageScanner.Result)StorageScanner.results.poll()) != null)
        {
            StorageData.merge(res);
        }

        frame++;
        StorageSelection.tick(mc);
        long now = System.currentTimeMillis();

        if (!StorageScanner.live || mc.getIntegratedServer() == null)
        {
            watchOpenContainers(mc, now);
        }

        if (now - lastPresence > 1000L)
        {
            lastPresence = now;
            checkPresence(mc);
        }

        updateLocate();

        if (StorageData.dirty && now - lastSave > 30000L)
        {
            lastSave = now;
            StorageData.saveWorld();
        }

        tickMarker(mc);
        drawHud(mc);
        return true;
    }

    private static void tickMarker(Minecraft mc)
    {
        // The marker is lost with its world (dimension change, disconnect) and when its chunk unloads.
        if (marker != null && (marker.worldObj != mc.theWorld || marker.isDead || ++markerTicks % 40 == 0 && !mc.theWorld.loadedEntityList.contains(marker)))
        {
            marker.setDead();
            marker = null;
        }

        // WorldClient queues an entity spawned in a missing chunk, so wait until the player's chunk is there.
        if (marker == null && mc.theWorld.blockExists(MathHelper.floor_double(mc.thePlayer.posX), 64, MathHelper.floor_double(mc.thePlayer.posZ)))
        {
            marker = new EntityStorageMarker(mc.theWorld);
            marker.setPosition(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
            mc.theWorld.spawnEntityInWorld(marker);
            markerTicks = 0;
        }
    }

    /**
     * From the client's own chunks: containers that were broken are forgotten, and (without the
     * server side) containers never opened are counted as unseen.
     */
    private static void checkPresence(Minecraft mc)
    {
        int dim = mc.thePlayer.dimension;
        boolean serverSide = StorageScanner.live && mc.getIntegratedServer() != null;
        Set present = new HashSet();
        Set checkedChunks = new HashSet();
        boolean any = false;

        for (int i = 0; i < StorageData.spaces.size(); i++)
        {
            StorageData.Space s = (StorageData.Space)StorageData.spaces.get(i);

            if (s.dim != dim)
            {
                continue;
            }

            for (int cx = s.x1 >> 4; cx <= s.x2 >> 4; cx++)
            {
                for (int cz = s.z1 >> 4; cz <= s.z2 >> 4; cz++)
                {
                    Chunk chunk = mc.theWorld.getChunkFromChunkCoords(cx, cz);

                    if (!hasData(chunk) || !checkedChunks.add(Long.valueOf(StorageData.chunkKey(cx, cz))))
                    {
                        continue;
                    }

                    Iterator it = chunk.chunkTileEntityMap.values().iterator();

                    while (it.hasNext())
                    {
                        TileEntity te = (TileEntity)it.next();

                        if (te instanceof IInventory && !(te instanceof TileEntityEnderChest) && s.contains(dim, te.xCoord, te.yCoord, te.zCoord))
                        {
                            present.add(StorageData.key(dim, te.xCoord, te.yCoord, te.zCoord));
                        }
                    }
                }
            }
        }

        // Forget containers whose block is gone, in chunks the client has. The client makes a chunk's
        // tile entities only when something asks for them (rendering it, mostly), so a missing tile
        // entity says nothing: far or unrendered chests would drop out every second and come back
        // with the next scan. Only the block itself is checked.
        Iterator it = StorageData.records.values().iterator();

        while (it.hasNext())
        {
            StorageData.Record r = (StorageData.Record)it.next();

            if (r.dim == dim && checkedChunks.contains(Long.valueOf(StorageData.chunkKey(r.x >> 4, r.z >> 4))) && !isContainerBlock(mc.theWorld, r.x, r.y, r.z))
            {
                it.remove();
                any = true;
            }
        }

        Set unseen = new HashSet();

        if (!serverSide)
        {
            it = present.iterator();

            while (it.hasNext())
            {
                String k = (String)it.next();

                if (!StorageData.records.containsKey(k))
                {
                    unseen.add(k);
                }
            }
        }

        // Unseen containers of other dimensions are kept as they were, and so are this dimension's
        // while their block is still there (their tile entity may not have been made this time).
        it = StorageData.unseen.iterator();

        while (it.hasNext())
        {
            String k = (String)it.next();

            if (!k.startsWith(dim + " "))
            {
                unseen.add(k);
                continue;
            }

            String[] p = k.split(" ");
            int x = Integer.parseInt(p[1]);
            int y = Integer.parseInt(p[2]);
            int z = Integer.parseInt(p[3]);

            if (!serverSide && !StorageData.records.containsKey(k) && StorageData.anyContains(dim, x, y, z)
                    && (!checkedChunks.contains(Long.valueOf(StorageData.chunkKey(x >> 4, z >> 4))) || isContainerBlock(mc.theWorld, x, y, z)))
            {
                unseen.add(k);
            }
        }

        if (!unseen.equals(StorageData.unseen))
        {
            StorageData.unseen.clear();
            StorageData.unseen.addAll(unseen);
            StorageData.version++;
        }

        if (any)
        {
            StorageData.changed();
        }
    }

    /** Whether the block there makes a tile entity (a container, if one was recorded there). */
    private static boolean isContainerBlock(World world, int x, int y, int z)
    {
        int id = world.getBlockId(x, y, z);
        return id > 0 && id < Block.blocksList.length && Block.blocksList[id] != null && Block.blocksList[id].hasTileEntity();
    }

    /** A client chunk that came with block data (not one only announced by the server yet). */
    private static boolean hasData(Chunk chunk)
    {
        if (chunk == null || chunk.isEmpty())
        {
            return false;
        }

        ExtendedBlockStorage[] parts = chunk.getBlockStorageArray();

        for (int i = 0; i < parts.length; i++)
        {
            if (parts[i] != null)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Without the server side, a container's contents are only known while its screen is open: the
     * screen is tied to the container block the player last looked at, and its slots are recorded.
     */
    private static void watchOpenContainers(Minecraft mc, long now)
    {
        if (mc.currentScreen == null)
        {
            boundScreen = null;
            MovingObjectPosition m = mc.objectMouseOver;

            if (m != null && m.typeOfHit == EnumMovingObjectType.TILE && mc.theWorld.getBlockTileEntity(m.blockX, m.blockY, m.blockZ) instanceof IInventory)
            {
                lastTarget = new int[] {m.blockX, m.blockY, m.blockZ};
                lastTargetTime = now;
            }

            return;
        }

        if (!(mc.currentScreen instanceof GuiContainer) || mc.currentScreen instanceof GuiInventory || mc.currentScreen instanceof GuiContainerCreative)
        {
            return;
        }

        if (mc.currentScreen != boundScreen)
        {
            boundScreen = mc.currentScreen;
            boundPos = lastTarget != null && now - lastTargetTime < 1500L ? lastTarget : null;
        }

        if (boundPos == null || frame % 10 != 0)
        {
            return;
        }

        Container c = ((GuiContainer)mc.currentScreen).inventorySlots;
        List stacks = new ArrayList();

        for (int i = 0; i < c.inventorySlots.size(); i++)
        {
            Slot slot = (Slot)c.inventorySlots.get(i);

            if (slot.inventory != mc.thePlayer.inventory)
            {
                ItemStack s = slot.getStack();
                stacks.add(s != null ? s.copy() : null);
            }
        }

        if (stacks.isEmpty())
        {
            return;
        }

        int dim = mc.thePlayer.dimension;
        int x = boundPos[0];
        int y = boundPos[1];
        int z = boundPos[2];
        TileEntity te = mc.theWorld.getBlockTileEntity(x, y, z);
        int kind = te != null ? StorageScanner.kindOf(te) : 4;
        List records = new ArrayList();

        if (kind == 0 && stacks.size() == 54)
        {
            // A double chest: the half with the lower x (or z) holds the first 27 slots, like BlockChest.
            int id = mc.theWorld.getBlockId(x, y, z);
            int[] other = mc.theWorld.getBlockId(x - 1, y, z) == id ? new int[] {x - 1, y, z} : (mc.theWorld.getBlockId(x + 1, y, z) == id ? new int[] {x + 1, y, z} : (mc.theWorld.getBlockId(x, y, z - 1) == id ? new int[] {x, y, z - 1} : new int[] {x, y, z + 1}));
            boolean thisFirst = other[0] > x || other[2] > z;
            int[] first = thisFirst ? boundPos : other;
            int[] second = thisFirst ? other : boundPos;
            records.add(record(dim, first, kind, stacks.subList(0, 27), now));
            records.add(record(dim, second, kind, stacks.subList(27, 54), now));
        }
        else
        {
            records.add(record(dim, boundPos, kind, stacks, now));
        }

        StorageScanner.Result res = new StorageScanner.Result();
        res.dim = dim;
        res.area = new int[] {0, 0, 0, -1, -1, -1};
        res.time = now;
        res.found = records;
        StorageData.merge(res);
    }

    private static StorageData.Record record(int dim, int[] pos, int kind, List stacks, long now)
    {
        StorageData.Record r = new StorageData.Record();
        r.dim = dim;
        r.x = pos[0];
        r.y = pos[1];
        r.z = pos[2];
        r.kind = kind;
        r.slots = stacks.size();
        r.seen = now;
        r.setStacks((ItemStack[])stacks.toArray(new ItemStack[stacks.size()]));
        return r;
    }

    /**
     * The selection and locate HUDs. This runs every frame from the in-game tick hook, after the frame is
     * drawn, so whatever GL state it leaves is where the next frame starts. It used to enable texturing
     * every frame even with nothing to show, while the lightmap's texture unit could still be the active
     * one: the lightmap then stayed on into the next frame and tinted the sky and fog, which flickered
     * from frame to frame (most visibly in the Nether). Now it only draws when a HUD is up, on the
     * default texture unit, and puts the enable/texture state back afterwards.
     */
    private static void drawHud(Minecraft mc)
    {
        boolean selection = StorageSelection.active && StorageData.selectHud && mc.currentScreen == null;
        boolean locate = locateKey != -1 && StorageData.locateHud && mc.currentScreen == null && locateFound != null;

        if (mc.gameSettings.hideGUI || !selection && !locate)
        {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT);

        try
        {
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            drawHudContents(mc, locate);
        }
        finally
        {
            GL11.glPopAttrib();
        }
    }

    private static void drawHudContents(Minecraft mc, boolean locate)
    {
        ScaledResolution res = new ScaledResolution(mc.gameSettings, mc.displayWidth, mc.displayHeight);
        mc.entityRenderer.setupOverlayRendering();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        StorageSelection.drawHud(mc, res);

        if (!locate)
        {
            return;
        }

        // "Cobblestone: 4 containers, nearest 12 m ahead-left, 3 down"
        StorageData.Record near = null;
        double best = Double.MAX_VALUE;
        long total = 0L;

        for (int i = 0; i < locateFound.size(); i++)
        {
            StorageData.Record r = (StorageData.Record)locateFound.get(i);

            if (r.dim != mc.thePlayer.dimension)
            {
                continue;
            }

            total += r.count(locateKey);
            double d = mc.thePlayer.getDistanceSq(r.x + 0.5D, r.y + 0.5D, r.z + 0.5D);

            if (d < best)
            {
                best = d;
                near = r;
            }
        }

        String s;

        if (near == null)
        {
            s = "\u00a7a" + (locateRecord != null ? "Container" : StorageData.itemName(locateKey)) + "\u00a7r: none in this dimension";
        }
        else
        {
            double dx = near.x + 0.5D - mc.thePlayer.posX;
            double dz = near.z + 0.5D - mc.thePlayer.posZ;
            int dy = near.y - MathHelper.floor_double(mc.thePlayer.boundingBox.minY);
            String where = (int)Math.sqrt(best) + " m " + direction(mc.thePlayer.rotationYaw, dx, dz) + (dy != 0 ? ", " + Math.abs(dy) + (dy > 0 ? " up" : " down") : "");

            if (locateRecord != null)
            {
                s = "\u00a7a" + StorageData.KIND_NAMES[Math.min(near.kind, 4)] + "\u00a7r at " + near.x + ", " + near.y + ", " + near.z + "  \u00a77" + where;
            }
            else
            {
                s = "\u00a7a" + StorageData.itemName(locateKey) + "\u00a7r " + StorageData.exact(total) + " in " + locateFound.size() + " container" + (locateFound.size() == 1 ? "" : "s") + "  \u00a77nearest " + where;
            }
        }

        FontRenderer f = mc.fontRenderer;
        int w = f.getStringWidth(s);
        int x = (res.getScaledWidth() - w) / 2;
        int y = res.getScaledHeight() - 72;
        Gui.drawRect(x - 3, y - 2, x + w + 3, y + 10, 0x80000000);
        f.drawStringWithShadow(s, x, y, 0xFFFFFF);
    }

    /** Where (dx, dz) is for someone facing yaw: ahead, behind, left, right, or between. */
    private static String direction(float yaw, double dx, double dz)
    {
        // Minecraft's yaw 0 faces +z (south), 90 faces -x (west).
        double angle = Math.toDegrees(Math.atan2(-dx, dz)) - yaw;
        angle = ((angle % 360.0D) + 540.0D) % 360.0D - 180.0D;
        String[] names = new String[] {"ahead", "ahead-right", "right", "behind-right", "behind", "behind-left", "left", "ahead-left"};
        int i = (int)Math.floor((angle + 22.5D + 360.0D) / 45.0D) % 8;
        return names[i];
    }
}

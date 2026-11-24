package net.minecraft.src;

import java.io.DataInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/**
 * Reads container contents on the server side of a singleplayer world, so nothing has to be opened:
 * <ul>
 * <li>loaded chunks: the live tile entities, read on the server thread (the scanner is one of the
 * server's tickables, so it runs between world ticks and never races the game);</li>
 * <li>unloaded chunks: the chunk NBT in the save's region files, read on a background thread.</li>
 * </ul>
 * Results go to a queue that the client thread merges into StorageData.
 */
public class StorageScanner implements IUpdatePlayerListBox
{
    /** What to scan: {dim, x1, y1, z1, x2, y2, z2} per space. */
    public static volatile int[][] jobs = new int[0][];
    public static volatile long interval = 2000L;
    public static final ConcurrentLinkedQueue results = new ConcurrentLinkedQueue();

    private static MinecraftServer installedOn;
    private static StorageScanner instance;
    private static FileReader reader;
    /** False when the server's tickable list couldn't be reached; the region files are still read. */
    public static boolean live;

    private long lastScan;

    /** The outcome of scanning some chunks of one area. */
    public static class Result
    {
        int dim;
        int[] area;
        List chunks = new ArrayList();
        List found = new ArrayList();
        /** Containers known before this time and missing from the scanned chunks are gone. */
        long time;
        boolean fromSave;
    }

    /** Hooks into the integrated server (once per server) and starts the region file reader. */
    public static void install(Minecraft mc)
    {
        MinecraftServer server = mc.getIntegratedServer();

        if (server == installedOn)
        {
            return;
        }

        stop();
        installedOn = server;

        if (server == null)
        {
            return;
        }

        instance = new StorageScanner();
        live = false;

        try
        {
            Field f = findField(MinecraftServer.class, "playersOnline", "p");
            f.setAccessible(true);
            List old = (List)f.get(server);
            // The server iterates this list every tick; a copy-on-write list swapped in whole can't
            // throw a ConcurrentModificationException at it.
            CopyOnWriteArrayList list = new CopyOnWriteArrayList(old);
            list.add(instance);
            f.set(server, list);
            live = true;
        }
        catch (Throwable t)
        {
            System.out.println("Storage Spaces: can't hook the server tick, loaded chunks will only update when chests are opened (" + t + ")");
        }

        reader = new FileReader(new File(Minecraft.getMinecraftDir(), "saves/" + server.getFolderName()), !live);
        reader.start();
    }

    public static void stop()
    {
        if (reader != null)
        {
            reader.running = false;
            reader.interrupt();
            reader = null;
        }

        if (instance != null)
        {
            instance.dead = true;
            instance = null;
        }

        installedOn = null;
        results.clear();
    }

    /** Asks for a scan as soon as possible (after a space is added or changed). */
    public static void poke()
    {
        if (instance != null)
        {
            instance.lastScan = 0L;
        }

        if (reader != null)
        {
            reader.lastRun = 0L;
            reader.interrupt();
        }
    }

    private static Field findField(Class c, String mcp, String obf) throws NoSuchFieldException
    {
        try
        {
            return c.getDeclaredField(mcp);
        }
        catch (NoSuchFieldException e)
        {
            return c.getDeclaredField(obf);
        }
    }

    private volatile boolean dead;

    /** Server thread, once per tick after every world has ticked. */
    public void func_73660_a()
    {
        long now = System.currentTimeMillis();

        if (dead || now - lastScan < interval)
        {
            return;
        }

        lastScan = now;
        int[][] js = jobs;
        List unloaded = new ArrayList();

        for (int j = 0; j < js.length; j++)
        {
            int[] job = js[j];

            try
            {
                scanLoaded(job, now, unloaded);
            }
            catch (Throwable t)
            {
                // A modded tile entity that throws shouldn't take the server down.
                System.out.println("Storage Spaces: scan failed: " + t);
            }
        }

        if (reader != null)
        {
            reader.unloaded = unloaded;
        }
    }

    private void scanLoaded(int[] job, long now, List unloaded)
    {
        WorldServer world = installedOn != null ? installedOn.worldServerForDimension(job[0]) : null;

        if (world == null || world.provider.worldType != job[0])
        {
            return;
        }

        Result res = new Result();
        res.dim = job[0];
        res.area = new int[] {job[1], job[2], job[3], job[4], job[5], job[6]};
        res.time = now;
        List missing = new ArrayList();
        IChunkProvider provider = world.getChunkProvider();

        for (int cx = job[1] >> 4; cx <= job[4] >> 4; cx++)
        {
            for (int cz = job[3] >> 4; cz <= job[6] >> 4; cz++)
            {
                if (!provider.chunkExists(cx, cz))
                {
                    missing.add(new int[] {cx, cz});
                    continue;
                }

                Chunk chunk = world.getChunkFromChunkCoords(cx, cz);
                res.chunks.add(Long.valueOf(StorageData.chunkKey(cx, cz)));
                Iterator it = chunk.chunkTileEntityMap.values().iterator();

                while (it.hasNext())
                {
                    TileEntity te = (TileEntity)it.next();

                    if (!(te instanceof IInventory) || te.isInvalid() || te instanceof TileEntityEnderChest)
                    {
                        continue;
                    }

                    if (te.xCoord < job[1] || te.yCoord < job[2] || te.zCoord < job[3] || te.xCoord > job[4] || te.yCoord > job[5] || te.zCoord > job[6])
                    {
                        continue;
                    }

                    IInventory inv = (IInventory)te;
                    int n = inv.getSizeInventory();
                    ItemStack[] stacks = new ItemStack[n];

                    for (int i = 0; i < n; i++)
                    {
                        ItemStack s = inv.getStackInSlot(i);
                        stacks[i] = s != null ? s.copy() : null;
                    }

                    StorageData.Record r = new StorageData.Record();
                    r.dim = job[0];
                    r.x = te.xCoord;
                    r.y = te.yCoord;
                    r.z = te.zCoord;
                    r.kind = kindOf(te);
                    r.slots = n;
                    r.seen = now;
                    r.setStacks(stacks);
                    res.found.add(r);
                }
            }
        }

        results.add(res);

        if (!missing.isEmpty())
        {
            unloaded.add(new Object[] {job, missing});
        }
    }

    public static int kindOf(TileEntity te)
    {
        return te instanceof TileEntityChest ? 0 : (te instanceof TileEntityDispenser ? 1 : (te instanceof TileEntityFurnace ? 2 : (te instanceof TileEntityBrewingStand ? 3 : 4)));
    }

    /**
     * Reads containers of unloaded chunks from the save's region files, every 20 seconds (sooner
     * when poked). Only reads files: no server state is touched from this thread.
     */
    static class FileReader extends Thread
    {
        private final File saveDir;
        /** Without the server hook nothing says which chunks are loaded, so every chunk is read. */
        private final boolean readAll;
        volatile boolean running = true;
        volatile long lastRun;
        /** From the server thread: {job, List of int[]{cx, cz}} for chunks that aren't loaded. */
        volatile List unloaded;

        FileReader(File saveDir, boolean readAll)
        {
            super("Storage Spaces region reader");
            this.saveDir = saveDir;
            this.readAll = readAll;
            setDaemon(true);
            setPriority(Thread.MIN_PRIORITY);
        }

        public void run()
        {
            while (running)
            {
                try
                {
                    Thread.sleep(lastRun == 0L ? 1500L : 1000L);
                }
                catch (InterruptedException e)
                {
                    // Poked or stopped.
                }

                if (!running || System.currentTimeMillis() - lastRun < 20000L)
                {
                    continue;
                }

                lastRun = System.currentTimeMillis();
                List work = readAll ? everyChunk() : unloaded;

                if (work == null)
                {
                    lastRun = 0L;
                    continue;
                }

                for (int i = 0; i < work.size() && running; i++)
                {
                    Object[] w = (Object[])work.get(i);
                    readJob((int[])w[0], (List)w[1]);
                }
            }
        }

        private List everyChunk()
        {
            List work = new ArrayList();
            int[][] js = jobs;

            for (int j = 0; j < js.length; j++)
            {
                List chunks = new ArrayList();

                for (int cx = js[j][1] >> 4; cx <= js[j][4] >> 4; cx++)
                {
                    for (int cz = js[j][3] >> 4; cz <= js[j][6] >> 4; cz++)
                    {
                        chunks.add(new int[] {cx, cz});
                    }
                }

                work.add(new Object[] {js[j], chunks});
            }

            return work;
        }

        void readJob(int[] job, List chunks)
        {
            File dir = job[0] == 0 ? saveDir : new File(saveDir, "DIM" + job[0]);
            Result res = new Result();
            res.dim = job[0];
            res.area = new int[] {job[1], job[2], job[3], job[4], job[5], job[6]};
            res.fromSave = true;
            res.time = Long.MAX_VALUE;

            for (int i = 0; i < chunks.size(); i++)
            {
                int[] c = (int[])chunks.get(i);
                File region = new File(dir, "region/r." + (c[0] >> 5) + "." + (c[1] >> 5) + ".mca");

                // RegionFileCache would create a missing file.
                if (!region.exists())
                {
                    continue;
                }

                try
                {
                    DataInputStream in = RegionFileCache.getChunkInputStream(dir, c[0], c[1]);

                    if (in == null)
                    {
                        continue;
                    }

                    NBTTagCompound root = CompressedStreamTools.read(in);
                    in.close();
                    long time = region.lastModified();
                    res.time = Math.min(res.time, time);
                    res.chunks.add(Long.valueOf(StorageData.chunkKey(c[0], c[1])));
                    readChunk(root.getCompoundTag("Level"), job, time, res.found);
                }
                catch (Exception e)
                {
                    // A chunk being written or a damaged region; try again next round.
                }

                Thread.yield();
            }

            if (!res.chunks.isEmpty())
            {
                results.add(res);
            }
        }

        private static void readChunk(NBTTagCompound level, int[] job, long time, List found)
        {
            NBTTagList tes = level.getTagList("TileEntities");

            for (int i = 0; i < tes.tagCount(); i++)
            {
                NBTTagCompound te = (NBTTagCompound)tes.tagAt(i);

                if (!te.hasKey("Items"))
                {
                    continue;
                }

                String id = te.getString("id");

                if (id.equals("EnderChest"))
                {
                    continue;
                }

                int x = te.getInteger("x");
                int y = te.getInteger("y");
                int z = te.getInteger("z");

                if (x < job[1] || y < job[2] || z < job[3] || x > job[4] || y > job[5] || z > job[6])
                {
                    continue;
                }

                int kind = id.equals("Chest") ? 0 : (id.equals("Trap") ? 1 : (id.equals("Furnace") ? 2 : (id.equals("Cauldron") ? 3 : 4)));
                int slots = kind == 0 ? 27 : (kind == 1 ? 9 : (kind == 2 ? 3 : (kind == 3 ? 4 : 0)));
                NBTTagList items = te.getTagList("Items");
                List stacks = new ArrayList();

                for (int k = 0; k < items.tagCount(); k++)
                {
                    NBTTagCompound it = (NBTTagCompound)items.tagAt(k);
                    int slot = it.getByte("Slot") & 255;
                    slots = Math.max(slots, slot + 1);
                    short itemId = it.getShort("id");

                    if (itemId > 0 && itemId < Item.itemsList.length && Item.itemsList[itemId] != null)
                    {
                        stacks.add(new ItemStack(itemId, it.getByte("Count"), it.getShort("Damage")));
                    }
                }

                StorageData.Record r = new StorageData.Record();
                r.dim = job[0];
                r.x = x;
                r.y = y;
                r.z = z;
                r.kind = kind;
                r.slots = slots;
                r.seen = time;
                r.fromSave = true;
                r.setStacks((ItemStack[])stacks.toArray(new ItemStack[stacks.size()]));
                found.add(r);
            }
        }
    }
}

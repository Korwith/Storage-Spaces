package net.minecraft.src;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;

/**
 * Storage Spaces' data: the settings, the named areas of the current world or server, and the last
 * known contents of every container in them (kept for chests in unloaded chunks, and on servers
 * for chests that were opened). Each world or server has its own file in config/StorageSpaces/.
 */
public class StorageData
{
    public static final String[] KIND_NAMES = new String[] {"Chest", "Dispenser", "Furnace", "Brewing Stand", "Container"};
    public static final String[] CONTAINER_NAMES = new String[] {"Chests", "+ Dispensers", "All"};
    public static final String[] OUTLINE_NAMES = new String[] {"Off", "Selecting", "Always"};
    public static final String[] NUMBER_NAMES = new String[] {"Exact", "Compact", "Stacks"};
    public static final String[] SORT_NAMES = new String[] {"Count", "Name", "ID", "Change"};
    /** Outline colours a new space cycles through. */
    public static final int[] COLORS = new int[] {0x55FFFF, 0xFFAA00, 0x55FF55, 0xFF55FF, 0xFFFF55, 0x5555FF, 0xFF5555, 0xFFFFFF};
    public static final int MAX_EXTENT = 1024;

    // ---------------------------------------------------------------- settings

    /** 0 chests only, 1 chests and dispensers, 2 every container (furnaces, brewing stands, modded). */
    public static int containers = 0;
    /** 0 never, 1 while selecting or locating, 2 always. */
    public static int outlines = 1;
    public static boolean labels = true;
    public static boolean seeThrough = false;
    /** Seconds between scans of loaded chunks in singleplayer. */
    public static int refresh = 2;
    public static int numbers = 0;
    public static int sort = 0;
    /** Seconds a located item stays highlighted; 0 until cleared. */
    public static int locateSeconds = 60;
    public static boolean locateCloses = true;
    public static boolean locateHud = true;
    public static boolean deltas = true;
    /** How far away a corner can be picked in selection mode. */
    public static int reach = 64;
    public static boolean selectHud = true;
    /** Blocks a selection face or box moves per key press (without Shift). */
    public static int nudgeStep = 1;
    /** Pie charts measured in slots get an "Empty slots" slice. */
    public static boolean showEmpty = true;
    /** Total's chart: 0 by item, 1 by space. */
    public static int chartBy = 1;
    /** Charts: 0 items, 1 slots. */
    public static int chartMeasure = 0;

    // ---------------------------------------------------------------- world data

    public static final List spaces = new ArrayList();
    /** Every known container, keyed by "dim x y z". */
    public static final Map records = new HashMap();
    /** Containers seen on the client that were never opened (servers only), by the same key. */
    public static final Set unseen = new HashSet();
    /** Bumped whenever the records or spaces change, so cached stats know to recompute. */
    public static int version;
    public static String worldKey;
    public static boolean dirty;
    private static int nextId = 1;

    public static class Space
    {
        public int id;
        public String name;
        public int dim;
        public int x1;
        public int y1;
        public int z1;
        public int x2;
        public int y2;
        public int z2;
        public int color;
        public boolean outline = true;
        /** Item key for the tab icon, or -1 for the most common item. */
        public int icon = -1;
        public long created;
        public long baselineTime;
        public Map baseline;

        public void setBounds(int ax, int ay, int az, int bx, int by, int bz)
        {
            x1 = Math.min(ax, bx);
            y1 = Math.min(ay, by);
            z1 = Math.min(az, bz);
            x2 = Math.max(ax, bx);
            y2 = Math.max(ay, by);
            z2 = Math.max(az, bz);
        }

        public boolean contains(int dim, int x, int y, int z)
        {
            return dim == this.dim && x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
        }

        public boolean contains(Record r)
        {
            return contains(r.dim, r.x, r.y, r.z);
        }

        public int sizeX() { return x2 - x1 + 1; }
        public int sizeY() { return y2 - y1 + 1; }
        public int sizeZ() { return z2 - z1 + 1; }
    }

    /** One container's contents when it was last seen. */
    public static class Record
    {
        public int dim;
        public int x;
        public int y;
        public int z;
        public int kind;
        public int slots;
        public int used;
        /** Item keys and their counts in this container. */
        public int[] keys = new int[0];
        public int[] counts = new int[0];
        public long seen;
        /** Read from a region file rather than a live chunk (not saved; only steers merging). */
        public transient boolean fromSave;

        public String key()
        {
            return StorageData.key(dim, x, y, z);
        }

        public int total()
        {
            int n = 0;

            for (int i = 0; i < counts.length; i++)
            {
                n += counts[i];
            }

            return n;
        }

        public int count(int key)
        {
            for (int i = 0; i < keys.length; i++)
            {
                if (keys[i] == key)
                {
                    return counts[i];
                }
            }

            return 0;
        }

        /** The most common item, for the container's icon. */
        public int topKey()
        {
            int best = -1;
            int bestCount = 0;

            for (int i = 0; i < keys.length; i++)
            {
                if (counts[i] > bestCount)
                {
                    best = keys[i];
                    bestCount = counts[i];
                }
            }

            return best;
        }

        /** Fills keys/counts/used from a list of stacks (nulls are empty slots). */
        public void setStacks(ItemStack[] stacks)
        {
            Map map = new HashMap();
            List order = new ArrayList();
            used = 0;

            for (int i = 0; i < stacks.length; i++)
            {
                ItemStack s = stacks[i];

                if (s == null || s.stackSize <= 0)
                {
                    continue;
                }

                used++;
                Integer k = Integer.valueOf(itemKey(s));
                Integer old = (Integer)map.get(k);

                if (old == null)
                {
                    order.add(k);
                }

                map.put(k, Integer.valueOf((old != null ? old.intValue() : 0) + s.stackSize));
            }

            keys = new int[order.size()];
            counts = new int[order.size()];

            for (int i = 0; i < keys.length; i++)
            {
                keys[i] = ((Integer)order.get(i)).intValue();
                counts[i] = ((Integer)map.get(order.get(i))).intValue();
            }
        }

        public boolean sameContents(Record o)
        {
            if (o == null || o.kind != kind || o.slots != slots || o.used != used || o.keys.length != keys.length)
            {
                return false;
            }

            for (int i = 0; i < keys.length; i++)
            {
                if (o.keys[i] != keys[i] || o.counts[i] != counts[i])
                {
                    return false;
                }
            }

            return true;
        }
    }

    /** Totals over a set of containers. */
    public static class Stats
    {
        /** Item key -> long[] {count, containers}. */
        public final Map items = new HashMap();
        public final List records = new ArrayList();
        public long total;
        public int slots;
        public int used;
        public int[] kinds = new int[KIND_NAMES.length];
        public int cached;
        public int unseen;
        public long oldest = Long.MAX_VALUE;

        public long count(int key)
        {
            long[] v = (long[])items.get(Integer.valueOf(key));
            return v != null ? v[0] : 0L;
        }

        public int containersWith(int key)
        {
            long[] v = (long[])items.get(Integer.valueOf(key));
            return v != null ? (int)v[1] : 0;
        }

        /** The most common item, for a tab icon. */
        public int topKey()
        {
            int best = -1;
            long bestCount = 0L;
            Iterator it = items.entrySet().iterator();

            while (it.hasNext())
            {
                Map.Entry e = (Map.Entry)it.next();
                long c = ((long[])e.getValue())[0];

                if (c > bestCount)
                {
                    bestCount = c;
                    best = ((Integer)e.getKey()).intValue();
                }
            }

            return best;
        }
    }

    // ---------------------------------------------------------------- keys and names

    public static String key(int dim, int x, int y, int z)
    {
        return dim + " " + x + " " + y + " " + z;
    }

    /**
     * id and damage in one int. Tools and armour keep their wear in the damage, so those are counted
     * together; wool, dyes, logs and the like keep their kind.
     */
    public static int itemKey(ItemStack s)
    {
        int damage = s.getItemDamage();
        Item item = s.itemID >= 0 && s.itemID < Item.itemsList.length ? Item.itemsList[s.itemID] : null;

        if (item != null && item.isDamageable() && !item.getHasSubtypes())
        {
            damage = 0;
        }

        return (s.itemID << 16) | (damage & 0xFFFF);
    }

    public static int keyId(int key)
    {
        return key >>> 16;
    }

    public static int keyDamage(int key)
    {
        return key & 0xFFFF;
    }

    public static ItemStack stack(int key)
    {
        int id = keyId(key);

        if (id <= 0 || id >= Item.itemsList.length || Item.itemsList[id] == null)
        {
            return null;
        }

        return new ItemStack(id, 1, keyDamage(key));
    }

    private static final Map nameCache = new HashMap();

    public static String itemName(int key)
    {
        Integer k = Integer.valueOf(key);
        String name = (String)nameCache.get(k);

        if (name == null)
        {
            ItemStack s = stack(key);
            name = null;

            if (s != null)
            {
                try
                {
                    name = s.getItem().getItemDisplayName(s);
                }
                catch (Exception e)
                {
                    // Falls through to the id.
                }
            }

            if (name == null || name.trim().length() == 0)
            {
                name = "Item " + keyId(key) + (keyDamage(key) != 0 ? ":" + keyDamage(key) : "");
            }

            name = name.trim();
            nameCache.put(k, name);
        }

        return name;
    }

    public static String idString(int key)
    {
        return keyId(key) + (keyDamage(key) != 0 ? ":" + keyDamage(key) : "");
    }

    public static int maxStack(int key)
    {
        ItemStack s = stack(key);
        return s != null ? Math.max(1, s.getMaxStackSize()) : 64;
    }

    private static final DecimalFormat GROUPED = new DecimalFormat("#,##0");
    private static final DecimalFormat ONE_DECIMAL = new DecimalFormat("0.0");

    public static String exact(long n)
    {
        return GROUPED.format(n);
    }

    /** 999, 1.2k, 12k, 1.2M: fits under an item slot. */
    public static String compact(long n)
    {
        long a = Math.abs(n);

        if (a < 1000L)
        {
            return String.valueOf(n);
        }

        if (a < 10000L)
        {
            return ONE_DECIMAL.format(n / 1000.0D) + "k";
        }

        if (a < 1000000L)
        {
            return n / 1000L + "k";
        }

        if (a < 10000000L)
        {
            return ONE_DECIMAL.format(n / 1000000.0D) + "M";
        }

        return n / 1000000L + "M";
    }

    /** A count as the Numbers setting says. */
    public static String number(long n, int key)
    {
        if (numbers == 1)
        {
            return compact(n);
        }

        if (numbers == 2)
        {
            return stacks(n, key < 0 ? 64 : maxStack(key));
        }

        return exact(n);
    }

    public static String stacks(long n, int perStack)
    {
        if (n < perStack)
        {
            return exact(n);
        }

        long s = n / perStack;
        long r = n % perStack;
        return exact(s) + " st" + (r > 0 ? " + " + r : "");
    }

    public static String signed(long n)
    {
        return (n > 0 ? "+" : "") + (numbers == 1 ? compact(n) : exact(n));
    }

    public static String ago(long time)
    {
        long s = (System.currentTimeMillis() - time) / 1000L;

        if (s < 60L)
        {
            return s + "s ago";
        }

        if (s < 3600L)
        {
            return s / 60L + "m ago";
        }

        if (s < 86400L)
        {
            return s / 3600L + "h ago";
        }

        return s / 86400L + "d ago";
    }

    public static String dimName(int dim)
    {
        return dim == 0 ? "Overworld" : (dim == -1 ? "Nether" : (dim == 1 ? "The End" : "Dimension " + dim));
    }

    public static boolean counts(int kind)
    {
        return kind == 0 || containers >= 2 || containers == 1 && kind == 1;
    }

    /** Live: seen in the last scans. Otherwise the numbers are from when the chunk was last loaded or the chest opened. */
    public static boolean isLive(Record r)
    {
        return System.currentTimeMillis() - r.seen < Math.max(5000L, refresh * 3000L);
    }

    // ---------------------------------------------------------------- spaces and stats

    public static Space addSpace(String name, int dim, int[] a, int[] b)
    {
        Space s = new Space();
        s.id = nextId++;
        s.name = name;
        s.dim = dim;
        s.setBounds(a[0], a[1], a[2], b[0], b[1], b[2]);
        s.color = COLORS[(s.id - 1) % COLORS.length];
        s.created = System.currentTimeMillis();
        spaces.add(s);
        changed();
        return s;
    }

    public static Space getSpace(int id)
    {
        for (int i = 0; i < spaces.size(); i++)
        {
            Space s = (Space)spaces.get(i);

            if (s.id == id)
            {
                return s;
            }
        }

        return null;
    }

    public static void removeSpace(Space s)
    {
        spaces.remove(s);

        // Forget containers no other space covers.
        Iterator it = records.values().iterator();

        while (it.hasNext())
        {
            Record r = (Record)it.next();

            if (s.contains(r) && spaceOf(r) == null)
            {
                it.remove();
            }
        }

        changed();
    }

    public static Space spaceOf(Record r)
    {
        for (int i = 0; i < spaces.size(); i++)
        {
            Space s = (Space)spaces.get(i);

            if (s.contains(r))
            {
                return s;
            }
        }

        return null;
    }

    public static String uniqueName(String base)
    {
        String name = base;

        for (int n = 2; nameTaken(name); n++)
        {
            name = base + " " + n;
        }

        return name;
    }

    private static boolean nameTaken(String name)
    {
        for (int i = 0; i < spaces.size(); i++)
        {
            if (((Space)spaces.get(i)).name.equalsIgnoreCase(name))
            {
                return true;
            }
        }

        return false;
    }

    public static void changed()
    {
        version++;
        dirty = true;
    }

    /** Totals for one space, or for every space (null), counting each container once. */
    public static Stats stats(Space only)
    {
        Stats st = new Stats();
        Iterator it = records.values().iterator();

        while (it.hasNext())
        {
            Record r = (Record)it.next();

            if (!counts(r.kind) || (only != null ? !only.contains(r) : spaceOf(r) == null))
            {
                continue;
            }

            st.records.add(r);
            st.slots += r.slots;
            st.used += r.used;
            st.kinds[Math.min(r.kind, KIND_NAMES.length - 1)]++;
            st.oldest = Math.min(st.oldest, r.seen);

            if (!isLive(r))
            {
                st.cached++;
            }

            for (int i = 0; i < r.keys.length; i++)
            {
                Integer k = Integer.valueOf(r.keys[i]);
                long[] v = (long[])st.items.get(k);

                if (v == null)
                {
                    v = new long[2];
                    st.items.put(k, v);
                }

                v[0] += r.counts[i];
                v[1]++;
                st.total += r.counts[i];
            }
        }

        it = unseen.iterator();

        while (it.hasNext())
        {
            String[] p = ((String)it.next()).split(" ");
            int dim = Integer.parseInt(p[0]);
            int x = Integer.parseInt(p[1]);
            int y = Integer.parseInt(p[2]);
            int z = Integer.parseInt(p[3]);

            if (only != null ? only.contains(dim, x, y, z) : anyContains(dim, x, y, z))
            {
                st.unseen++;
            }
        }

        return st;
    }

    public static boolean anyContains(int dim, int x, int y, int z)
    {
        for (int i = 0; i < spaces.size(); i++)
        {
            if (((Space)spaces.get(i)).contains(dim, x, y, z))
            {
                return true;
            }
        }

        return false;
    }

    public static void setBaseline(Space s)
    {
        Stats st = stats(s);
        s.baseline = new HashMap();
        Iterator it = st.items.entrySet().iterator();

        while (it.hasNext())
        {
            Map.Entry e = (Map.Entry)it.next();
            s.baseline.put(e.getKey(), Long.valueOf(((long[])e.getValue())[0]));
        }

        s.baselineTime = System.currentTimeMillis();
        changed();
    }

    /** Change since the baseline, or 0 without one. Total (null) sums every space's baseline. */
    public static long delta(Space s, Stats st, int key)
    {
        if (s != null)
        {
            if (s.baseline == null)
            {
                return 0L;
            }

            Long b = (Long)s.baseline.get(Integer.valueOf(key));
            return st.count(key) - (b != null ? b.longValue() : 0L);
        }

        return 0L;
    }

    // ---------------------------------------------------------------- merging scans

    /**
     * Takes the result of scanning some chunks: containers found there replace what was known, and
     * known containers in those chunks (and inside the scanned area) that weren't found are gone.
     * Older news never wins: a region file read while the chunk was loaded again is ignored.
     */
    public static void merge(StorageScanner.Result res)
    {
        boolean any = false;
        Set foundKeys = new HashSet();

        for (int i = 0; i < res.found.size(); i++)
        {
            Record r = (Record)res.found.get(i);
            String k = r.key();
            foundKeys.add(k);
            Record old = (Record)records.get(k);

            if (old != null && (old.seen > r.seen || res.fromSave && liveScanned(old)))
            {
                continue;
            }

            records.put(k, r);
            unseen.remove(k);

            if (!r.sameContents(old))
            {
                any = true;
            }
        }

        Set chunks = new HashSet(res.chunks);
        int[] area = res.area;
        Iterator it = records.values().iterator();

        while (it.hasNext())
        {
            Record r = (Record)it.next();

            if (r.dim == res.dim && r.seen <= res.time && !(res.fromSave && liveScanned(r)) && !foundKeys.contains(r.key()) && chunks.contains(Long.valueOf(chunkKey(r.x >> 4, r.z >> 4)))
                    && r.x >= area[0] && r.y >= area[1] && r.z >= area[2] && r.x <= area[3] && r.y <= area[4] && r.z <= area[5])
            {
                it.remove();
                any = true;
            }
        }

        if (any)
        {
            changed();
        }
        else
        {
            // Only the seen times moved: stats show live/cached, but no save is due.
            version++;
        }
    }

    /**
     * Seen by the live scan lately, so its chunk is (or just was) loaded. The region file can lag
     * behind such a chunk (the server writes chunks on its own thread, and the file's time is that of
     * any chunk in the region), so a region read never replaces or removes these.
     */
    private static boolean liveScanned(Record r)
    {
        return !r.fromSave && isLive(r);
    }

    public static long chunkKey(int cx, int cz)
    {
        return (long)cx << 32 | cz & 0xFFFFFFFFL;
    }

    // ---------------------------------------------------------------- files

    private static File settingsFile()
    {
        return new File(Minecraft.getMinecraftDir(), "config/StorageSpaces.cfg");
    }

    private static File worldFile(String key)
    {
        return new File(Minecraft.getMinecraftDir(), "config/StorageSpaces/" + key + ".txt");
    }

    /** "sp_<save folder>" or "mp_<server address>", safe as a file name. */
    public static String currentWorldKey(Minecraft mc)
    {
        String key;

        if (mc.getIntegratedServer() != null)
        {
            key = "sp_" + mc.getIntegratedServer().getFolderName();
        }
        else if (mc.getServerData() != null && mc.getServerData().serverIP != null)
        {
            key = "mp_" + mc.getServerData().serverIP;
        }
        else
        {
            key = "mp_unknown";
        }

        return key.replaceAll("[^A-Za-z0-9._ -]", "_");
    }

    public static void loadSettings()
    {
        File file = settingsFile();

        if (!file.exists())
        {
            return;
        }

        try
        {
            BufferedReader in = new BufferedReader(new FileReader(file));
            String line;

            while ((line = in.readLine()) != null)
            {
                int eq = line.indexOf('=');

                if (eq <= 0 || line.startsWith("#"))
                {
                    continue;
                }

                String k = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();

                try
                {
                    int v = value.equals("true") ? 1 : (value.equals("false") ? 0 : Integer.parseInt(value));

                    if (k.equals("containers")) containers = clamp(v, 0, 2);
                    else if (k.equals("outlines")) outlines = clamp(v, 0, 2);
                    else if (k.equals("labels")) labels = v != 0;
                    else if (k.equals("seeThrough")) seeThrough = v != 0;
                    else if (k.equals("refresh")) refresh = clamp(v, 1, 30);
                    else if (k.equals("numbers")) numbers = clamp(v, 0, 2);
                    else if (k.equals("sort")) sort = clamp(v, 0, 3);
                    else if (k.equals("locateSeconds")) locateSeconds = clamp(v, 0, 300);
                    else if (k.equals("locateCloses")) locateCloses = v != 0;
                    else if (k.equals("locateHud")) locateHud = v != 0;
                    else if (k.equals("deltas")) deltas = v != 0;
                    else if (k.equals("reach")) reach = clamp(v, 5, 128);
                    else if (k.equals("selectHud")) selectHud = v != 0;
                    else if (k.equals("nudgeStep")) nudgeStep = clamp(v, 1, 16);
                    else if (k.equals("showEmpty")) showEmpty = v != 0;
                    else if (k.equals("chartBy")) chartBy = clamp(v, 0, 1);
                    else if (k.equals("chartMeasure")) chartMeasure = clamp(v, 0, 1);
                }
                catch (NumberFormatException e)
                {
                    System.out.println("Storage Spaces: bad value in StorageSpaces.cfg: " + line);
                }
            }

            in.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void saveSettings()
    {
        try
        {
            File file = settingsFile();
            file.getParentFile().mkdirs();
            PrintWriter out = new PrintWriter(new FileWriter(file));
            out.println("# Storage Spaces settings (in game: press I, then the Settings tab)");
            out.println("containers=" + containers);
            out.println("outlines=" + outlines);
            out.println("labels=" + labels);
            out.println("seeThrough=" + seeThrough);
            out.println("refresh=" + refresh);
            out.println("numbers=" + numbers);
            out.println("sort=" + sort);
            out.println("locateSeconds=" + locateSeconds);
            out.println("locateCloses=" + locateCloses);
            out.println("locateHud=" + locateHud);
            out.println("deltas=" + deltas);
            out.println("reach=" + reach);
            out.println("selectHud=" + selectHud);
            out.println("nudgeStep=" + nudgeStep);
            out.println("showEmpty=" + showEmpty);
            out.println("chartBy=" + chartBy);
            out.println("chartMeasure=" + chartMeasure);
            out.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static int clamp(int v, int min, int max)
    {
        return Math.max(min, Math.min(max, v));
    }

    /** Switches to another world's data (null: none), saving the current one first. */
    public static void switchWorld(String key)
    {
        if (key != null ? key.equals(worldKey) : worldKey == null)
        {
            return;
        }

        saveWorld();
        spaces.clear();
        records.clear();
        unseen.clear();
        nextId = 1;
        worldKey = key;
        dirty = false;
        version++;

        if (key != null)
        {
            loadWorld();
        }
    }

    private static void loadWorld()
    {
        File file = worldFile(worldKey);

        if (!file.exists())
        {
            return;
        }

        try
        {
            BufferedReader in = new BufferedReader(new FileReader(file));
            String line;

            while ((line = in.readLine()) != null)
            {
                try
                {
                    if (line.startsWith("space|"))
                    {
                        String[] p = line.split("\\|", 15);
                        Space s = new Space();
                        s.id = Integer.parseInt(p[1]);
                        s.dim = Integer.parseInt(p[2]);
                        s.setBounds(Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6]), Integer.parseInt(p[7]), Integer.parseInt(p[8]));
                        s.color = Integer.parseInt(p[9]);
                        s.outline = !p[10].equals("0");
                        s.icon = Integer.parseInt(p[11]);
                        s.created = Long.parseLong(p[12]);
                        s.baselineTime = Long.parseLong(p[13]);
                        s.name = p[14];
                        spaces.add(s);
                        nextId = Math.max(nextId, s.id + 1);
                    }
                    else if (line.startsWith("baseline|"))
                    {
                        String[] p = line.split("\\|", 3);
                        Space s = getSpace(Integer.parseInt(p[1]));

                        if (s != null)
                        {
                            s.baseline = new HashMap();
                            String[] items = p[2].length() > 0 ? p[2].split(",") : new String[0];

                            for (int i = 0; i < items.length; i++)
                            {
                                String[] kv = items[i].split(":");
                                s.baseline.put(Integer.valueOf(Integer.parseInt(kv[0])), Long.valueOf(Long.parseLong(kv[1])));
                            }
                        }
                    }
                    else if (line.startsWith("rec|"))
                    {
                        String[] p = line.split("\\|", 10);
                        Record r = new Record();
                        r.dim = Integer.parseInt(p[1]);
                        r.x = Integer.parseInt(p[2]);
                        r.y = Integer.parseInt(p[3]);
                        r.z = Integer.parseInt(p[4]);
                        r.kind = Integer.parseInt(p[5]);
                        r.slots = Integer.parseInt(p[6]);
                        r.used = Integer.parseInt(p[7]);
                        r.seen = Long.parseLong(p[8]);
                        String[] items = p[9].length() > 0 ? p[9].split(",") : new String[0];
                        r.keys = new int[items.length];
                        r.counts = new int[items.length];

                        for (int i = 0; i < items.length; i++)
                        {
                            String[] kv = items[i].split(":");
                            r.keys[i] = Integer.parseInt(kv[0]);
                            r.counts[i] = Integer.parseInt(kv[1]);
                        }

                        records.put(r.key(), r);
                    }
                }
                catch (RuntimeException e)
                {
                    System.out.println("Storage Spaces: skipped a bad line in " + file.getName() + ": " + line);
                }
            }

            in.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void saveWorld()
    {
        if (worldKey == null || !dirty)
        {
            return;
        }

        dirty = false;

        try
        {
            File file = worldFile(worldKey);
            file.getParentFile().mkdirs();
            File tmp = new File(file.getPath() + ".tmp");
            PrintWriter out = new PrintWriter(new FileWriter(tmp));
            out.println("# Storage Spaces: spaces and the last known contents of their containers");

            for (int i = 0; i < spaces.size(); i++)
            {
                Space s = (Space)spaces.get(i);
                out.println("space|" + s.id + "|" + s.dim + "|" + s.x1 + "|" + s.y1 + "|" + s.z1 + "|" + s.x2 + "|" + s.y2 + "|" + s.z2 + "|" + s.color + "|" + (s.outline ? 1 : 0) + "|" + s.icon + "|" + s.created + "|" + s.baselineTime + "|" + s.name);

                if (s.baseline != null)
                {
                    StringBuilder b = new StringBuilder("baseline|" + s.id + "|");
                    Iterator it = s.baseline.entrySet().iterator();

                    while (it.hasNext())
                    {
                        Map.Entry e = (Map.Entry)it.next();
                        b.append(e.getKey()).append(':').append(e.getValue()).append(it.hasNext() ? "," : "");
                    }

                    out.println(b);
                }
            }

            Iterator it = records.values().iterator();

            while (it.hasNext())
            {
                Record r = (Record)it.next();
                StringBuilder b = new StringBuilder();
                b.append("rec|").append(r.dim).append('|').append(r.x).append('|').append(r.y).append('|').append(r.z).append('|').append(r.kind).append('|').append(r.slots).append('|').append(r.used).append('|').append(r.seen).append('|');

                for (int i = 0; i < r.keys.length; i++)
                {
                    b.append(r.keys[i]).append(':').append(r.counts[i]).append(i + 1 < r.keys.length ? "," : "");
                }

                out.println(b);
            }

            out.close();
            file.delete();

            if (!tmp.renameTo(file))
            {
                System.out.println("Storage Spaces: could not write " + file);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /** Writes a space's (or every space's) totals as CSV; returns the file. */
    public static File exportCsv(Space s, Stats st) throws Exception
    {
        String base = (s != null ? s.name : "All Storage").replaceAll("[^A-Za-z0-9._ -]", "_");
        String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new java.util.Date());
        File file = new File(Minecraft.getMinecraftDir(), "storagespaces/" + base + "_" + stamp + ".csv");
        file.getParentFile().mkdirs();
        PrintWriter out = new PrintWriter(new FileWriter(file));
        out.println("Item,ID,Count,Stacks,Containers" + (s != null && s.baseline != null ? ",Change" : ""));
        List keys = new ArrayList(st.items.keySet());
        java.util.Collections.sort(keys);

        for (int i = 0; i < keys.size(); i++)
        {
            int k = ((Integer)keys.get(i)).intValue();
            long n = st.count(k);
            int per = maxStack(k);
            out.println("\"" + itemName(k).replace("\"", "\"\"") + "\"," + idString(k) + "," + n + "," + (n / per) + (n % per > 0 ? "+" + n % per : "") + "," + st.containersWith(k) + (s != null && s.baseline != null ? "," + delta(s, st, k) : ""));
        }

        out.close();
        return file;
    }
}

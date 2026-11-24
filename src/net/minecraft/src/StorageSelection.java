package net.minecraft.src;

import java.lang.reflect.Field;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Selection mode (O, or the + tab): left-click a block for corner 1, right-click for corner 2,
 * Enter saves (with only corner 1, the block looked at is corner 2), Backspace starts over and O
 * cancels. The box is drawn in the world as it is made.
 *
 * Clicks reach the stand-in controller through the Attack and Use Item binds. Those may be keys
 * (Attack on C, say), so while selecting, a left or right mouse button that no bind uses gets a
 * hidden key binding of its own: Minecraft counts every press of it (KeyBinding.onTick), so even a
 * quick click between two frames isn't missed. A button that is bound is left alone.
 *
 * Once both corners are set (or a one-corner box is pinned by the first nudge key), the face the
 * player points at is the hot face: Grow and Shrink move it out or in, keeping the opposite face
 * where it is, and the Move keys move the whole box. With the wheel modifier held, the mouse wheel
 * grows and shrinks the hot face instead of changing the hotbar slot.
 */
public class StorageSelection
{
    public static boolean active;
    public static int[][] corners = new int[2][];
    /** The space being reselected, or -1 for a new one. */
    public static int editing = -1;
    public static int dim;
    private static StorageSelectController controller;
    private static boolean enterDown;
    private static boolean backDown;
    /** The hidden bindings on the left and right mouse buttons, while selecting (null: button is bound). */
    private static final KeyBinding[] mouseBinds = new KeyBinding[2];
    private static int containersInside;
    private static int frame;
    private static String warning;
    private static int warningColor;
    private static long warningTime;

    /** The face the nudge keys move (0 bottom, 1 top, 2 north, 3 south, 4 west, 5 east, like block sides), or -1. */
    public static int hotFace = -1;
    public static final String[] FACE_NAMES = new String[] {"Bottom", "Top", "North", "South", "West", "East"};
    /** The corner coordinate (x 0, y 1, z 2) each face moves along. */
    private static final int[] FACE_AXIS = new int[] {1, 1, 2, 2, 0, 0};
    /** Unit steps for south, west, north, east: Minecraft's facing order from the player's yaw. */
    private static final int[][] FACING = new int[][] {{0, 1}, { -1, 0}, {0, -1}, {1, 0}};
    private static final long[] lastPress = new long[6];
    private static final int[] held = new int[6];
    /** The hotbar slot as it was before this tick's input, to undo the wheel's change (-1: unknown). */
    private static int lastSlot = -1;

    public static void start(Minecraft mc, StorageData.Space edit)
    {
        if (mc.thePlayer == null || mc.playerController == null)
        {
            return;
        }

        cancel(mc);
        active = true;
        dim = mc.thePlayer.dimension;
        editing = edit != null ? edit.id : -1;
        corners[0] = null;
        corners[1] = null;

        if (edit != null && edit.dim == dim)
        {
            corners[0] = new int[] {edit.x1, edit.y1, edit.z1};
            corners[1] = new int[] {edit.x2, edit.y2, edit.z2};
        }

        controller = new StorageSelectController(mc, mc.playerController);
        mc.playerController = controller;
        // Keys still held (the select key) must be let go first.
        enterDown = backDown = true;
        lastSlot = mc.thePlayer.inventory.currentItem;
        hotFace = -1;
        hookMouse();
        mc.sndManager.playSoundFX("random.click", 1.0F, 1.2F);
    }

    public static void cancel(Minecraft mc)
    {
        if (controller != null && mc.playerController == controller)
        {
            mc.playerController = controller.getOriginal();
        }

        controller = null;
        active = false;
        unhookMouse();
        releaseKirmod();
        editing = -1;
        corners[0] = null;
        corners[1] = null;
        hotFace = -1;
        lastSlot = -1;
    }

    /** Puts a hidden binding on each mouse button no bind uses. Every frame: Controls rebuilds the table. */
    private static void hookMouse()
    {
        for (int b = 0; b < 2; b++)
        {
            int code = b - 100;
            Object bound = KeyBinding.hash.lookup(code);

            if (bound == null)
            {
                if (mouseBinds[b] == null)
                {
                    // The constructor lists it for Controls; take it out of the list, keep it in the table.
                    mouseBinds[b] = new KeyBinding("Storage Spaces selection click " + b, code);
                    KeyBinding.keybindArray.remove(mouseBinds[b]);
                }
                else
                {
                    KeyBinding.hash.addKey(code, mouseBinds[b]);
                }

                mouseBinds[b].pressTime = 0;
            }
            else if (bound != mouseBinds[b])
            {
                // A real bind took the button: it reaches the controller itself.
                mouseBinds[b] = null;
            }
        }
    }

    private static void unhookMouse()
    {
        for (int b = 0; b < 2; b++)
        {
            if (mouseBinds[b] != null && KeyBinding.hash.lookup(b - 100) == mouseBinds[b])
            {
                KeyBinding.hash.removeObject(b - 100);
            }

            mouseBinds[b] = null;
        }
    }

    /**
     * Kirmod turns its Sneak Place off for the session when it finds a controller it doesn't know
     * (ours, while selecting); once ours is gone it may hook in again.
     */
    private static void releaseKirmod()
    {
        String[] names = new String[] {"net.minecraft.src.KirInteraction", "KirInteraction"};

        for (int i = 0; i < names.length; i++)
        {
            try
            {
                Field f = Class.forName(names[i]).getDeclaredField("warnedController");
                f.setAccessible(true);
                f.setBoolean(null, false);
                return;
            }
            catch (Throwable t)
            {
                // Kirmod isn't installed (or is another version): nothing to undo.
            }
        }
    }

    static void setCorner(int i, int x, int y, int z)
    {
        Minecraft mc = ModLoader.getMinecraftInstance();
        int[] c = new int[] {x, y, z};
        int[] other = corners[1 - i];

        if (corners[i] != null && corners[i][0] == x && corners[i][1] == y && corners[i][2] == z)
        {
            return;
        }

        if (other != null && (Math.abs(other[0] - x) >= StorageData.MAX_EXTENT || Math.abs(other[2] - z) >= StorageData.MAX_EXTENT))
        {
            warn("Too big: a space can be at most " + StorageData.MAX_EXTENT + " blocks across");
            return;
        }

        corners[i] = c;
        mc.sndManager.playSoundFX("random.click", 1.0F, i == 0 ? 1.0F : 1.3F);
    }

    private static void warn(String s)
    {
        note(s, 0xFF6060);
    }

    private static void note(String s, int color)
    {
        warning = s;
        warningColor = color;
        warningTime = System.currentTimeMillis();
    }

    /**
     * A Grow, Shrink or Move key (0 grow, 1 shrink, 2 forward, 3 back, 4 up, 5 down). Repeating
     * keys fire every tick while held: act once on the press, then 10 times a second after 0.3
     * seconds, like typing. A gap of more than a tick means the key was let go.
     */
    public static void key(Minecraft mc, int i)
    {
        long now = System.currentTimeMillis();
        held[i] = now - lastPress[i] > 120L ? 0 : held[i] + 1;
        lastPress[i] = now;

        if (!active || mc.currentScreen != null || held[i] != 0 && (held[i] < 6 || held[i] % 2 != 0) || !pinned(mc))
        {
            return;
        }

        int step = step();

        if (i < 2)
        {
            nudge(mc, i == 0 ? step : -step);
        }
        else if (i < 4)
        {
            int[] fwd = FACING[MathHelper.floor_double(mc.thePlayer.rotationYaw * 4.0F / 360.0F + 0.5D) & 3];
            int d = i == 2 ? step : -step;
            move(fwd[0] * d, 0, fwd[1] * d);
        }
        else
        {
            move(0, i == 4 ? step : -step, 0);
        }
    }

    /** Blocks per nudge: the Nudge Step setting, or 5 with Shift held. */
    private static int step()
    {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) ? 5 : StorageData.nudgeStep;
    }

    /**
     * Whether the box is fixed so it can be nudged. A box with one corner follows the block looked
     * at; the first nudge pins it there (sets corner 2) instead of moving it.
     */
    private static boolean pinned(Minecraft mc)
    {
        if (corners[0] != null && corners[1] != null)
        {
            return true;
        }

        if (corners[0] == null && corners[1] == null)
        {
            warn("Pick corner 1 first (" + clickName(mc, 0) + " a block)");
            return false;
        }

        int[] b = box(mc);
        int[] one = corners[0] != null ? corners[0] : corners[1];
        // The far corner of the box is the block looked at (or the corner itself).
        int[] other = new int[3];

        for (int k = 0; k < 3; k++)
        {
            other[k] = b[k] == one[k] ? b[k + 3] : b[k];
        }

        corners[corners[0] != null ? 1 : 0] = other;
        updateHotFace(mc);
        mc.sndManager.playSoundFX("random.click", 1.0F, 1.3F);
        note("Box pinned: the nudge keys now move the " + FACE_NAMES[Math.max(0, hotFace)].toLowerCase() + " face", 0xFFFF80);
        return false;
    }

    /** Moves the hot face out (d > 0) or in by d blocks; the opposite face stays where it is. */
    static void nudge(Minecraft mc, int d)
    {
        int f = hotFace;

        if (f < 0 || corners[0] == null || corners[1] == null)
        {
            return;
        }

        int k = FACE_AXIS[f];
        int dir = (f & 1) == 1 ? 1 : -1;
        int[] a = corners[0];
        int[] b = corners[1];
        int from = dir > 0 ? Math.max(a[k], b[k]) : Math.min(a[k], b[k]);
        int fixed = dir > 0 ? Math.min(a[k], b[k]) : Math.max(a[k], b[k]);
        int to = from + dir * d;
        String limit = null;

        if ((to - fixed) * dir < 0)
        {
            to = fixed;
            limit = "A space can't be thinner than 1 block";
        }
        else if (k == 1 && (to < 0 || to > 255))
        {
            to = Math.max(0, Math.min(255, to));
            limit = "A space has to stay between y 0 and 255";
        }
        else if (k != 1 && Math.abs(to - fixed) >= StorageData.MAX_EXTENT)
        {
            to = fixed + dir * (StorageData.MAX_EXTENT - 1);
            limit = "Too big: a space can be at most " + StorageData.MAX_EXTENT + " blocks across";
        }

        if (limit != null)
        {
            warn(limit);
        }

        if (to == from)
        {
            return;
        }

        // The corner on the moving face (with a 1-block-thin box both are; either one will do).
        int i = a[k] != b[k] ? (a[k] == from ? 0 : 1) : (dir > 0 ? 1 : 0);
        int[] c = (int[])corners[i].clone();
        c[k] = to;
        corners[i] = c;
        mc.sndManager.playSoundFX("random.click", 1.0F, d > 0 ? 1.15F : 0.9F);
    }

    /** Moves the whole box; up and down stop at y 0 and 255. */
    static void move(int dx, int dy, int dz)
    {
        Minecraft mc = ModLoader.getMinecraftInstance();
        int lo = Math.min(corners[0][1], corners[1][1]);
        int hi = Math.max(corners[0][1], corners[1][1]);

        if (lo + dy < 0 || hi + dy > 255)
        {
            dy = dy < 0 ? -lo : 255 - hi;
            warn("A space has to stay between y 0 and 255");
        }

        if (dx == 0 && dy == 0 && dz == 0)
        {
            return;
        }

        for (int i = 0; i < 2; i++)
        {
            corners[i] = new int[] {corners[i][0] + dx, corners[i][1] + dy, corners[i][2] + dz};
        }

        mc.sndManager.playSoundFX("random.click", 1.0F, 1.05F);
    }

    /**
     * The face the look ray hits first: the near face from outside the box, the far one from
     * inside. When the ray misses, the face along the axis looked along most squarely (the near
     * one when the box is ahead on that axis, else the one looked toward).
     */
    private static void updateHotFace(Minecraft mc)
    {
        if (corners[0] == null || corners[1] == null)
        {
            hotFace = -1;
            return;
        }

        int[] b = box(mc);
        EntityPlayer p = mc.thePlayer;
        double[] eye = new double[] {p.posX, p.posY, p.posZ};
        float yaw = p.rotationYaw * (float)Math.PI / 180.0F;
        float pitch = p.rotationPitch * (float)Math.PI / 180.0F;
        double[] look = new double[] { -MathHelper.sin(yaw) * MathHelper.cos(pitch), -MathHelper.sin(pitch), MathHelper.cos(yaw) * MathHelper.cos(pitch)};
        double[] lo = new double[] {b[0], b[1], b[2]};
        double[] hi = new double[] {b[3] + 1, b[4] + 1, b[5] + 1};
        // Faces as block sides; FACE_AXIS gives each one's axis, the low face is the even one.
        int best = -1;
        double bestT = Double.MAX_VALUE;

        for (int f = 0; f < 6; f++)
        {
            int k = FACE_AXIS[f];

            if (look[k] == 0.0D)
            {
                continue;
            }

            double t = (((f & 1) == 1 ? hi[k] : lo[k]) - eye[k]) / look[k];

            if (t <= 0.0D || t >= bestT)
            {
                continue;
            }

            boolean inside = true;

            for (int j = 0; j < 3; j++)
            {
                double v = eye[j] + look[j] * t;
                inside &= j == k || v >= lo[j] && v <= hi[j];
            }

            if (inside)
            {
                best = f;
                bestT = t;
            }
        }

        if (best == -1)
        {
            int k = 0;

            for (int j = 1; j < 3; j++)
            {
                if (Math.abs(look[j]) > Math.abs(look[k]))
                {
                    k = j;
                }
            }

            boolean ahead = look[k] > 0.0D ? eye[k] < lo[k] : eye[k] > hi[k];
            boolean high = look[k] > 0.0D != ahead;
            best = (k == 1 ? 0 : (k == 2 ? 2 : 4)) + (high ? 1 : 0);
        }

        hotFace = best;
    }

    /**
     * The wheel modifier: Minecraft has already turned the wheel into a hotbar change
     * (Minecraft.runTick: inventory.changeCurrentItem), so it is read back from the slot and the
     * slot put back. Run from the marker entity's update, which comes after runTick's input and
     * before the frame is drawn or the slot is sent to the server; each frame's tick runs it too in
     * case the marker isn't there.
     */
    public static void checkWheel(Minecraft mc)
    {
        if (!active || mc.thePlayer == null)
        {
            return;
        }

        InventoryPlayer inv = mc.thePlayer.inventory;
        int slot = inv.currentItem;

        if (lastSlot >= 0 && slot != lastSlot && mc.currentScreen == null && wheelModifierDown() && !hotbarKeyDown())
        {
            // changeCurrentItem takes one slot per wheel notch, wheel up being the slot to the left.
            int notches = ((lastSlot - slot) % 9 + 13) % 9 - 4;
            inv.currentItem = lastSlot;

            if (pinned(mc))
            {
                nudge(mc, notches * step());
            }

            return;
        }

        lastSlot = slot;
    }

    private static boolean wheelModifierDown()
    {
        int code = StorageSpacesMod.KEY_WHEEL.keyCode;
        return code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
    }

    /** The 1-9 keys pick a slot themselves; that change isn't the wheel's. */
    private static boolean hotbarKeyDown()
    {
        for (int i = 0; i < 9; i++)
        {
            if (Keyboard.isKeyDown(Keyboard.KEY_1 + i))
            {
                return true;
            }
        }

        return false;
    }

    /** The box being made: both corners, or corner 1 to the block looked at. */
    public static int[] box(Minecraft mc)
    {
        int[] a = corners[0];
        int[] b = corners[1];

        if (a == null && b == null)
        {
            return null;
        }

        if (a == null || b == null)
        {
            int[] t = target(mc);
            int[] one = a != null ? a : b;

            if (t == null)
            {
                return new int[] {one[0], one[1], one[2], one[0], one[1], one[2]};
            }

            a = one;
            b = t;
        }

        return new int[] {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]), Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])};
    }

    private static int[] target(Minecraft mc)
    {
        MovingObjectPosition m = mc.objectMouseOver;
        return m != null && m.typeOfHit == EnumMovingObjectType.TILE ? new int[] {m.blockX, m.blockY, m.blockZ} : null;
    }

    /** Every frame: keys, losing the controller, the containers count. */
    public static void tick(Minecraft mc)
    {
        if (!active)
        {
            return;
        }

        // A new world or a respawn into another dimension replaced the controller: selection is over.
        if (mc.playerController != controller || mc.thePlayer == null || mc.thePlayer.dimension != dim)
        {
            cancel(mc);
            return;
        }

        boolean enter = Keyboard.isKeyDown(Keyboard.KEY_RETURN) || Keyboard.isKeyDown(Keyboard.KEY_NUMPADENTER);
        boolean back = Keyboard.isKeyDown(Keyboard.KEY_BACK);

        if (mc.currentScreen == null)
        {
            if (enter && !enterDown)
            {
                confirm(mc);
            }

            if (back && !backDown)
            {
                corners[0] = null;
                corners[1] = null;
                mc.sndManager.playSoundFX("random.click", 1.0F, 0.8F);
            }
        }

        enterDown = enter;
        backDown = back;

        for (int b = 0; b < 2; b++)
        {
            KeyBinding k = mouseBinds[b];
            boolean clicked = false;

            // Clicks on a screen count too; only ones made in the world pick a corner.
            while (k != null && k.isPressed())
            {
                clicked |= mc.currentScreen == null;
            }

            if (clicked)
            {
                MovingObjectPosition m = mc.objectMouseOver;

                if (m != null && m.typeOfHit == EnumMovingObjectType.TILE)
                {
                    setCorner(b, m.blockX, m.blockY, m.blockZ);
                }
                else
                {
                    warn("Look at a block (up to " + StorageData.reach + " blocks away)");
                }
            }
        }

        hookMouse();
        checkWheel(mc);
        updateHotFace(mc);

        if (++frame % 10 == 0)
        {
            int[] b = box(mc);
            containersInside = b != null ? countContainers(mc.theWorld, b) : 0;
        }
    }

    /** "left-click", "left-click or C" when Attack is on a key, or just "C" when the button belongs to another bind. */
    private static String clickName(Minecraft mc, int b)
    {
        int code = (b == 0 ? mc.gameSettings.keyBindAttack : mc.gameSettings.keyBindUseItem).keyCode;
        String name = b == 0 ? "left-click" : "right-click";

        if (code == b - 100)
        {
            return name;
        }

        String key = code >= 0 ? Keyboard.getKeyName(code) : "mouse " + (code + 101);
        return mouseBinds[b] != null ? name + " or " + key : "press " + key + " on";
    }

    private static void confirm(Minecraft mc)
    {
        int[] b = box(mc);

        if (b == null || corners[0] == null && corners[1] == null)
        {
            warn("Pick corner 1 first (" + clickName(mc, 0) + " a block)");
            return;
        }

        if (b[3] - b[0] >= StorageData.MAX_EXTENT || b[5] - b[2] >= StorageData.MAX_EXTENT)
        {
            warn("Too big: a space can be at most " + StorageData.MAX_EXTENT + " blocks across");
            return;
        }

        StorageData.Space edit = StorageData.getSpace(editing);
        int d = dim;
        cancel(mc);

        if (edit != null)
        {
            edit.dim = d;
            edit.setBounds(b[0], b[1], b[2], b[3], b[4], b[5]);
            StorageData.changed();
            StorageSpacesMod.spacesChanged();
            mc.displayGuiScreen(new GuiStorageSpaces(edit.id));
        }
        else
        {
            mc.displayGuiScreen(new GuiStorageName(d, b));
        }
    }

    /** Containers in the box, from the client's tile entities of loaded chunks. */
    public static int countContainers(World world, int[] b)
    {
        int n = 0;

        for (int cx = b[0] >> 4; cx <= b[3] >> 4; cx++)
        {
            for (int cz = b[2] >> 4; cz <= b[5] >> 4; cz++)
            {
                Chunk chunk = world.getChunkFromChunkCoords(cx, cz);

                if (chunk.isEmpty())
                {
                    continue;
                }

                Iterator it = chunk.chunkTileEntityMap.values().iterator();

                while (it.hasNext())
                {
                    TileEntity te = (TileEntity)it.next();

                    if (te instanceof IInventory && !(te instanceof TileEntityEnderChest) && StorageData.counts(StorageScanner.kindOf(te))
                            && te.xCoord >= b[0] && te.yCoord >= b[1] && te.zCoord >= b[2] && te.xCoord <= b[3] && te.yCoord <= b[4] && te.zCoord <= b[5])
                    {
                        n++;
                    }
                }
            }
        }

        return n;
    }

    /** The help panel at the top of the screen. */
    public static void drawHud(Minecraft mc, ScaledResolution res)
    {
        if (!active || mc.currentScreen != null || !StorageData.selectHud)
        {
            return;
        }

        FontRenderer f = mc.fontRenderer;
        StorageData.Space edit = StorageData.getSpace(editing);
        String title = edit != null ? "Reselecting \u00a7e" + edit.name : "\u00a7eNew Storage Space";
        String c1 = corners[0] != null ? corners[0][0] + ", " + corners[0][1] + ", " + corners[0][2] : "\u00a77" + clickName(mc, 0) + " a block";
        String c2 = corners[1] != null ? corners[1][0] + ", " + corners[1][1] + ", " + corners[1][2] : "\u00a77" + clickName(mc, 1) + " a block";
        String line2 = "\u00a7bCorner 1\u00a7r " + c1 + "    \u00a7dCorner 2\u00a7r " + c2;
        int[] b = box(mc);
        String line3 = b != null ? (b[3] - b[0] + 1) + " x " + (b[4] - b[1] + 1) + " x " + (b[5] - b[2] + 1) + "  \u00a77|\u00a7r  " + containersInside + " container" + (containersInside == 1 ? "" : "s") + " inside" : "Reach " + StorageData.reach + " blocks";
        String[] adjust = nudgeHelp(mc);
        String line5 = "\u00a77Enter\u00a7r save   \u00a77Backspace\u00a7r clear   \u00a77" + Keyboard.getKeyName(StorageSpacesMod.KEY_SELECT.keyCode) + "\u00a7r cancel";
        String[] lines = new String[4 + adjust.length];
        lines[0] = title;
        lines[1] = line2;
        lines[2] = line3;
        System.arraycopy(adjust, 0, lines, 3, adjust.length);
        lines[lines.length - 1] = line5;
        int w = 0;

        for (int i = 0; i < lines.length; i++)
        {
            w = Math.max(w, f.getStringWidth(lines[i]));
        }

        int x = (res.getScaledWidth() - w) / 2;
        int y = 6;
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        Gui.drawRect(x - 5, y - 4, x + w + 5, y + lines.length * 11 + 2, 0xA0000000);
        Gui.drawRect(x - 5, y - 4, x + w + 5, y - 3, 0xFFFFAA00);

        for (int i = 0; i < lines.length; i++)
        {
            f.drawStringWithShadow(lines[i], x + (w - f.getStringWidth(lines[i])) / 2, y + i * 11, 0xFFFFFF);
        }

        if (warning != null && System.currentTimeMillis() - warningTime < 3000L)
        {
            int ww = f.getStringWidth(warning);
            f.drawStringWithShadow(warning, (res.getScaledWidth() - ww) / 2, y + lines.length * 11 + 6, warningColor);
        }
    }

    /**
     * The controls for adjusting the box: once both corners are picked, its own block of lines
     * (the hot face, grow/shrink, move); with one corner, how to pin it; nothing before that.
     */
    private static String[] nudgeHelp(Minecraft mc)
    {
        if (corners[0] == null && corners[1] == null)
        {
            return new String[0];
        }

        String grow = keyName(StorageSpacesMod.KEY_GROW);
        String shrink = keyName(StorageSpacesMod.KEY_SHRINK);

        if (hotFace < 0)
        {
            return new String[] {"\u00a77" + grow + "\u00a7r or \u00a77" + shrink + "\u00a7r pins the box here to adjust it"};
        }

        String step = "step " + StorageData.nudgeStep + ", Shift 5";
        // Paired keys keep each line narrow enough for a 427-wide GUI (854 x 480 at scale 2).
        return new String[]
        {
            "\u00a76Adjust\u00a7r the \u00a7f" + FACE_NAMES[hotFace] + " face\u00a7r (look to pick)  \u00a78" + step,
            "\u00a77" + grow + "/" + shrink + "\u00a7r grow/shrink  \u00a77" + keyName(StorageSpacesMod.KEY_WHEEL) + " + wheel\u00a7r too",
            "\u00a77" + keyName(StorageSpacesMod.KEY_FORWARD) + "/" + keyName(StorageSpacesMod.KEY_BACK) + "\u00a7r forward/back  \u00a77" + keyName(StorageSpacesMod.KEY_UP) + "/" + keyName(StorageSpacesMod.KEY_DOWN) + "\u00a7r up/down"
        };
    }

    private static String keyName(KeyBinding k)
    {
        return k.keyCode < 0 ? "mouse " + (k.keyCode + 101) : Keyboard.getKeyName(k.keyCode);
    }
}

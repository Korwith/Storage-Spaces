package net.minecraft.src;

import java.util.Iterator;
import org.lwjgl.input.Keyboard;

/**
 * Names a newly selected Storage Space. Shows its size and how many containers are in it, and can
 * shrink the box to just the containers inside.
 */
public class GuiStorageName extends GuiScreen
{
    /** When the screen was closed, so the select key's press doesn't start a new selection. */
    static long closedAt;

    /** The Enter press that confirmed the selection may still be queued for this screen. */
    private final long openedAt = System.currentTimeMillis();
    private final int dim;
    private int[] box;
    private GuiTextField name;
    private int px;
    private int py;
    private int pw = 236;
    private int ph = 132;
    private int containers;

    public GuiStorageName(int dim, int[] box)
    {
        this.dim = dim;
        this.box = box;
    }

    public void initGui()
    {
        Keyboard.enableRepeatEvents(true);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        String old = name != null ? name.getText() : StorageData.uniqueName("Storage");
        name = new GuiTextField(fontRenderer, px + 10, py + 30, pw - 20, 16);
        name.setMaxStringLength(40);
        name.setText(old);
        name.setFocused(true);
        controlList.clear();
        int bw = (pw - 20 - 8) / 3;
        controlList.add(new StorageUI.ThemedButton(0, px + 10, py + ph - 52, pw - 20, 20, "Shrink to Containers"));
        controlList.add(new StorageUI.ThemedButton(1, px + 10, py + ph - 28, bw, 20, "Create"));
        controlList.add(new StorageUI.ThemedButton(2, px + 10 + bw + 4, py + ph - 28, bw, 20, "Reselect"));
        controlList.add(new StorageUI.ThemedButton(3, px + 10 + (bw + 4) * 2, py + ph - 28, bw, 20, "Cancel"));
        containers = StorageSelection.countContainers(mc.theWorld, box);
        ((GuiButton)controlList.get(0)).enabled = containers > 0;
    }

    public void onGuiClosed()
    {
        Keyboard.enableRepeatEvents(false);
        closedAt = System.currentTimeMillis();
    }

    public boolean doesGuiPauseGame()
    {
        return false;
    }

    protected void actionPerformed(GuiButton b)
    {
        if (b.id == 0)
        {
            shrink();
        }
        else if (b.id == 1)
        {
            create();
        }
        else if (b.id == 2)
        {
            StorageSelection.start(mc, null);
            StorageSelection.corners[0] = new int[] {box[0], box[1], box[2]};
            StorageSelection.corners[1] = new int[] {box[3], box[4], box[5]};
            mc.displayGuiScreen(null);
        }
        else
        {
            mc.displayGuiScreen(null);
        }
    }

    /** Fits the box around the containers in it. */
    private void shrink()
    {
        int[] b = null;

        for (int cx = box[0] >> 4; cx <= box[3] >> 4; cx++)
        {
            for (int cz = box[2] >> 4; cz <= box[5] >> 4; cz++)
            {
                Iterator it = mc.theWorld.getChunkFromChunkCoords(cx, cz).chunkTileEntityMap.values().iterator();

                while (it.hasNext())
                {
                    TileEntity te = (TileEntity)it.next();

                    if (!(te instanceof IInventory) || te instanceof TileEntityEnderChest || !StorageData.counts(StorageScanner.kindOf(te))
                            || te.xCoord < box[0] || te.yCoord < box[1] || te.zCoord < box[2] || te.xCoord > box[3] || te.yCoord > box[4] || te.zCoord > box[5])
                    {
                        continue;
                    }

                    if (b == null)
                    {
                        b = new int[] {te.xCoord, te.yCoord, te.zCoord, te.xCoord, te.yCoord, te.zCoord};
                    }
                    else
                    {
                        b[0] = Math.min(b[0], te.xCoord);
                        b[1] = Math.min(b[1], te.yCoord);
                        b[2] = Math.min(b[2], te.zCoord);
                        b[3] = Math.max(b[3], te.xCoord);
                        b[4] = Math.max(b[4], te.yCoord);
                        b[5] = Math.max(b[5], te.zCoord);
                    }
                }
            }
        }

        if (b != null)
        {
            box = b;
        }
    }

    private void create()
    {
        String n = name.getText().trim();

        if (n.length() == 0)
        {
            n = StorageData.uniqueName("Storage");
        }

        StorageData.Space s = StorageData.addSpace(n, dim, new int[] {box[0], box[1], box[2]}, new int[] {box[3], box[4], box[5]});
        StorageSpacesMod.spacesChanged();
        StorageData.saveWorld();
        mc.displayGuiScreen(new GuiStorageSpaces(s.id));
    }

    protected void keyTyped(char c, int key)
    {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER)
        {
            if (System.currentTimeMillis() - openedAt > 300L)
            {
                create();
            }

            return;
        }

        if (key == Keyboard.KEY_ESCAPE)
        {
            mc.displayGuiScreen(null);
            return;
        }

        name.textboxKeyTyped(c, key);
    }

    protected void mouseClicked(int mx, int my, int button)
    {
        super.mouseClicked(mx, my, button);
        name.mouseClicked(mx, my, button);
    }

    public void updateScreen()
    {
        name.updateCursorCounter();
    }

    public void drawScreen(int mx, int my, float partialTicks)
    {
        drawDefaultBackground();
        StorageUI.panel(px, py, px + pw, py + ph);
        StorageUI.text(fontRenderer, "New Storage Space", px + 10, py + 8, StorageUI.LABEL);
        StorageUI.text(fontRenderer, "Name", px + 10, py + 20, StorageUI.MUTED);
        name.drawTextBox();
        String size = (box[3] - box[0] + 1) + " x " + (box[4] - box[1] + 1) + " x " + (box[5] - box[2] + 1) + " in the " + StorageData.dimName(dim);
        StorageUI.text(fontRenderer, size, px + 10, py + 52, StorageUI.LABEL);
        String found = containers + " container" + (containers == 1 ? "" : "s") + " inside (" + StorageData.CONTAINER_NAMES[StorageData.containers].replace("+ ", "chests + ").toLowerCase() + ")";
        StorageUI.text(fontRenderer, StorageUI.fit(fontRenderer, found, pw - 20), px + 10, py + 64, containers > 0 ? StorageUI.GOOD : StorageUI.BAD);
        super.drawScreen(mx, my, partialTicks);
    }
}

package net.minecraft.src;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;

/**
 * Stands in for the player controller while a Storage Space is being selected: left-click picks
 * corner 1, right-click corner 2, and nothing is broken, placed, used or attacked. Blocks can be
 * picked further away than usual (the Reach setting). Swapped in only for selection mode and put
 * back after.
 */
public class StorageSelectController extends PlayerControllerMP
{
    private final PlayerControllerMP original;

    public StorageSelectController(Minecraft mc, PlayerControllerMP original)
    {
        super(mc, mc.getSendQueue());
        this.original = original;

        // Same game mode as the real controller (creative flight, HUD).
        try
        {
            Field[] fields = PlayerControllerMP.class.getDeclaredFields();

            for (int i = 0; i < fields.length; i++)
            {
                if (fields[i].getType() == EnumGameType.class)
                {
                    fields[i].setAccessible(true);
                    fields[i].set(this, fields[i].get(original));
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public PlayerControllerMP getOriginal()
    {
        return original;
    }

    public void clickBlock(int x, int y, int z, int side)
    {
        StorageSelection.setCorner(0, x, y, z);
    }

    public void onPlayerDamageBlock(int x, int y, int z, int side) {}

    public boolean onPlayerDestroyBlock(int x, int y, int z, int side)
    {
        return false;
    }

    public boolean onPlayerRightClick(EntityPlayer player, World world, ItemStack stack, int x, int y, int z, int side, Vec3 hit)
    {
        StorageSelection.setCorner(1, x, y, z);
        return true;
    }

    public boolean sendUseItem(EntityPlayer player, World world, ItemStack stack)
    {
        return false;
    }

    public void attackEntity(EntityPlayer player, Entity target) {}

    public boolean func_78768_b(EntityPlayer player, Entity target)
    {
        return false;
    }

    public float getBlockReachDistance()
    {
        return (float)StorageData.reach;
    }
}

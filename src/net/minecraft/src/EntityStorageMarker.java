package net.minecraft.src;

import net.minecraft.client.Minecraft;

/**
 * A client-only entity that follows the player so the world renderer draws the Storage Spaces outlines every
 * frame (ModLoader has no world render hook). It never collides, saves or reaches the server.
 */
public class EntityStorageMarker extends Entity
{
    public EntityStorageMarker(World world)
    {
        super(world);
        this.ignoreFrustumCheck = true;
        this.noClip = true;
        this.setSize(0.1F, 0.1F);
    }

    protected void entityInit() {}

    protected void readEntityFromNBT(NBTTagCompound tag) {}

    protected void writeEntityToNBT(NBTTagCompound tag) {}

    public void onUpdate()
    {
        Minecraft mc = ModLoader.getMinecraftInstance();

        if (mc.thePlayer != null && mc.thePlayer.worldObj == this.worldObj)
        {
            this.setPosition(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
            this.lastTickPosX = this.prevPosX = this.posX;
            this.lastTickPosY = this.prevPosY = this.posY;
            this.lastTickPosZ = this.prevPosZ = this.posZ;
        }

        // Entities update after runTick's input, before the frame is drawn: the place to undo the wheel.
        StorageSelection.checkWheel(mc);
    }

    public boolean isInRangeToRenderDist(double distance)
    {
        return true;
    }

    public boolean canBeCollidedWith()
    {
        return false;
    }

    public boolean canBePushed()
    {
        return false;
    }

    public int getBrightnessForRender(float partialTicks)
    {
        return 0xF000F0;
    }
}

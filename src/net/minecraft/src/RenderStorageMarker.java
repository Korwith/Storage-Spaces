package net.minecraft.src;

/** Draws the Storage Spaces overlay when the world renderer reaches its marker entity. */
public class RenderStorageMarker extends Render
{
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks)
    {
        if (StorageSpacesMod.isMarker(entity))
        {
            StorageRender.renderWorld(ModLoader.getMinecraftInstance());
        }
    }
}

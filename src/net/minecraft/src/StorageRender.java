package net.minecraft.src;

import java.util.List;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

/**
 * Draws Storage Spaces in the world: each space's outline and name, the box being selected, and
 * the containers holding a located item (through walls, pulsing).
 */
public class StorageRender
{
    /** Called from the marker entity's renderer, in world space relative to the camera. */
    public static void renderWorld(Minecraft mc)
    {
        if (mc.thePlayer == null)
        {
            return;
        }

        int dim = mc.thePlayer.dimension;
        double ox = RenderManager.renderPosX;
        double oy = RenderManager.renderPosY;
        double oz = RenderManager.renderPosZ;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glTranslated(-ox, -oy, -oz);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        boolean showSpaces = StorageData.outlines == 2 || StorageData.outlines == 1 && (StorageSelection.active || StorageSpacesMod.locateKey != -1);

        if (showSpaces)
        {
            for (int i = 0; i < StorageData.spaces.size(); i++)
            {
                StorageData.Space s = (StorageData.Space)StorageData.spaces.get(i);

                if (s.dim != dim || !s.outline || StorageSelection.active && s.id == StorageSelection.editing)
                {
                    continue;
                }

                box(s.x1, s.y1, s.z1, s.x2 + 1, s.y2 + 1, s.z2 + 1, s.color, 0.85F, 0.0F, 2.0F, StorageData.seeThrough);
            }
        }

        if (StorageSelection.active)
        {
            int[] b = StorageSelection.box(mc);

            if (b != null)
            {
                box(b[0] - 0.01D, b[1] - 0.01D, b[2] - 0.01D, b[3] + 1.01D, b[4] + 1.01D, b[5] + 1.01D, 0xFFCC33, 1.0F, 0.10F, 3.0F, true);
            }

            int f = StorageSelection.hotFace;

            if (b != null && f >= 0)
            {
                // The hot face, as a thin slab just outside the box.
                double[] lo = new double[] {b[0] - 0.03D, b[1] - 0.03D, b[2] - 0.03D};
                double[] hi = new double[] {b[3] + 1.03D, b[4] + 1.03D, b[5] + 1.03D};
                int k = f < 2 ? 1 : (f < 4 ? 2 : 0);

                if ((f & 1) == 1)
                {
                    lo[k] = hi[k];
                }
                else
                {
                    hi[k] = lo[k];
                }

                box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2], 0xFFFFFF, 1.0F, 0.22F, 3.0F, true);
            }

            if (StorageSelection.corners[0] != null)
            {
                int[] c = StorageSelection.corners[0];
                box(c[0] - 0.03D, c[1] - 0.03D, c[2] - 0.03D, c[0] + 1.03D, c[1] + 1.03D, c[2] + 1.03D, 0x55FFFF, 1.0F, 0.25F, 2.0F, true);
            }

            if (StorageSelection.corners[1] != null)
            {
                int[] c = StorageSelection.corners[1];
                box(c[0] - 0.03D, c[1] - 0.03D, c[2] - 0.03D, c[0] + 1.03D, c[1] + 1.03D, c[2] + 1.03D, 0xFF55FF, 1.0F, 0.25F, 2.0F, true);
            }
        }

        List found = StorageSpacesMod.locateFound;

        if (StorageSpacesMod.locateKey != -1 && found != null)
        {
            float pulse = 0.55F + 0.45F * (float)Math.sin(System.currentTimeMillis() / 180.0D);

            for (int i = 0; i < found.size(); i++)
            {
                StorageData.Record r = (StorageData.Record)found.get(i);

                if (r.dim == dim)
                {
                    box(r.x - 0.04D, r.y - 0.04D, r.z - 0.04D, r.x + 1.04D, r.y + 1.04D, r.z + 1.04D, 0x40FF60, pulse, 0.18F * pulse, 3.0F, true);
                }
            }
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);

        if (showSpaces && StorageData.labels)
        {
            for (int i = 0; i < StorageData.spaces.size(); i++)
            {
                StorageData.Space s = (StorageData.Space)StorageData.spaces.get(i);

                if (s.dim == dim && s.outline)
                {
                    StorageData.Stats st = StorageSpacesMod.cachedStats(s);
                    label(mc, s.name, StorageData.compact(st.total) + " items", (s.x1 + s.x2 + 1) / 2.0D, s.y2 + 1.6D, (s.z1 + s.z2 + 1) / 2.0D, s.color);
                }
            }
        }

        if (StorageSpacesMod.locateKey != -1 && found != null)
        {
            for (int i = 0; i < found.size(); i++)
            {
                StorageData.Record r = (StorageData.Record)found.get(i);

                if (r.dim == dim)
                {
                    label(mc, "x " + StorageData.exact(r.count(StorageSpacesMod.locateKey)), null, r.x + 0.5D, r.y + 1.5D, r.z + 0.5D, 0x40FF60);
                }
            }
        }

        GL11.glPopAttrib();
        GL11.glPopMatrix();
        GL11.glDepthMask(true);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Edges (and faint faces when fill > 0) of a box; see-through draws a dim copy behind walls first. */
    private static void box(double x1, double y1, double z1, double x2, double y2, double z2, int rgb, float alpha, float fill, float width, boolean through)
    {
        float r = (rgb >> 16 & 255) / 255.0F;
        float g = (rgb >> 8 & 255) / 255.0F;
        float b = (rgb & 255) / 255.0F;

        if (through)
        {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glLineWidth(Math.max(1.0F, width - 1.0F));
            edges(x1, y1, z1, x2, y2, z2, r, g, b, alpha * 0.35F);

            if (fill > 0.0F)
            {
                faces(x1, y1, z1, x2, y2, z2, r, g, b, fill * 0.5F);
            }

            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }

        GL11.glLineWidth(width);
        edges(x1, y1, z1, x2, y2, z2, r, g, b, alpha);

        if (fill > 0.0F)
        {
            faces(x1, y1, z1, x2, y2, z2, r, g, b, fill);
        }
    }

    private static void edges(double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a)
    {
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_LINES);
        t.setColorRGBA_F(r, g, b, a);
        double[] xs = new double[] {x1, x2};
        double[] ys = new double[] {y1, y2};
        double[] zs = new double[] {z1, z2};

        for (int i = 0; i < 2; i++)
        {
            for (int j = 0; j < 2; j++)
            {
                t.addVertex(x1, ys[i], zs[j]);
                t.addVertex(x2, ys[i], zs[j]);
                t.addVertex(xs[i], y1, zs[j]);
                t.addVertex(xs[i], y2, zs[j]);
                t.addVertex(xs[i], ys[j], z1);
                t.addVertex(xs[i], ys[j], z2);
            }
        }

        t.draw();
    }

    private static void faces(double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a)
    {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F(r, g, b, a);
        t.addVertex(x1, y1, z1); t.addVertex(x2, y1, z1); t.addVertex(x2, y1, z2); t.addVertex(x1, y1, z2);
        t.addVertex(x1, y2, z1); t.addVertex(x1, y2, z2); t.addVertex(x2, y2, z2); t.addVertex(x2, y2, z1);
        t.addVertex(x1, y1, z1); t.addVertex(x1, y2, z1); t.addVertex(x2, y2, z1); t.addVertex(x2, y1, z1);
        t.addVertex(x1, y1, z2); t.addVertex(x2, y1, z2); t.addVertex(x2, y2, z2); t.addVertex(x1, y2, z2);
        t.addVertex(x1, y1, z1); t.addVertex(x1, y1, z2); t.addVertex(x1, y2, z2); t.addVertex(x1, y2, z1);
        t.addVertex(x2, y1, z1); t.addVertex(x2, y2, z1); t.addVertex(x2, y2, z2); t.addVertex(x2, y1, z2);
        t.draw();
    }

    /** A name tag like a player's, readable through walls, growing less than distance so far ones stay legible. */
    private static void label(Minecraft mc, String text, String sub, double x, double y, double z, int rgb)
    {
        RenderManager rm = RenderManager.instance;
        double dx = x - RenderManager.renderPosX;
        double dy = y - RenderManager.renderPosY;
        double dz = z - RenderManager.renderPosZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist > 160.0D)
        {
            return;
        }

        FontRenderer f = mc.fontRenderer;
        float scale = 0.016666668F * 1.6F * (float)Math.max(1.0D, dist / 12.0D);
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(rm.playerViewX, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(-scale, -scale, scale);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        int w = Math.max(f.getStringWidth(text), sub != null ? f.getStringWidth(sub) : 0) / 2;
        int h = sub != null ? 18 : 8;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F(0.0F, 0.0F, 0.0F, 0.4F);
        t.addVertex(-w - 2, -2, 0.0D);
        t.addVertex(-w - 2, h + 1, 0.0D);
        t.addVertex(w + 2, h + 1, 0.0D);
        t.addVertex(w + 2, -2, 0.0D);
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        f.drawString(text, -f.getStringWidth(text) / 2, 0, 0xFF000000 | rgb);

        if (sub != null)
        {
            f.drawString(sub, -f.getStringWidth(sub) / 2, 10, 0xFFDDDDDD);
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }
}

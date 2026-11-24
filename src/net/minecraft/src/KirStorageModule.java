package net.minecraft.src;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;

/**
 * A Storage Spaces plugin row: one Storage Spaces setting shown in Kirmod's settings screen.
 *
 * Storage Spaces (StorageData, bundled in this plugin) stays the owner of the value: its own Settings tab
 * and config/StorageSpaces.cfg. This module reads the value every frame (read()), and a change made in
 * Kirmod is written back (write()) and saved to StorageSpaces.cfg. So both screens always show the same
 * thing, and the copy Kirmod keeps in Kirmod.cfg is only a mirror. A slider being dragged is written once
 * it is let go.
 *
 * Kinds of rows: lists of names (cycling buttons), ranges (sliders), Off/On checkboxes and actions (a
 * single value; a click runs it, see Action).
 */
public abstract class KirStorageModule extends KirModule
{
    private final String category;
    private final String[] values;
    private final boolean slider;
    private final String[] tooltip;

    /** A slider moved while the mouse is still down; written when it is let go. */
    private boolean pending;

    /** Every row, so all are synced after a write (Kirmod runs no module updates at the main menu). */
    private static final List ALL = new ArrayList();

    protected KirStorageModule(String key, String title, int defaultValue, String category, String[] values, boolean slider, String[] tooltip)
    {
        super(key, title, title, defaultValue, null);
        this.category = category;
        this.values = values;
        this.slider = slider;
        this.tooltip = tooltip;
        ALL.add(this);
    }

    /** Storage Spaces' current value, as an index into the values. */
    protected abstract int read();

    /** Sets Storage Spaces' value to the one at index. */
    protected abstract void write(int index);

    public String[] getValueNames()
    {
        return this.values;
    }

    public boolean isSlider()
    {
        return this.slider;
    }

    public String getCategory()
    {
        return this.category;
    }

    public String[] getTooltip()
    {
        return this.tooltip;
    }

    /** Kirmod.cfg's copy may be old: StorageSpaces.cfg wins. */
    public void install()
    {
        this.sync();
    }

    public void update(Minecraft mc)
    {
        if (this.pending)
        {
            if (!Mouse.isCreated() || !Mouse.isButtonDown(0))
            {
                this.pending = false;
                this.push();
            }

            return;
        }

        this.sync();
    }

    protected void onValueChanged()
    {
        Minecraft mc = ModLoader.getMinecraftInstance();

        if (this.slider && mc != null && mc.currentScreen instanceof GuiKirmod && Mouse.isCreated() && Mouse.isButtonDown(0))
        {
            this.pending = true;
        }
        else
        {
            this.push();
        }
    }

    private void push()
    {
        this.write(this.getValue());
        StorageData.saveSettings();
        // Counts depend on settings such as Containers counted: recompute them.
        StorageData.version++;

        for (int i = 0; i < ALL.size(); ++i)
        {
            KirStorageModule m = (KirStorageModule)ALL.get(i);

            if (!m.pending)
            {
                m.sync();
            }
        }
    }

    /** Takes Storage Spaces' value (changed in its own screen). */
    private void sync()
    {
        int v = this.read();

        if (v != this.getValue() && v >= 0 && v < this.getValueNames().length)
        {
            this.loadValue(v);
        }
    }

    /* ------------------------------------------------------------------ Kinds */

    static final String[] OFF_ON = new String[] {"Off", "On"};

    /** An Off/On setting. */
    abstract static class Toggle extends KirStorageModule
    {
        Toggle(String key, String title, boolean def, String category, String ... tooltip)
        {
            super(key, title, def ? 1 : 0, category, OFF_ON, false, tooltip);
        }

        abstract boolean get();

        abstract void set(boolean v);

        protected int read()
        {
            return this.get() ? 1 : 0;
        }

        protected void write(int index)
        {
            this.set(index == 1);
        }
    }

    /** A setting that is an index into names (a cycling button). */
    abstract static class Choice extends KirStorageModule
    {
        Choice(String key, String title, int def, String category, String[] names, String ... tooltip)
        {
            super(key, title, def, category, names, false, tooltip);
        }

        abstract int get();

        abstract void set(int v);

        protected int read()
        {
            return this.get();
        }

        protected void write(int index)
        {
            this.set(index);
        }
    }

    /** A number from min to max in steps of step (a slider); value i is min + i * step. */
    abstract static class Range extends KirStorageModule
    {
        final int min;
        final int step;

        Range(String key, String title, int min, int max, int step, int def, String suffix, String zeroName, String category, String ... tooltip)
        {
            super(key, title, (def - min) / step, category, names(min, max, step, suffix, zeroName), true, tooltip);
            this.min = min;
            this.step = step;
        }

        abstract int get();

        abstract void set(int v);

        protected int read()
        {
            return (this.get() - this.min) / this.step;
        }

        protected void write(int index)
        {
            this.set(this.min + index * this.step);
        }

        private static String[] names(int min, int max, int step, String suffix, String zeroName)
        {
            String[] out = new String[(max - min) / step + 1];

            for (int i = 0; i < out.length; ++i)
            {
                int v = min + i * step;
                out[i] = v == 0 && zeroName != null ? zeroName : v + suffix;
            }

            return out;
        }
    }

    /**
     * A row that does something when clicked instead of holding a value ("Open...", "Rescan Now"). Its one
     * value is the text at the right of the row, which may change; it is greyed out while there is no world
     * when it needs one.
     */
    public abstract static class Action extends KirModule
    {
        private final String category;
        private final String[] tooltip;
        private final boolean needsWorld;

        protected Action(String key, String title, String category, boolean needsWorld, String ... tooltip)
        {
            super(key, title, title, 0, null);
            this.category = category;
            this.needsWorld = needsWorld;
            this.tooltip = tooltip;
        }

        /** The text at the right of the row. */
        protected abstract String getLabel();

        protected abstract void run(Minecraft mc);

        public String[] getValueNames()
        {
            return new String[] {this.getLabel()};
        }

        public String getCategory()
        {
            return this.category;
        }

        public String[] getTooltip()
        {
            return this.tooltip;
        }

        public String getLockedMessage()
        {
            Minecraft mc = ModLoader.getMinecraftInstance();
            return this.needsWorld && (mc == null || mc.theWorld == null || mc.thePlayer == null) ? "Join a world first." : null;
        }

        /** A click (either button) runs it. */
        public void cycleValue(int direction)
        {
            Minecraft mc = ModLoader.getMinecraftInstance();

            if (mc != null && this.getLockedMessage() == null)
            {
                this.run(mc);
            }
        }

        /** "/kirmod KEY anything" runs it too; there is no value to set. */
        public void setValue(int value)
        {
            this.cycleValue(1);
        }

        /** Reset to Defaults has nothing to reset. */
        public void resetValue() {}

        public String getDisplayString()
        {
            return this.getTitle();
        }
    }
}

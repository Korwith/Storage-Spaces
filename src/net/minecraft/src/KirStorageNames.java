package net.minecraft.src;

/** The Storage Spaces plugin's tab and group names (groups are labelled sections of the one tab). */
final class KirStorageNames
{
    static final String TAB = "Storage Spaces";
    static final String COUNTING = "Counting";
    static final String WORLD = "In the World";
    static final String LOCATE = "Locate";
    static final String SELECTION = "Selection";
    static final String LOOK = "Charts";

    private KirStorageNames() {}

    static boolean isGroup(String category)
    {
        return COUNTING.equals(category) || WORLD.equals(category) || LOCATE.equals(category) || SELECTION.equals(category) || LOOK.equals(category);
    }
}

package mezz.jei.bookmarks;

import mezz.jei.Internal;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.autocrafting.IngredientUtil;
import mezz.jei.autocrafting.RecipeBookmarkItem;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.IngredientListElementFactory;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.ingredients.group.CollapsedGroupIngredient;
import mezz.jei.ingredients.group.CollapsibleGroup;
import mezz.jei.startup.ForgeModIdHelper;
import mezz.jei.util.Log;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.Collection;

public class BookmarkItem<I> {
    @SuppressWarnings("rawtypes")
    public static final IIngredientType<BookmarkItem> TYPE = () -> BookmarkItem.class;

    public I ingredient;
    public long amount = 0L;
    @Nullable
    protected BookmarkGroup group;

    protected static final String MARKER_OTHER = "O:";
    protected static final String MARKER_STACK = "T:";
    protected static final String MARKER_COLLAPSED_GROUP = "G:";
    private static final char MARKER_NORMAL = 'B';
    protected static final char MARKER_RECIPE = 'R';

    public BookmarkItem(I ingredient) {
        this.ingredient = IngredientUtil.normalizeCopy(ingredient);
    }

    public BookmarkItem<I> copy() {
        return new BookmarkItem<>(ingredient);
    }

    @Nullable
    public IIngredientListElement<I> getSavedElement() {
        IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
        IIngredientType<I> ingredientType = ingredientRegistry.getIngredientType(ingredient);
        return IngredientListElementFactory.createUnorderedElement(
                ingredientRegistry,
                ingredientType,
                ingredient,
                ForgeModIdHelper.getInstance());
    }

    public int getGroupIndex() {
        return getGroup() == null ? 0 : getGroup().id;
    }

    public void changeAmount(long delta) {
        // Make sure the amount reaches a multiple of the delta (it acts as a step).
        this.amount = Math.round(this.amount / delta) * delta;
        this.amount += delta;
        this.amount = Math.max(0, this.amount);
    }

    public boolean startsNewRow() {
        return false;
    }

    public long getDisplayAmount() {
        return amount;
    }

    public boolean deserialize(NBTTagCompound tag) {
        this.amount = tag.getLong("amount");
        return true;
    }

    @Nullable
    public String serialize() {
        if (ingredient instanceof CollapsedGroupIngredient) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("id", ((CollapsedGroupIngredient) ingredient).getId());
            tag.setLong("amount", amount);
            return MARKER_NORMAL + MARKER_COLLAPSED_GROUP + tag;
        }
        NBTTagCompound tag = getNBTOfIngredient(ingredient);
        if (tag == null) {
            return null;
        }
        tag.setLong("amount", amount);
        if (ingredient instanceof ItemStack) {
            return MARKER_NORMAL + MARKER_STACK + tag;
        } else {
            return MARKER_NORMAL + MARKER_OTHER + tag;
        }
    }

    protected NBTTagCompound getNBTOfIngredient(Object ingredient) {
        if (ingredient instanceof ItemStack) {
            return ((ItemStack) ingredient).writeToNBT(new NBTTagCompound());
        } else {
            IIngredientListElement<?> listElement = this.getSavedElement();
            if (listElement == null) {
                return null;
            }
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("uid", Internal.getIngredientRegistry().getUniqueId(listElement.getIngredient()));
            return tag;
        }
    }

    @Nullable
    public static BookmarkItem<?> deserialize(String ingredientJsonString, Collection<IIngredientType> otherIngredientTypes) {
        Object ingredient = parseIngredient(ingredientJsonString.substring(1), otherIngredientTypes);
        BookmarkItem<?> item;

        if (ingredient == null) {
            // Old format; deserialize whole string as ingredient and add as bookmark item
            ingredient = parseIngredientOld(ingredientJsonString, otherIngredientTypes);
            if (ingredient == null) {
                return null;
            }
            item = new BookmarkItem<>(ingredient);

            // Don't try and deserialize; old format has no amount, and may not be nbt
            return item;
        } else {
            switch (ingredientJsonString.charAt(0)) {
                case MARKER_NORMAL:
                    item = new BookmarkItem<>(ingredient);
                    break;
                case MARKER_RECIPE:
                    item = new RecipeBookmarkItem<>(ingredient);
                    break;
                default:
                    return null;
            }
        }

        if (item.deserialize(getNBT(ingredientJsonString))) {
            return item;
        }
        return null;
    }

    @Nullable
    protected static Object parseIngredient(String ingredientJsonString, Collection<IIngredientType> otherIngredientTypes) {
        if (ingredientJsonString.startsWith(MARKER_STACK)) {
            NBTTagCompound parsed = getNBT(ingredientJsonString);
            if (parsed != null) {
                ItemStack itemStack = new ItemStack(parsed);
                if (!itemStack.isEmpty()) {
                    IngredientUtil.normalize(itemStack);
                    return itemStack;
                } else {
                    Log.get().warn("Failed to load bookmarked ItemStack, the item no longer exists:\n{}", parsed);
                }
            }
        } else if (ingredientJsonString.startsWith(MARKER_COLLAPSED_GROUP)) {
            NBTTagCompound parsed = getNBT(ingredientJsonString);
            if (parsed != null) {
                String groupId = parsed.getString("id");
                CollapsibleGroup group = Internal.getCollapsedGroupRegistry().getAllGroups().get(groupId);
                if (group != null) {
                    return group.getIngredient();
                } else {
                    Log.get().warn("Failed to load bookmarked collapsible group, group no longer exists: {}", groupId);
                }
            }
        } else if (ingredientJsonString.startsWith(MARKER_OTHER)) {
            NBTTagCompound parsed = getNBT(ingredientJsonString);
            if (parsed != null) {
                Object ingredient = getUnknownIngredientByUid(otherIngredientTypes, parsed.getString("uid"));
                if (ingredient != null) {
                    IngredientUtil.normalize(ingredient);
                    return ingredient;
                } else {
                    Log.get().warn("Failed to load bookmarked unknown ingredient, the ingredient no longer exists:\n{}", parsed);
                }
            }
        }
        return null;
    }

    @Nullable
    protected static Object parseIngredientOld(String ingredientJsonString, Collection<IIngredientType> otherIngredientTypes) {
        if (ingredientJsonString.startsWith(MARKER_STACK)) {
            // Use normal parsing
            return parseIngredient(ingredientJsonString, otherIngredientTypes);
        } if (ingredientJsonString.startsWith(MARKER_OTHER)) {
            Object ingredient = getUnknownIngredientByUid(otherIngredientTypes, ingredientJsonString.substring(2));
            if (ingredient != null) {
                IngredientUtil.normalize(ingredient);
                return ingredient;
            } else {
                Log.get().warn("Failed to load bookmarked unknown ingredient, the ingredient no longer exists:\n{}", ingredientJsonString.substring(2));
            }
        }
        return null;
    }

    @Nullable
    private static Object getUnknownIngredientByUid(Collection<IIngredientType> ingredientTypes, String uid) {
        IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
        for (IIngredientType<?> ingredientType : ingredientTypes) {
            Object ingredient = ingredientRegistry.getIngredientByUid(ingredientType, uid);
            if (ingredient != null) {
                return ingredient;
            }
        }
        return null;
    }


    @Nullable
    private static NBTTagCompound getNBT(String ingredientString) {
        int colonAfterMarker = ingredientString.indexOf(':');
        if (colonAfterMarker < 0) {
            Log.get().error("Bookmark ingredient parsing error: missing separator ':' in bookmark string:\n{}", ingredientString);
            return null;
        }
        try {
            return JsonToNBT.getTagFromJson(ingredientString.substring(colonAfterMarker + 1));
        } catch (NBTException e) {
            Log.get().error("Failed to parse bookmarked ingredient from JSON:\n{}", ingredientString, e);
            return null;
        }
    }

    public <O> void setIngredient(O ingredient) {
        if (this.ingredient.getClass().isAssignableFrom(ingredient.getClass())) { // Incredible instanceof
            this.ingredient = (I) ingredient;
        }
    }

    public void setGroup(@Nullable BookmarkGroup group) {
        this.group = group;
    }

    @Nullable
    public BookmarkGroup getGroup() {
        return group;
    }
}

package mezz.jei.bookmarks;

import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.function.LongSupplier;

/**
 * One ingredient slot of a recipe row in the bookmark overlay.
 * <p>
 * These stand for a recipe's ingredients rather than for bookmarks the player made,
 * thus they are not saved nor editable.
 */
public class DummyBookmarkItem<I> extends BookmarkItem<I> {

	private final LongSupplier displayAmount;

	public DummyBookmarkItem(I ingredient, @Nullable BookmarkGroup group, LongSupplier displayAmount) {
		super(ingredient);
		this.setGroup(group);
		this.displayAmount = displayAmount;
	}

	public DummyBookmarkItem(I ingredient, @Nullable BookmarkGroup group, long displayAmount) {
		this(ingredient, group, () -> displayAmount);
	}

	@Override
	public void changeAmount(long delta) {
	}

	@Override
	public long getDisplayAmount() {
		return displayAmount.getAsLong();
	}

	@Override
	@Nullable
	public String serialize() {
		return null;
	}

	@Override
	public boolean deserialize(NBTTagCompound ingredientJsonString) {
		return false;
	}
}

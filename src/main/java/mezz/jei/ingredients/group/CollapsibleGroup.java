package mezz.jei.ingredients.group;

public class CollapsibleGroup {

    private final CollapsedGroupIngredient ingredient;

    private boolean enabled = true;

    CollapsibleGroup(CollapsedGroupIngredient ingredient) {
        this.ingredient = ingredient;
    }

    public CollapsedGroupIngredient getIngredient() {
        return ingredient;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}

package mezz.jei.api.recipe.transfer;

public interface IAutocraftingHandler {
    /**
     * Inform JEI that the player has finished its current autocrafting step.
     * @param success If the autocrafting was successful at all.
     * @param amount The number of recipes that were completed.
     */
    void informOfAutocrafting(boolean success, int amount);
}

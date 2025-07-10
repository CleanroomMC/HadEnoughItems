package mezz.jei.api.recipe.transfer;

/**
 * HEI's handler for current autocrafting. Keeps track of the recipes required to complete a recipe chain,
 * and listens for when steps are completed.
 * @since HEI 4.28.0
 */
public interface IAutocraftingHandler {
    /**
     * Inform JEI that the player has finished its current autocrafting step.
     * @param success If the autocrafting was successful at all; if false, the autocrafting will be stopped.
     * @param amount The number of recipes that were completed.
     */
    void informOfAutocrafting(boolean success, int amount);
}

package mezz.jei.api.recipe.transfer;

/**
 * HEI's handler for current autocrafting. Keeps track of the recipes required to complete a recipe chain,
 * and listens for when steps are completed.
 * @since HEI 4.28.0
 */
public interface IAutocraftingHandler {
    /**
     * Inform HEI that the player has finished its current autocrafting step.
     * @param success If the autocrafting was successful at all; if false, the autocrafting will be stopped.
     * @param amount The number of recipes that were completed.
     */
    void stepFinished(boolean success, int amount);

    /**
     * Returns true if HEI is currently autocrafting.
     */
    boolean isActive();

    /**
     * Stop the current autocrafting process; other than potentially some server messages, no other action will be taken.
     */
    void stop();
}

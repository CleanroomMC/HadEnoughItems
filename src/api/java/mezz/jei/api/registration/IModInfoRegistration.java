package mezz.jei.api.registration;

import java.util.Arrays;
import java.util.Collection;

/**
 * Registers additional information that helps JEI identify a mod.
 *
 * @since HEI 4.37.0
 */
public interface IModInfoRegistration {
	/**
	 * Register alternative mod names used for searching.
	 *
	 * @since HEI 4.37.0
	 */
	void addModAliases(String modId, Collection<String> aliases);

	/**
	 * Register alternative mod names used for searching.
	 *
	 * @since HEI 4.37.0
	 */
	default void addModAliases(String modId, String... aliases) {
		addModAliases(modId, Arrays.asList(aliases));
	}
}

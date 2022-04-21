package mezz.jei.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Log {
	private static final Logger LOGGER = LogManager.getLogger("HadEnoughItems");

	public static Logger get() {
		return LOGGER;
	}

	private Log() {
	}
}

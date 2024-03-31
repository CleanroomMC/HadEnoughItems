package mezz.jei.util;

import mezz.jei.Tags;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Log {
	private static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

	public static Logger get() {
		return LOGGER;
	}

	private Log() {
	}
}

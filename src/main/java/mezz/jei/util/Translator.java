package mezz.jei.util;

import java.util.IllegalFormatException;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;
import net.minecraft.client.resources.LanguageManager;
import net.minecraft.util.text.translation.I18n;

import javax.annotation.Nullable;

public final class Translator {

	@Nullable
	private static Locale locale;

	private Translator() {
	}

	public static String translateToLocal(String key) {
		if (I18n.canTranslate(key)) {
			return I18n.translateToLocal(key);
		} else {
			return I18n.translateToFallback(key);
		}
	}

	public static String translateToLocalFormatted(String key, Object... format) {
		String s = translateToLocal(key);
		try {
			return String.format(s, format);
		} catch (IllegalFormatException e) {
			Log.get().error("Format error: {}", s, e);
			return "Format error: " + s;
		}
	}

	public static String toLowercaseWithLocale(String string) {
		return string.toLowerCase(getLocale());
	}

	public static void invalidateLocale() {
		locale = null;
	}

	@SuppressWarnings("ConstantConditions")
	private static Locale getLocale() {
		if (locale == null) {
			Minecraft minecraft = Minecraft.getMinecraft();
			if (minecraft != null) {
				LanguageManager languageManager = minecraft.getLanguageManager();
				if (languageManager != null) {
					Language currentLanguage = languageManager.getCurrentLanguage();
					if (currentLanguage != null) {
						locale = currentLanguage.getJavaLocale();
						return locale;
					}
				}
			}
			locale = Locale.getDefault();
		}
		return locale;
	}
}

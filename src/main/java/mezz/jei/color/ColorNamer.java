package mezz.jei.color;

import javax.annotation.Nullable;
import java.awt.Color;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import mezz.jei.util.Translator;

public class ColorNamer {
	private final ImmutableMap<Color, String> colorNames;

	public ColorNamer(ImmutableMap<Color, String> colorNames) {
		this.colorNames = colorNames;
	}

	public Collection<String> getColorNames(Iterable<Color> colors, boolean lowercase) {
		final Set<String> allColorNames = new LinkedHashSet<>();
		for (Color color : colors) {
			final String colorName = getClosestColorName(color);
			if (colorName != null) {
				if (lowercase) {
					allColorNames.add(Translator.toLowercaseWithLocale(colorName));
				} else {
					allColorNames.add(colorName);
				}
			}
		}
		return allColorNames;
	}

	@Nullable
	private String getClosestColorName(Color color) {
		if (colorNames.isEmpty()) {
			return null;
		}

		String closestColorName = null;
		double closestColorDistance = Double.MAX_VALUE;

		for (Map.Entry<Color, String> entry : colorNames.entrySet()) {
			final Color namedColor = entry.getKey();
			final double distance = ColorUtil.slowPerceptualColorDistanceSquared(namedColor, color);
			if (distance < closestColorDistance) {
				closestColorDistance = distance;
				closestColorName = entry.getValue();
			}
		}

		return closestColorName;
	}
}

package mezz.jei.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import mezz.jei.ingredients.group.CollapsedGroupIngredient;
import mezz.jei.util.Log;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages custom collapsible groups persistence as JSON.
 * File: config/jei/customCollapsibleGroups.json
 */
public class CustomGroupsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type GROUP_LIST_TYPE = new TypeToken<List<CustomGroup>>() {}.getType();

	private final File configFile;
	private Map<String, CustomGroup> customGroups = new LinkedHashMap<>();

	public CustomGroupsConfig(File configDir) {
		this.configFile = new File(configDir, "customCollapsibleGroups.json");
	}

	public void load() {
		customGroups = new LinkedHashMap<>();
		if (!configFile.exists()) {
			return;
		}
		try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
			List<CustomGroup> loaded = GSON.fromJson(reader, GROUP_LIST_TYPE);
			if (loaded != null) {
				for (CustomGroup group : loaded) {
					customGroups.put(group.id, group);
				}
			}
		} catch (Exception e) {
			Log.get().error("Failed to load custom collapsible groups from {}", configFile, e);
			customGroups = new LinkedHashMap<>();
		}
	}

	public void save() {
		try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
			GSON.toJson(new ArrayList<>(customGroups.values()), GROUP_LIST_TYPE, writer);
		} catch (Exception e) {
			Log.get().error("Failed to save custom collapsible groups to {}", configFile, e);
		}
	}

	public Collection<CustomGroup> getCustomGroups() {
		return Collections.unmodifiableCollection(customGroups.values());
	}

	@Nullable
	public CustomGroup getGroup(String id) {
		return customGroups.get(id);
	}

	public void addGroup(CustomGroup group) {
		customGroups.put(group.id, group);
		save();
	}

	public void removeGroup(String id) {
		customGroups.remove(id);
		save();
	}

	public void updateGroup(CustomGroup updated) {
		customGroups.put(updated.id, updated);
		save();
	}

	/**
	 * A user-defined collapsible group stored as JSON.
	 * Items are identified by their unique identifier string from StackHelper.
	 */
	public static class CustomGroup {
		public String id;
		public String displayName;
		public int backgroundColor;
		public int borderColor;
		public List<String> itemUids;

		public CustomGroup() {
			this.id = "";
			this.displayName = "";
			this.backgroundColor = CollapsedGroupIngredient.BACKGROUND_COLOR_SMOKE;
			this.borderColor = CollapsedGroupIngredient.BORDER_COLOR_SMOKE;
			this.itemUids = new ArrayList<>();
		}

		public CustomGroup(String id, String displayName, int backgroundColor, int borderColor, List<String> itemUids) {
			this.id = id;
			this.displayName = displayName;
			this.backgroundColor = backgroundColor;
			this.borderColor = borderColor;
			this.itemUids = new ArrayList<>(itemUids);
		}

		public CustomGroup copy() {
			return new CustomGroup(id, displayName, backgroundColor, borderColor, new ArrayList<>(itemUids));
		}
	}
}

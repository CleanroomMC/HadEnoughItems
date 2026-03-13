package mezz.jei.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import mezz.jei.util.Log;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages custom collapsible groups persistence as JSON.
 * File: config/jei/customCollapsibleGroups.json
 */
public class CustomGroupsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type GROUP_LIST_TYPE = new TypeToken<List<CustomGroup>>() {}.getType();

	private final File configFile;
	private List<CustomGroup> customGroups = new ArrayList<>();

	public CustomGroupsConfig(File configDir) {
		this.configFile = new File(configDir, "customCollapsibleGroups.json");
	}

	public void load() {
		if (!configFile.exists()) {
			customGroups = new ArrayList<>();
			return;
		}
		try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
			List<CustomGroup> loaded = GSON.fromJson(reader, GROUP_LIST_TYPE);
			customGroups = loaded != null ? loaded : new ArrayList<>();
		} catch (Exception e) {
			Log.get().error("Failed to load custom collapsible groups from {}", configFile, e);
			customGroups = new ArrayList<>();
		}
	}

	public void save() {
		try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
			GSON.toJson(customGroups, GROUP_LIST_TYPE, writer);
		} catch (Exception e) {
			Log.get().error("Failed to save custom collapsible groups to {}", configFile, e);
		}
	}

	public List<CustomGroup> getCustomGroups() {
		return customGroups;
	}

	public void addGroup(CustomGroup group) {
		customGroups.add(group);
		save();
	}

	public void removeGroup(String id) {
		customGroups.removeIf(g -> g.id.equals(id));
		save();
	}

	public void updateGroup(CustomGroup updated) {
		for (int i = 0; i < customGroups.size(); i++) {
			if (customGroups.get(i).id.equals(updated.id)) {
				customGroups.set(i, updated);
				save();
				return;
			}
		}
		// Not found — add as new
		addGroup(updated);
	}

	/**
	 * A user-defined collapsible group stored as JSON.
	 * Items are identified by their unique identifier string from StackHelper.
	 */
	public static class CustomGroup {
		public String id;
		public String displayName;
		public List<String> itemUids;

		public CustomGroup() {
			this.id = "";
			this.displayName = "";
			this.itemUids = new ArrayList<>();
		}

		public CustomGroup(String id, String displayName, List<String> itemUids) {
			this.id = id;
			this.displayName = displayName;
			this.itemUids = new ArrayList<>(itemUids);
		}

		public CustomGroup copy() {
			return new CustomGroup(id, displayName, new ArrayList<>(itemUids));
		}
	}
}

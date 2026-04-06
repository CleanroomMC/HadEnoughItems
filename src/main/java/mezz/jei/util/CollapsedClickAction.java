package mezz.jei.util;

/**
 * Controls what a plain left-click on a collapsed group slot does.
 *
 * OPEN_GROUP  — click opens/expands the group; Alt+Click applies the action to the first item.
 * FIRST_ITEM  — click applies the action to the first item in the group; Alt+Click expands/collapses.
 */
public enum CollapsedClickAction {
	OPEN_GROUP,
	FIRST_ITEM
}

# Changelog

## [4.33.0] - 2026-08-04

### Added
- Better keybind selection for adding bookmarks
- Allow mods to expose a slot's ingredient type through providers (thanks @romanfedyniak!)
- New search index system, powered by mezz's new library
- Search index replacement API
- Animation when adding bookmark ingredients
- Ability to disable categories through config

### Fixed
- Dedicated servers not able to run when autocrafting
- HEI keybinds were being activated when the text field was focused
- Collapsible groups affecting previous/next buttons, making them act weird
- Previous/next buttons now abide `hideOnSinglePage`, enabled only when there are adjacent pages (thanks @default-dx!)
- Allow previous/next page buttons to wrap on navigating when it isn't 1/1
- Avoid consuming scroll event if mouse is in exclusion area (thanks @ZZZank!)
- Token related issues
- Blacklisting issues when they are added/removed at runtime

### Changed
- Rewritten autocrafting and bookmarking
- Initialize the bookmark menu only when there are bookmarks present
- Move to the most logical page when adding/removing bookmarks
- Improve navigation layout around GUI exclusion areas (thanks @Circulate233!)
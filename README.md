[![](http://cf.way2muchnoise.eu/full_557549_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/had-enough-items) [![Discord](https://img.shields.io/discord/926486493562814515.svg?colorB=7289DA&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAHYAAABWAgMAAABnZYq0AAAACVBMVEUAAB38%2FPz%2F%2F%2F%2Bm8P%2F9AAAAAXRSTlMAQObYZgAAAAFiS0dEAIgFHUgAAAAJcEhZcwAACxMAAAsTAQCanBgAAAAHdElNRQfhBxwQJhxy2iqrAAABoElEQVRIx7WWzdGEIAyGgcMeKMESrMJ6rILZCiiBg4eYKr%2Fd1ZAfgXFm98sJfAyGNwno3G9sLucgYGpQ4OGVRxQTREMDZjF7ILSWjoiHo1n%2BE03Aw8p7CNY5IhkYd%2F%2F6MtO3f8BNhR1QWnarCH4tr6myl0cWgUVNcfMcXACP1hKrGMt8wcAyxide7Ymcgqale7hN6846uJCkQxw6GG7h2MH4Czz3cLqD1zHu0VOXMfZjHLoYvsdd0Q7ZvsOkafJ1P4QXxrWFd14wMc60h8JKCbyQvImzlFjyGoZTKzohwWR2UzSONHhYXBQOaKKsySsahwGGDnb%2FiYPJw22sCqzirSULYy1qtHhXGbtgrM0oagBV4XiTJok3GoLoDNH8ooTmBm7ZMsbpFzi2bgPGoXWXME6XT%2BRJ4GLddxJ4PpQy7tmfoU2HPN6cKg%2BledKHBKlF8oNSt5w5g5o8eXhu1IOlpl5kGerDxIVT%2BztzKepulD8utXqpChamkzzuo7xYGk%2FkpSYuviLXun5bzdRf0Krejzqyz7Z3p0I1v2d6HmA07dofmS48njAiuMgAAAAASUVORK5CYII%3D)](https://discord.gg/f2K4aSpG4F)

# HadEnoughItems (HEI)
[HadEnoughItems](https://discord.gg/f2K4aSpG4F) is an Item and Recipe viewing mod for Minecraft with a focus on stability, performance, and ease of use.

This means (same as JEI of course):
 * Just ingredients and recipes
 * Clean API for developers
 * Not a coremod – no dependencies other than Forge.
 
 New features in HEI:
 * Memory optimizations
 * Load time optimizations
 * Render optimizations
 * Autocrafting
 * Bookmark and Favoriting of Recipes
 * Able to change default fluid containers for when you pick up fluids from the ingredient menu
 * Able to fill fluid containers by clicking on fluids in the ingredient menu
 * Able to order bookmarks differently
 * Consider diacritics when searching
 * Removable "Recipe By" tooltip in recipe menu
 * More information available in recipe tabs
 * Better ordering of recipes in certain contexts
 * Better rendering of ingredient amounts to improve clarity
 * Bookmark Groups
 * Collapsible Groups

### Keybinds

All keys below are rebindable under **Options => Controls => Had Enough Items (HEI)**. They only apply while a GUI is open.

#### Bookmarks and Groups

Point at an ingredient or a recipe and press one of these. Whether you bookmark an *ingredient* or a *recipe* is decided by what is under the cursor, not by which key you press — the same key does both.

| Key                | Purpose                                                                                    |
|--------------------|--------------------------------------------------------------------------------------------|
| `A`                | Bookmarks the hovered ingredient or recipe to the **end** of the current bookmark group.   |
| `Shift + A`        | Bookmarks the hovered ingredient or recipe to the **start** of the current bookmark group. |
| `Ctrl + A`         | Bookmarks the hovered ingredient into a **new group of its own**, at the **end**.          |
| `Ctrl + Shift + A` | Bookmarks the hovered ingredient into a **new group of its own**, at the **start**.        |

Pressing any of these keys on something that is *already* bookmarked, removes it. Bookmarking an ingredient you already have bookmarked elsewhere moves it to whichever end you asked for rather than adding a second copy. Recipes always get a group of their own, so `Ctrl` makes no difference to them.

The bookmark overlay opens by itself the first time you bookmark something, and closes when you remove the last one.

#### Bookmark Groups

Point anywhere along a group's rows for these:

| Key                                 | What it does                                                                                                                                   |
|-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| `Shift + Up`                        | Moves the group up one place.                                                                                                                  |
| `Shift + Down`                      | Moves the group down one place.                                                                                                                |
| `Shift + C`                         | Autocrafts a recipe group. Requires autocrafting to be enabled in the config.                                                                  |
| `Ctrl + Left Click`                 | Drags the whole group rather than a single bookmark, which is how you move bookmarks between groups.                                           |
| `Ctrl + Scroll`                     | Cycles the amount of the final output of a recipe chain.                                                                                       |
| `A` **on the group's coloured tab** | **Deletes the whole group.** No confirmation, no undo. Restricted to the narrow tab on the left so it can't be hit while aiming at a bookmark. |

Hold **Alt** while hovering a group to list its actions in the tooltip.

#### Other

`Ctrl + O` shows and hides HEI. **Show/Hide Bookmarked Ingredients** toggles just the bookmark overlay and has no key assigned by default.

### For Devs:
Add CleanroomMC's repository and depend on HEI's maven entry:
```groovy
repositories {
    maven {
        url 'https://maven.cleanroommc.com'
    }
}

dependencies {
    implementation 'mezz:jei:4.34.1'
}
```

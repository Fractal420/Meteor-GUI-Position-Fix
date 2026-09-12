# Meteor GUI Position Fix

A client-side Meteor Client addon for Minecraft 1.21.11 that keeps your ClickGUI organized the way you left it.



https://github.com/user-attachments/assets/5ccbb9a4-af4f-4200-8d69-433fea4ab1b1



## Features

* **Stable category positions**  
  Category windows stay exactly where you place them. Collapsed windows use their actual visible size, so reopening the ClickGUI no longer shifts them based on full expanded height.

* **Smart overlap handling**  
  When you drop a category on top of another one, only the window you dragged is moved to the nearest free spot. Other windows are never pushed aside.

* **Top bar protection**  
  Category windows are kept below the top bar so they never cover Meteor’s settings and controls.

* **Remembered positions**  
  Window positions are saved and restored when you close/reopen the ClickGUI and across game restarts.

* **Fixed search window size**  
  The search window keeps a stable size. Filtering modules no longer resizes it or rearranges other windows. Contents still scroll normally.

* **Reliable dropdown clicks**  
  Expanded setting dropdowns receive clicks first, even when they overlap other settings. Works for both module settings and Meteor’s own settings.

* **Catppuccin theme support**  
  Fully compatible with [Catppuccin](https://github.com/X-C-0/catppuccin-addon) themes. Category windows can be freely dragged (including below the middle of the screen) and keep correct positions after release.

## Installation

1. Install the matching version of **Meteor Client** for Minecraft 1.21.11.
2. Download the `Meteor GUI Position Fix` JAR.
3. Place the JAR in your Minecraft `mods` folder alongside Meteor.
4. Launch the game and open the Meteor ClickGUI.

No configuration is required.

## Building from source

Before building, place your **Meteor Client 1.21.11** JAR in the project's `libs` folder and rename it to:

```text
meteor-client-1.21.11-local.jar
```

Then run:

```bash
./gradlew clean build
```

The built addon JAR will be available in:

```text
build/libs/
```

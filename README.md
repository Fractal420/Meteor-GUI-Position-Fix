# Meteor GUI Position Fix

A client-side Meteor addon for Minecraft 1.21.11

## What it fixes

This mod fixes several annoying issues with Meteor's ClickGUI category windows:

* **Collapsed categories stay where you put them.** Reopening the ClickGUI no longer moves collapsed categories based on their full expanded size. The mod uses their actual visible size to keep them in the correct position.
* **Categories no longer overlap.** When you release a category on top of another one, it is automatically moved to the closest available space instead.
* **Categories stay below the top bar.** Category windows are prevented from moving over the top bar where Meteor's settings and other controls are located.
* **Positions are remembered.** Category positions persist when closing and reopening the ClickGUI, and across game restarts.

Preview: 

https://github.com/user-attachments/assets/1bb8f888-2d3f-47eb-8866-c218c2826f7a


The goal is simple: **your ClickGUI stays organized the way you left it.**

## Installation

1. Install the matching version of **Meteor Client** for Minecraft 1.21.11.
2. Download the `Meteor GUI Position Fix` JAR.
3. Put the JAR in your Minecraft `mods` folder alongside Meteor.
4. Launch the game and open the Meteor ClickGUI.

No additional configuration is required.

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

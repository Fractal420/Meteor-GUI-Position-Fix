# Meteor GUI Position Fix

A client-side Meteor Client addon that keeps your ClickGUI organized the way you left it.

Supports Minecraft **1.21.11**, **26.1** and **26.2**.

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
  Fully compatible with Catppuccin themes. Category windows can be freely dragged and keep correct positions after release.

## Installation

1. Install the matching version of **Meteor Client** for your Minecraft version.
2. Download the `Meteor GUI Position Fix` JAR that matches your Minecraft version.
3. Place the JAR in your Minecraft `mods` folder alongside Meteor.
4. Launch the game and open the Meteor ClickGUI.

No configuration is required.

## Building from source

This is a multi-version project. Shared sources live in `src/`. Each Minecraft version is a subproject under `versions/`.

Build a specific version:

```bash
./gradlew build -Pmc=1.21.11
./gradlew build -Pmc=26.1
./gradlew build -Pmc=26.2
```

Or target the subproject directly:

```bash
./gradlew :1.21.11:build
./gradlew :26.1:build
./gradlew :26.2:build
```

Build every version:

```bash
./gradlew buildAll
```

Built JARs appear in `build/libs/` (when using `-Pmc=...`) and in `versions/<mc>/build/libs/`.

### Meteor Client dependency

The build resolves `meteor-client` from the official Meteor Maven (`https://maven.meteordev.org/snapshots`) as a compile-only dependency:

```
meteordevelopment:meteor-client:<minecraft_version>-SNAPSHOT
```

You normally do **not** need to place any Meteor JARs manually.

If the SNAPSHOT cannot be resolved (offline build, Maven outage, etc.), create a `libs/` folder in the project root and place a renamed Meteor Client JAR there, for example:

```text
libs/meteor-client-1.21.11-SNAPSHOT.jar
libs/meteor-client-26.1-SNAPSHOT.jar
libs/meteor-client-26.2-SNAPSHOT.jar
```

The `flatDir` repository will pick it up automatically.

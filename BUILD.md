# Building Chatrix

Use **JDK 25** to build the full supported range. The Gradle wrapper downloads Gradle and dependencies. Minecraft 1.21.x builds target Java 21 bytecode; 26.x builds target Java 25.

## All supported versions

On Windows:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-supported.ps1
```

On Linux or macOS:

```sh
./gradlew :1.21.10:buildAndCollect :1.21.11:buildAndCollect :26.1:buildAndCollect :26.2:buildAndCollect :26.3:buildAndCollect --configure-on-demand --no-daemon
```

Jars are collected in `build/libs/2.2.0/`. Install the matching `chatrix-2.2.0+<minecraft>.jar`. Files ending in `-sources.jar` contain source code and are not installable mods. The `+26.1` jar also targets 26.1.1 and 26.1.2.

## One version

```powershell
.\gradlew.bat :1.21.11:buildAndCollect --configure-on-demand --no-daemon
```

Replace `1.21.11` with `1.21.10`, `26.1`, `26.2`, or `26.3` as needed.

## Development client

```powershell
.\gradlew.bat :1.21.11:runClient --configure-on-demand
```

The run directory is `run/`. The active source version is defined in `stonecutter.gradle.kts`. Switch it with:

```powershell
.\gradlew.bat "Set active project to 1.21.11"
```

## Verification

Build all five targets after changing shared code or compatibility conversions. Compilation verifies source compatibility and packaging, but does not test interactive behavior or live services.

In game, check `/chatrix test Hello!`, world and HUD modes, settings navigation, pin persistence, and history. Use actual platform channels to check live messages, emotes, badges, filters, sounds, and events.

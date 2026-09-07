# Vast Hall engine (GameInstance / GameMode / TimerManager / Events / SaveGame / Audio / Scene / Actor / Level / Component / Asset / Console)

Unreal mental model: **GameInstance owns long-lived services; OpenLevel selects a GameMode; the World owns Actors, a TimerManager, a multicast EventDispatcher, and an AudioManager; Actors own Components; named Levels stream into the World; the Asset Registry is the Content Browser–lite index; SaveGame snapshots a small JSON payload into a named slot; AudioManager plays registered `AUDIO` assets by id**. You do not `new` an actor and hope it ticks. You spawn it into a `World` (or load a level that does), which calls `beginPlay`, ticks it each frame, and calls `endPlay` on destroy. Destroying an actor detaches its components. You do not hardcode a one-off classpath read for each mesh or map — you register it, then look it up by id or path. Timers are tick-driven (`SetTimer`), not a second thread. Gameplay listeners use typed multicast delegates (`bind` / `unbind` / `broadcast`), not a Blueprint Event Dispatcher UI. Saves are slot files (`CreateSaveGameObject` / `SaveGameToSlot` / `LoadGameFromSlot`), not a full native serializer. Sounds are `PlaySound` / `PlaySound2D` against the Asset Registry, not a MediaPlayer one-off.

Native hall rendering and locomotion still live in `libvasthall.so`. This Java layer is the gameplay object model those natives can later attach to. **Transform stays on the Actor** (`actor.transform()`), not on a component — same as Unreal's root transform on `AActor`.

## Types

| Vast Hall | Unreal analog | Role |
| --- | --- | --- |
| `GameInstance` | `UGameInstance` | Owns World, AssetRegistry, Console, TimerManager, EventDispatcher, AudioManager, SaveGameSystem across travel |
| `GameMode` / `HallGameMode` | `AGameMode` | Per-level rules + default pawn hook + delayed-start timer + event binds |
| `TimerManager` / `TimerHandle` | `FTimerManager` / `FTimerHandle` | SetTimer by delay, loop, clear, pause/unpause |
| `EventDispatcher` / `MulticastDelegate` / `DelegateHandle` | Event Dispatcher / `FMulticastDelegate` / `FDelegateHandle` | Typed bind / unbind / broadcast |
| `EventType` | declared multicast / gameplay-message tag | `LevelLoaded`, `ActorSpawned`, … or `EventType.of(name, payload)` |
| `World` | `UWorld` | Spawn, destroy, tick, query, stream levels |
| `Actor` | `AActor` | Gameplay object with a transform |
| `ActorComponent` | `UActorComponent` | Behavior attached to an actor |
| `MovementComponent` | `UMovementComponent` stub | Adds `velocity * dt` to owner location |
| `TagComponent` | actor tags | Named tags for query/dump (tick off) |
| `Transform` | `FTransform` | Location, rotator (pitch/yaw/roll degrees), scale |
| `LevelDefinition` | map / streaming-level asset | Named list of actor templates |
| `Level` | loaded `ULevel` | Actors currently owned by one loaded map |
| `SaveGame` / `SaveGameSystem` | `USaveGame` + slot APIs | Create / save / load / does-exist / delete a JSON slot |
| `AudioManager` / `AudioDevice` | `UAudioDevice` + `PlaySound2D` | Play / stop registered `AUDIO` by id; master volume; silent foss device |
| `GameplayStatics` | `UGameplayStatics` | `loadLevel` / `unloadLevel` / `openLevel` / `getGameInstance` / `getGameMode` / `getTimerManager` / `setTimer` / `getEventDispatcher` / `bindEvent` / `findAsset` / `loadAsset` / `createSaveGame` / `saveGameToSlot` / `loadGameFromSlot` / `doesSaveGameExist` / `deleteGameInSlot` / `playSound2D` / `stopSound` / `setMasterVolume` |
| `AssetRegistry` | `UAssetManager` / Asset Registry | Register and look up content by id or path |
| `Asset` | registry row + loaded handle | `id`, `path`, `kind`, payload |
| `AssetKind` | asset class | `LEVEL`, `MESH`, `TEXTURE`, `AUDIO` |
| `MeshHandle` / `TextureHandle` / `AudioHandle` | stub `UObject`s | Named handles (no cook/decode yet) |
| `DeveloperConsole` | `~` console | Register and run named commands |
| `PlayerPawn` | default pawn | Java handle for the native player avatar (tick off) |
| `HallBeaconActor` | demo actor | Spawned by the Hall sample; bobs and yaws; resolves a texture in `beginPlay` |

Package: `com.elitesavior.vasthall.engine`.

## Startup flow

Unreal names, in order:

1. **Init** — construct the long-lived `GameInstance` (`GameInstance.withDemoAssets()`) and call `init()`.
2. **GameInstance** — owns one `World`, that world's `AssetRegistry`, `TimerManager`, `EventDispatcher`, `AudioManager`, a `SaveGameSystem`, and a `DeveloperConsole` bound to the world. Those objects survive map travel.
3. **OpenLevel** — `game.openLevel("Hall")` (or `GameplayStatics.openLevel(game, "Hall")`). Same-world travel: unload loaded streaming levels, then load the named map.
4. **GameMode** — after the map streams in, GameInstance constructs the mode from the level's `gameMode` field (or the instance default, `HallGameMode`), then `initGame` → `startPlay`. `startPlay` spawns `defaultPawnClass()` only if the world has none.

```java
import com.elitesavior.vasthall.engine.GameInstance;
import com.elitesavior.vasthall.engine.GameplayStatics;

GameInstance game = GameInstance.withDemoAssets();
game.init();
game.openLevel("Hall");                 // Play start
GameplayStatics.openLevel(game, "Hall"); // same path; also used by the console

game.world();                           // same World after travel
game.assets();                          // same AssetRegistry
game.console();                         // same DeveloperConsole
game.timerManager();                    // same TimerManager (on the World)
game.events();                          // same EventDispatcher (on the World)
game.audio();                           // same AudioManager (on the World)
game.saves();                           // same SaveGameSystem (slot directory)
game.gameMode();                        // HallGameMode for Hall.json
```

Play start uses this path instead of `new World(...)` + a loose console.

## Load / unload a level

Unreal names on `World` and `GameplayStatics`:

```java
import com.elitesavior.vasthall.engine.GameInstance;
import com.elitesavior.vasthall.engine.GameplayStatics;
import com.elitesavior.vasthall.engine.LevelDefinition;
import com.elitesavior.vasthall.engine.World;

GameInstance game = GameInstance.withDemoAssets();
game.init();
World world = game.world();                              // Hall + mesh/texture/audio stubs
world.loadLevel("Hall");                                 // or "levels/Hall.json"
world.unloadLevel("Hall");                               // UnloadStreamLevel — destroys Hall actors only
game.openLevel("Hall");                                  // OpenLevel + install GameMode

// Still valid: register a LevelDefinition yourself (indexes the registry)
world.registerLevel(LevelDefinition.hall());

GameplayStatics.loadLevel(world, "Hall");
GameplayStatics.unloadLevel(world, "Hall");
GameplayStatics.openLevel(game, "Hall");                 // prefers GameInstance so GameMode is installed
GameplayStatics.openLevel(world, "Hall");                // same, when world.gameInstance() is set
```

`openLevel` is **same-world travel**: it does not create a new `World`. Actors you spawned yourself (no `levelName`) stay; every loaded streaming level is unloaded first.

Loading a name that is already loaded returns the existing `Level` and does not duplicate actors. Unloading a name that is not loaded returns `false`.

Each actor spawned from a level has `actor.levelName()` set to that map. Unload walks that ownership list, `destroy`s those actors (`endPlay`, detached from the registry), and drops the `Level`. Persistent actors and other loaded levels are left alone.

## Sample: `Hall`

`app/src/main/resources/levels/Hall.json` (also `LevelDefinition.hall()`):

```json
{
  "name": "Hall",
  "gameMode": "HallGameMode",
  "actors": [
    {
      "class": "PlayerPawn",
      "name": "PlayerPawn",
      "location": [0, 0, 0],
      "tickEnabled": false
    },
    {
      "class": "HallBeaconActor",
      "name": "HallBeacon",
      "location": [0, 1.5, 4],
      "tickEnabled": true
    }
  ]
}
```

`class` is a short name from `ActorTypes` (`PlayerPawn`, `HallBeaconActor`, …) or a fully qualified `Actor` subclass. Optional fields: `name`, `location` `[x,y,z]`, `rotation` `[pitch,yaw,roll]` degrees, `scale`, `tickEnabled`. Optional `gameMode` is a short name from `GameModeTypes` (`HallGameMode`, `GameMode`, …) or a fully qualified `GameMode` subclass.

Java equivalent:

```java
world.registerLevel(LevelDefinition.named("Hall")
        .gameMode("HallGameMode")
        .actor(ActorTemplate.of("PlayerPawn").named("PlayerPawn").at(0, 0, 0).tickEnabled(false))
        .actor(ActorTemplate.of("HallBeaconActor").named("HallBeacon")
                .at(0, 1.5f, 4).tickEnabled(true)));
```

Play start calls `GameInstance.withDemoAssets()`, `init()`, then `openLevel("Hall")`.

## Write a GameMode

Subclass `GameMode` and register the short name (or use the fully qualified class in JSON):

```java
public class ArenaGameMode extends GameMode {
    @Override
    public Class<? extends Actor> defaultPawnClass() {
        return PlayerPawn.class;
    }

    @Override
    public void initGame(String options) {
        super.initGame(options);
    }

    @Override
    public void startPlay() {
        super.startPlay(); // spawns defaultPawnClass if the world has none
    }
}

GameModeTypes.register("ArenaGameMode", ArenaGameMode.class);
world.registerLevel(LevelDefinition.named("Arena").gameMode("ArenaGameMode"));
game.openLevel("Arena");
```

`GameMode.endPlay` runs when GameInstance travels or unloads the last streaming level. A pawn spawned by `startPlay` is bound to the current loaded map so the next `openLevel` destroys it with that map. `openLevel` rejects an unknown name before tearing down the current mode. `HallGameMode.startPlay` also sets a one-shot `TimerManager` hook after `HallGameMode.DELAYED_START_SECONDS` (0.25s) and binds `ActorSpawned` / `LevelUnloaded` (cleared in `endPlay`). HUD / PlayerController / GameState are out of scope this version.

## TimerManager

Unreal mental model: `GetWorld()->GetTimerManager().SetTimer(...)`. Java timers are **tick-driven**: `World.tick(dt)` subtracts `dt` from remaining time and fires due callbacks on the same thread. There is no extra async executor.

```java
import com.elitesavior.vasthall.engine.Actor;
import com.elitesavior.vasthall.engine.GameplayStatics;
import com.elitesavior.vasthall.engine.TimerHandle;
import com.elitesavior.vasthall.engine.TimerManager;
import com.elitesavior.vasthall.engine.World;

World world = game.world();
TimerManager timers = world.timerManager();          // same as game.timerManager()
TimerHandle once = timers.setTimer(() -> { /* log */ }, 1.0f, false);
TimerHandle loop = timers.setTimer(this::pulse, 0.5f, true);
timers.pauseTimer(loop);
timers.unPauseTimer(loop);
timers.clearTimer(once);

// Actor / component helpers auto-clear on endPlay / onDetach
Actor actor = world.findActor("HallBeacon");
TimerHandle owned = actor.setTimer(() -> actor.setName("tock"), 2.0f, false);
actor.clearTimer(owned);

GameplayStatics.setTimer(world, () -> {}, 0.25f, false);
GameplayStatics.getTimerManager(world);
```

| Call | What it does |
| --- | --- |
| `setTimer(callback, rate, looping)` | Fire after `rate` seconds; looping restarts at the same rate |
| `clearTimer(handle)` | Cancel. Null / already-invalid handles are ignored |
| `pauseTimer` / `unPauseTimer` | Freeze or resume remaining time |
| `isTimerActive` / `isTimerPaused` | Active means present and not paused |
| `getTimerRemaining` / `getTimerRate` | Seconds, or `-1` if the handle is not live |
| `clearAll` | Invalidate every handle (also `World.destroyAll`) |

Rate must be `> 0`. A large `dt` can fire a looping timer more than once in one tick (capped). Actor- and component-owned timers are cleared when the actor is destroyed or the component is detached. World-level timers survive `openLevel` (same World); `HallGameMode.endPlay` clears its delayed-start handle on travel.

Console demo (fossDebug `~`):

```
settimer 1 once hello
timers
cleartimer
```

After about one second of play (menu closed so the world ticks), the console log gets `timer fired id=… hello`. `HallGameMode` also schedules its 0.25s delayed-start hook on `open Hall`.

## Gameplay events / delegates

Unreal mental model: a multicast Event Dispatcher (`Add` / `Remove` / `Broadcast`) or a typed Gameplay Message. Java uses `EventType<T>` keys so a bind is type-safe (Kotlin: `events.bind(EventType.LEVEL_LOADED) { … }`). There is no native/JNI bridge for this layer.

```java
import com.elitesavior.vasthall.engine.ActorEvent;
import com.elitesavior.vasthall.engine.DelegateHandle;
import com.elitesavior.vasthall.engine.EventDispatcher;
import com.elitesavior.vasthall.engine.EventType;
import com.elitesavior.vasthall.engine.GameplayStatics;
import com.elitesavior.vasthall.engine.LevelEvent;
import com.elitesavior.vasthall.engine.World;

World world = game.world();
EventDispatcher events = world.events();             // same as game.events()

DelegateHandle loaded = events.bind(EventType.LEVEL_LOADED, (LevelEvent e) -> {
    // e.world(), e.levelName()
});
DelegateHandle spawned = events.bind(EventType.ACTOR_SPAWNED, (ActorEvent e) -> {
    // e.actor() is live (after beginPlay)
});
events.unbind(loaded);                               // no further delivery; handle invalid

// Custom gameplay event: same name must keep the same payload class
EventType<String> ping = EventType.of("Ping", String.class);
events.bind(ping, message -> { /* … */ });
events.broadcast(EventType.of("Ping", String.class), "hi");

GameplayStatics.bindEvent(world, EventType.LEVEL_UNLOADED, e -> { });
GameplayStatics.getEventDispatcher(world);
```

| Engine hook | When it broadcasts |
| --- | --- |
| `EventType.LEVEL_LOADED` | After a **new** `loadLevel` finishes spawning that map's actors |
| `EventType.LEVEL_UNLOADED` | After `unloadLevel` destroys those actors and drops the `Level` |
| `EventType.ACTOR_SPAWNED` | After `beginPlay` (deferred to end-of-tick if you spawn during `tick`) |
| `EventType.ACTOR_DESTROYED` | After `endPlay`, before the actor detaches |

Already-loaded `loadLevel` and not-loaded `unloadLevel` do not broadcast. Bind order is preserved. A listener bound during broadcast runs on the **next** broadcast. Unbind during broadcast is safe. Null / already-invalid / foreign handles are ignored (the handle stays valid if it was not on that delegate). If you `loadLevel` / `unloadLevel` during `tick`, the level event waits until that frame's pending spawn/destroy flush so `ActorSpawned` / `ActorDestroyed` still precede it. Level templates apply `name` / `tickEnabled` before `ActorSpawned`.

`HallGameMode` binds `ActorSpawned` / `LevelUnloaded` in `startPlay` (so it hears later spawns and streaming unloads, not the opening Hall actors — those fire before the mode exists) and unbinds in `endPlay`. The console binds all four engine hooks at builtin registration and logs `event LevelLoaded Hall`, `event ActorSpawned PlayerPawn`, …

Console demo (fossDebug `~`):

```
events
load Hall
```

`events` lists listener counts. Play start (`open Hall`) already logs the Hall load / pawn / beacon spawns.

## SaveGame

Unreal mental model: `UGameplayStatics::CreateSaveGameObject` / `SaveGameToSlot` / `LoadGameFromSlot` / `DoesSaveGameExist` / `DeleteGameInSlot`. Java persists a small JSON payload (`<slot>.sav`) — current level name, GameMode class, and live actor fields (name, class, level, transform, tick, `TagComponent` tags). No cloud, no networking, no native renderer dump.

```java
import com.elitesavior.vasthall.engine.GameInstance;
import com.elitesavior.vasthall.engine.GameplayStatics;
import com.elitesavior.vasthall.engine.SaveGame;

GameInstance game = GameInstance.withDemoAssets();
game.setSaveDirectory(new java.io.File(tempDir, "saves")); // tests; APK uses filesDir/SaveGames
game.init();
game.openLevel("Hall");

SaveGame snapshot = game.createSaveGame();          // or GameplayStatics.createSaveGame(game)
snapshot.levelName();                               // "Hall"
snapshot.findActor("HallBeacon").location();

game.saveGameToSlot("Slot0");                       // creates Slot0.sav
game.doesSaveGameExist("Slot0");
SaveGame loaded = game.loadSaveGameObject("Slot0"); // read only
game.loadGameFromSlot("Slot0");                     // openLevel + restore matching actors
game.deleteGameInSlot("Slot0");

GameplayStatics.saveGameToSlot(game.world(), "Slot0");
GameplayStatics.loadGameFromSlot(game, "Slot0");
```

| Call | What it does |
| --- | --- |
| `createSaveGame()` | Snapshot the live World (does not write a file) |
| `saveGameToSlot(slot)` | Capture + write `{saveDir}/{slot}.sav` (overwrite) |
| `loadSaveGameObject(slot)` | Parse the slot; null if missing / invalid name |
| `loadGameFromSlot(slot)` | Read, `openLevel` the saved map, restore actors by name |
| `doesSaveGameExist` / `deleteGameInSlot` | Slot file probe / delete |
| `saveSlots()` | Sorted list of valid slot names in the directory |

Slot names are `[A-Za-z0-9][A-Za-z0-9_-]{0,63}` — no `/`, `\`, `.`, or `..`. Tests inject a temp directory (`setSaveDirectory`); play start uses `getFilesDir()/SaveGames`. Format `version` is `SaveGame.FORMAT_VERSION` (1). Unknown actors after travel are skipped.

Console demo (fossDebug `~`):

```
SaveGame Slot0
LoadGame Slot0
```

Names are case-insensitive (`savegame` / `loadgame`). Missing slots return `error: no save: …`.

## AudioManager

Unreal mental model: `UAudioDevice` + `UGameplayStatics::PlaySound2D` / `PlaySound`. Java looks up `AssetKind.AUDIO` rows (`AudioHandle` payloads) on the world's `AssetRegistry`, then play/stop **by id**. One voice per asset id (a second `play` restarts it). There is no MediaPlayer / SoundPool / FMOD in the foss default — `SilentAudioDevice` accepts the calls so unit tests need no hardware. `play2D` is the non-spatial stub (`PlaySound2D`); attenuation and 3D listeners are out of scope.

```java
import com.elitesavior.vasthall.engine.AssetRegistry;
import com.elitesavior.vasthall.engine.AudioManager;
import com.elitesavior.vasthall.engine.GameplayStatics;
import com.elitesavior.vasthall.engine.World;

World world = game.world();
AudioManager audio = world.audio();                 // same as game.audio()

audio.play(AssetRegistry.HALL_AMBIENCE_ID);         // or "/Game/Audio/HallAmbience"
audio.play2D(AssetRegistry.HALL_AMBIENCE_ID, 0.5f); // 2D stub; restarts the same voice
audio.isPlaying(AssetRegistry.HALL_AMBIENCE_ID);
audio.setMasterVolume(0.25f);                       // clamped to [0, 1]
audio.stop(AssetRegistry.HALL_AMBIENCE_ID);

GameplayStatics.playSound2D(world, "HallAmbience");
GameplayStatics.setMasterVolume(world, 0.5f);
GameplayStatics.stopSound(world, "HallAmbience");
GameplayStatics.getAudioManager(world);
```

| Call | What it does |
| --- | --- |
| `play(id)` | Start (or restart) the registered AUDIO asset. False if missing / not audio |
| `play2D(id[, volume])` | Same catalog, marked non-spatial. Volume scale clamped to `[0, 1]` |
| `stop(id)` | Stop that voice. False if unknown or not playing |
| `stopAll` | Stop every voice (also `World.destroyAll`) |
| `setMasterVolume` / `masterVolume` | Mixer gain. NaN / Inf throw; values clamp to `[0, 1]` |
| `isPlaying` / `playingCount` / `playingIds` | Voice query (canonical asset ids) |

Unknown ids and non-`AUDIO` assets (`HallMesh`, …) return `false` — they do not throw. Tests inject an `AudioDevice` (`setDevice`) to record play/stop/volume without hardware. Play start does **not** auto-play Hall ambience.

Console demo (fossDebug `~`):

```
PlaySound HallAmbience
SetMasterVolume 0.5
audio
StopSound HallAmbience
```

Names are case-insensitive (`playsound` / `stopsound` / `setmastervolume`). Missing ids return `error: unknown sound: …`.

## Register and load an asset

Unreal Content Browser mental model, without an editor: every piece of content has a **short id** and a **path**. Register once on the world's `AssetRegistry`. Look up later by either key. Missing names return null (`find`) or throw `unknown asset` (`require` / `GameplayStatics.loadAsset`).

Paths look like classpath files or `/Game/...` object paths:

| Kind | Sample id | Sample path | Payload |
| --- | --- | --- | --- |
| `LEVEL` | `Hall` | `levels/Hall.json` | `LevelDefinition` (same JSON as the Hall sample) |
| `MESH` | `HallMesh` | `/Game/Meshes/Hall` | `MeshHandle` stub for the native hall mesh |
| `TEXTURE` | `HallBeaconTexture` | `/Game/Textures/HallBeacon` | `TextureHandle` stub |
| `AUDIO` | `HallAmbience` | `/Game/Audio/HallAmbience` | `AudioHandle` stub |

```java
import com.elitesavior.vasthall.engine.Asset;
import com.elitesavior.vasthall.engine.AssetKind;
import com.elitesavior.vasthall.engine.AssetRegistry;
import com.elitesavior.vasthall.engine.GameplayStatics;
import com.elitesavior.vasthall.engine.LevelDefinition;
import com.elitesavior.vasthall.engine.TextureHandle;
import com.elitesavior.vasthall.engine.World;

World world = new World();
AssetRegistry assets = world.assets();

// 1. Register (or call assets.registerDemoAssets() for the built-in Hall set)
assets.register("Hall", "levels/Hall.json", AssetKind.LEVEL, LevelDefinition.hall());
assets.register(
        AssetRegistry.HALL_BEACON_TEXTURE_ID,
        AssetRegistry.HALL_BEACON_TEXTURE_PATH,
        AssetKind.TEXTURE,
        new TextureHandle(AssetRegistry.HALL_BEACON_TEXTURE_ID));

// 2. Look up by id or path
Asset hall = assets.find("Hall");
Asset same = assets.require("levels/Hall.json");
LevelDefinition def = assets.findLevel("Hall");

// 3. World / actor resolve through the same catalog
world.loadLevel("levels/Hall.json");                 // uses findLevel, not a one-off read
TextureHandle tex = world.findActor("HallBeacon")
        .loadAsset("/Game/Textures/HallBeacon", TextureHandle.class);

GameplayStatics.findAsset(world, "Hall");            // null if missing
GameplayStatics.loadAsset(world, "Hall");            // throws unknown asset if missing
```

`World.registerLevel` is a convenience that indexes a `LEVEL` asset as `{name}` / `levels/{name}.json`. `World.loadLevel` and `openLevel` resolve that asset instead of keeping a second hardcoded catalog.

Do world lookups in actor `beginPlay`, not in a constructor — the registry is on the `World`, and `owner().world()` is still null until `spawnActor` finishes. Hall's beacon does this for `/Game/Textures/HallBeacon`.

## Developer console

Unreal mental model: press `` ` `` / `~` and type a command. Java `DeveloperConsole` registers named handlers and `exec`s a line. Play start owns the console on `GameInstance`; builtins bind to that live `World`.

```java
import com.elitesavior.vasthall.engine.DeveloperConsole;
import com.elitesavior.vasthall.engine.World;

DeveloperConsole console = DeveloperConsole.withBuiltins(world);
console.exec("help");
console.exec("actors");
console.exec("assets");
console.exec("load Hall");
console.exec("unload Hall");
console.exec("open Hall");
console.exec("settimer 1 once hello");
console.exec("timers");
console.exec("events");
console.exec("SaveGame Slot0");
console.exec("LoadGame Slot0");
console.exec("PlaySound HallAmbience");
console.exec("SetMasterVolume 0.5");
console.exec("audio");
console.exec("StopSound HallAmbience");
console.register("ping", "Echo ping", (bound, args) -> "pong");
```

| Command | What it does |
| --- | --- |
| `help` / `help <name>` | List commands, or one help line |
| `actors` (`listactors`) | List live actors (name, class, level, loc) |
| `assets` (`listassets`) | List the Asset Registry |
| `load <name>` (`loadlevel`) | `GameplayStatics.loadLevel` (id or path) |
| `unload <name>` (`unloadlevel`) | `GameplayStatics.unloadLevel` |
| `open <name>` (`openlevel`) | `GameplayStatics.openLevel` (same-world travel) |
| `stat` | `actors=… levels=… assets=… frame=… mode=… timers=… events=… saves=… audio=…` |
| `settimer <seconds> [once\|loop] [message]` | `SetTimer` — delayed console log |
| `cleartimer [id]` | `ClearTimer` (last handle if id omitted) |
| `timers` | List active TimerManager entries |
| `events` | List EventDispatcher listener counts |
| `savegame <slot>` (`SaveGame`) | Capture the World and write a slot |
| `loadgame <slot>` (`LoadGame`) | Load a slot and restore the World |
| `playsound <id>` (`PlaySound`) | Play a registered AUDIO asset |
| `stopsound <id>` (`StopSound`) | Stop that voice |
| `setmastervolume <0-1>` (`SetMasterVolume`) | Clamp and set mixer gain |
| `audio` | List playing AudioManager voices |

Names are case-insensitive. Unknown names return `unknown command`. Level commands that throw (`unknown level`, missing name) return `error: …`.

**fossDebug only:** a `~` button sits on the play HUD (left of `DBG`). Menu → Debug → Console opens the same overlay. Hardware `` ` `` (`KEYCODE_GRAVE`) toggles it. Opening the console pauses play like Menu so sticks are not involved. **fossRelease** skips the button and overlay (`DeveloperConsoleGate.UI_ENABLED`, debug/release source set); the engine class still compiles.

## Spawn an actor from code

```java
import com.elitesavior.vasthall.engine.Actor;
import com.elitesavior.vasthall.engine.HallBeaconActor;
import com.elitesavior.vasthall.engine.Transform;
import com.elitesavior.vasthall.engine.World;

World world = new World();

// Class + transform (Unreal SpawnActor)
HallBeaconActor beacon = world.spawnActor(
        HallBeaconActor.class,
        Transform.at(0.0f, 1.5f, 4.0f));
beacon.setName("HallBeacon");
beacon.setActorTickEnabled(true);

// Or pass an already constructed instance
Actor named = world.spawnActor(new HallBeaconActor(), Transform.identity());
named.setName("SideBeacon");
```

Each spawn:

1. Assigns a unique `id`
2. Copies the spawn `Transform` onto the actor
3. Calls `beginPlay()` (deferred until after the current tick if you spawn during `tick`)

Code-spawned actors have `levelName() == null` until a level load binds them. They survive `unloadLevel` / `openLevel`.

## Add a component in code

Actors own components (Unreal `CreateDefaultSubobject` / `AddComponent`). Lifecycle: `onAttach` when added, `tick` each frame the **owner actor** ticks, `onDetach` when removed or when the actor is destroyed.

```java
import com.elitesavior.vasthall.engine.Actor;
import com.elitesavior.vasthall.engine.MovementComponent;
import com.elitesavior.vasthall.engine.TagComponent;
import com.elitesavior.vasthall.engine.Transform;
import com.elitesavior.vasthall.engine.World;

World world = new World();
Actor crate = world.spawnActor(Actor.class, Transform.at(0.0f, 0.0f, 2.0f));

// Constructor (onAttach may see owner().world() == null), beginPlay, or after spawn
TagComponent tags = crate.addComponent(new TagComponent("pickup"));
tags.addTag("crate");

MovementComponent move = crate.addComponent(new MovementComponent());
move.setVelocity(0.0f, 0.0f, -1.0f);   // slide toward -Z each tick

crate.getComponent(TagComponent.class);
crate.removeComponent(move);           // onDetach; crate stays in the world
```

Write your own by subclassing `ActorComponent`:

```java
public class SpinComponent extends ActorComponent {
    @Override
    protected void tick(float deltaSeconds) {
        owner().transform().rotation.yaw += 90.0f * deltaSeconds;
    }
}

actor.addComponent(new SpinComponent());
```

Rules that match this engine:

- One component instance belongs to at most one actor (`already attached` if you reuse it).
- `getComponent(Class)` returns the **first** match; `components()` / `componentsOf` list them.
- Constructor / `beginPlay` / after spawn are all valid times to `addComponent`. `onAttach` means “this actor owns you now.” If you add in a constructor (Hall’s `TagComponent`s do), `owner().world()` is still **null** until `World.spawnActor` finishes — do world lookups in actor `beginPlay`, not in `onAttach`.
- `addComponent` throws after the actor is pending kill (`destroy` / `unloadLevel`).
- Component `tick` runs only when the owner actor ticks (`setActorTickEnabled(true)`). `PlayerPawn` keeps actor tick off so Java movement cannot fight `libvasthall.so`.
- `setComponentTickEnabled(false)` skips that component even if the actor ticks. `TagComponent` defaults to tick off.
- Add/remove during `tick` is safe: the new component ticks next frame; a removed one does not finish this frame.
- `world.destroyActor(actor)` (and `unloadLevel`) calls actor `endPlay`, then `onDetach` on every remaining component.

Hall sample: `PlayerPawn` ships a `TagComponent("pawn")`, `HallBeaconActor` a `TagComponent("beacon")`. Beacon bob/yaw stays in the actor `tick`, not a movement component.

## Tick

`VastHallActivity` ticks the world from the vsync `Choreographer` callback while the menu is closed:

```java
world.tick(deltaSeconds);  // TimerManager first, then actors
```

Override on your subclass:

```java
public class SpinningProp extends Actor {
    @Override
    protected void tick(float deltaSeconds) {
        transform().rotation.yaw += 90.0f * deltaSeconds;
    }
}
```

`setActorTickEnabled(false)` keeps the actor in the world but skips `tick` **and** its components. `PlayerPawn` uses that because `libvasthall.so` still owns walk/look.

Pause / menu: the activity skips `world.tick` while overlays are open. Actors and timers stay in the world; they just stop advancing.

## Destroy and query

```java
world.destroyActor(beacon);          // or beacon.destroy()
world.destroyAll();                  // on activity destroy

int n = world.actorCount();
Actor found = world.findActor("HallBeacon");
List<HallBeaconActor> beacons = world.actorsOf(HallBeaconActor.class);
List<Actor> snapshot = world.actors(); // copy; mutating it does not change the world
Level hall = world.findLoadedLevel("Hall");
int comps = beacon.componentCount();
```

Destroy during `tick` is deferred until that frame finishes, so a ticking actor can spawn, destroy, or `loadLevel` without concurrent-modification errors.

## What you see in the APK

On play start the activity creates a `GameInstance`, `init()`s it, and `openLevel("Hall")`, which installs `HallGameMode` and spawns:

- `PlayerPawn` at the origin (logical stand-in for the native avatar; `TagComponent` `pawn`)
- `HallBeacon` at `(0, 1.5, 4)` with tick on (`TagComponent` `beacon`; texture from `/Game/Textures/HallBeacon`)

A top-center HUD line shows `SCENE Hall mode=HallGameMode actors=2 comps=2 assets=4 timers=1 events=6 saves=0 audio=0  HallBeacon y=… yaw=…`. `timers=1` is the HallGameMode delayed-start hook; after 0.25s of play it becomes `timers=0`. `events=6` is the console's four engine-hook binds plus HallGameMode's two. `saves=0` is the number of `.sav` slots in `filesDir/SaveGames`. `audio=0` is the number of playing AudioManager voices (Hall ambience is registered, not auto-played). Y and yaw change every frame while you are in the hall (not in Menu). fossDebug also shows a `~` button; open it (or Menu → Debug → Console) and run `actors` / `assets` / `settimer 1 once hello` / `timers` / `events` / `SaveGame Slot0` / `LoadGame Slot0` / `PlaySound HallAmbience` / `audio`. **Menu → Debug → Copy dump** includes the same list under `[ENGINE]`:

```
game.instance=1
game.saves=0
game.mode=HallGameMode pawn=PlayerPawn started=1
world.actors=2
world.frame=…
world.levels=1
world.timers=1
world.events=6
world.audio=0
world.assets=4
asset id=Hall path=levels/Hall.json kind=LEVEL
asset id=HallMesh path=/Game/Meshes/Hall kind=MESH
asset id=HallBeaconTexture path=/Game/Textures/HallBeacon kind=TEXTURE
asset id=HallAmbience path=/Game/Audio/HallAmbience kind=AUDIO
level=Hall actors=2
actor id=1 name=PlayerPawn class=PlayerPawn level=Hall tick=0 loc=0.0000,0.0000,0.0000 … components=1
  component class=TagComponent tick=0 tags=pawn
actor id=2 name=HallBeacon class=HallBeaconActor level=Hall tick=1 loc=0.0000,1.5xxx,4.0000 … components=1
  component class=TagComponent tick=0 tags=beacon
```

## Out of scope (this version)

- Unreal Editor / Blueprint / a real Content Browser UI / Blueprint Event Dispatcher reflection
- A full in-editor output log / command history browser
- Packaging / cooking / a packaging-pipeline rewrite
- Networking / cloud saves
- Native mesh spawn through JNI
- Full serialization of native renderer state
- Input / stick lockup changes
- Seamless travel / a second `World` instance / multiplayer
- A full Unreal Editor GameMode UI / PlayerController / GameState
- Component replication / Blueprint components
- A `TransformComponent` (transform is already on `Actor`)
- Spatial / 3D audio, attenuation, FMOD, MediaPlayer / SoundPool hardware decode

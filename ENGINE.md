# Vast Hall engine (Scene / Actor / Level / Component)

Unreal mental model: **the World owns Actors; Actors own Components; named Levels stream into the World**. You do not `new` an actor and hope it ticks. You spawn it into a `World` (or load a level that does), which calls `beginPlay`, ticks it each frame, and calls `endPlay` on destroy. Destroying an actor detaches its components.

Native hall rendering and locomotion still live in `libvasthall.so`. This Java layer is the gameplay object model those natives can later attach to. **Transform stays on the Actor** (`actor.transform()`), not on a component — same as Unreal's root transform on `AActor`.

## Types

| Vast Hall | Unreal analog | Role |
| --- | --- | --- |
| `World` | `UWorld` | Spawn, destroy, tick, query, stream levels |
| `Actor` | `AActor` | Gameplay object with a transform |
| `ActorComponent` | `UActorComponent` | Behavior attached to an actor |
| `MovementComponent` | `UMovementComponent` stub | Adds `velocity * dt` to owner location |
| `TagComponent` | actor tags | Named tags for query/dump (tick off) |
| `Transform` | `FTransform` | Location, rotator (pitch/yaw/roll degrees), scale |
| `LevelDefinition` | map / streaming-level asset | Named list of actor templates |
| `Level` | loaded `ULevel` | Actors currently owned by one loaded map |
| `GameplayStatics` | `UGameplayStatics` | `loadLevel` / `unloadLevel` / `openLevel` |
| `PlayerPawn` | default pawn | Java handle for the native player avatar (tick off) |
| `HallBeaconActor` | demo actor | Spawned by the Hall sample; bobs and yaws so tick is visible |

Package: `com.elitesavior.vasthall.engine`.

## Load / unload a level

Unreal names on `World` and `GameplayStatics`:

```java
import com.elitesavior.vasthall.engine.GameplayStatics;
import com.elitesavior.vasthall.engine.LevelDefinition;
import com.elitesavior.vasthall.engine.World;

World world = new World();
world.registerLevel(LevelDefinition.hall());          // classpath levels/Hall.json
world.loadLevel("Hall");                              // LoadStreamLevel
world.unloadLevel("Hall");                            // UnloadStreamLevel — destroys Hall actors only
world.openLevel("Hall");                              // OpenLevel: unload streaming levels, then load Hall

GameplayStatics.loadLevel(world, "Hall");
GameplayStatics.unloadLevel(world, "Hall");
GameplayStatics.openLevel(world, "Hall");
```

`openLevel` is **same-world travel**: it does not create a new `World`. Actors you spawned yourself (no `levelName`) stay; every loaded streaming level is unloaded first.

Loading a name that is already loaded returns the existing `Level` and does not duplicate actors. Unloading a name that is not loaded returns `false`.

Each actor spawned from a level has `actor.levelName()` set to that map. Unload walks that ownership list, `destroy`s those actors (`endPlay`, detached from the registry), and drops the `Level`. Persistent actors and other loaded levels are left alone.

## Sample: `Hall`

`app/src/main/resources/levels/Hall.json` (also `LevelDefinition.hall()`):

```json
{
  "name": "Hall",
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

`class` is a short name from `ActorTypes` (`PlayerPawn`, `HallBeaconActor`, …) or a fully qualified `Actor` subclass. Optional fields: `name`, `location` `[x,y,z]`, `rotation` `[pitch,yaw,roll]` degrees, `scale`, `tickEnabled`.

Java equivalent:

```java
world.registerLevel(LevelDefinition.named("Hall")
        .actor(ActorTemplate.of("PlayerPawn").named("PlayerPawn").at(0, 0, 0).tickEnabled(false))
        .actor(ActorTemplate.of("HallBeaconActor").named("HallBeacon")
                .at(0, 1.5f, 4).tickEnabled(true)));
```

Play start calls `openLevel("Hall")`.

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

// Constructor / beginPlay / after spawn — all fine
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
- Component `tick` runs only when the owner actor ticks (`setActorTickEnabled(true)`). `PlayerPawn` keeps actor tick off so Java movement cannot fight `libvasthall.so`.
- `setComponentTickEnabled(false)` skips that component even if the actor ticks. `TagComponent` defaults to tick off.
- Add/remove during `tick` is safe: the new component ticks next frame; a removed one does not finish this frame.
- `world.destroyActor(actor)` (and `unloadLevel`) calls actor `endPlay`, then `onDetach` on every remaining component.

Hall sample: `PlayerPawn` ships a `TagComponent("pawn")`, `HallBeaconActor` a `TagComponent("beacon")`. Beacon bob/yaw stays in the actor `tick`, not a movement component.

## Tick

`VastHallActivity` ticks the world from the vsync `Choreographer` callback while the menu is closed:

```java
world.tick(deltaSeconds);
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

Pause / menu: the activity skips `world.tick` while overlays are open. Actors stay in the world; they just stop advancing.

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

On play start the activity `openLevel("Hall")`, which spawns:

- `PlayerPawn` at the origin (logical stand-in for the native avatar; `TagComponent` `pawn`)
- `HallBeacon` at `(0, 1.5, 4)` with tick on (`TagComponent` `beacon`)

A top-center HUD line shows `SCENE Hall actors=2 comps=2  HallBeacon y=… yaw=…`. Y and yaw change every frame while you are in the hall (not in Menu). **Menu → Debug → Copy dump** includes the same list under `[ENGINE]`:

```
world.actors=2
world.frame=…
world.levels=1
level=Hall actors=2
actor id=1 name=PlayerPawn class=PlayerPawn level=Hall tick=0 loc=0.0000,0.0000,0.0000 … components=1
  component class=TagComponent tick=0 tags=pawn
actor id=2 name=HallBeacon class=HallBeaconActor level=Hall tick=1 loc=0.0000,1.5xxx,4.0000 … components=1
  component class=TagComponent tick=0 tags=beacon
```

## Out of scope (this version)

- Unreal Editor / Blueprint
- Networking
- Native mesh spawn through JNI
- Input / stick lockup changes
- Seamless travel / a second `World` instance
- Component replication / Blueprint components
- A `TransformComponent` (transform is already on `Actor`)

# Vast Hall engine (Scene / Actor)

Unreal mental model: **the World owns Actors**. You do not `new` an actor and hope it ticks. You spawn it into a `World`, which calls `beginPlay`, ticks it each frame, and calls `endPlay` on destroy.

Native hall rendering and locomotion still live in `libvasthall.so`. This Java layer is the gameplay object model those natives can later attach to.

## Types

| Vast Hall | Unreal analog | Role |
| --- | --- | --- |
| `World` | `UWorld` | Spawn, destroy, tick, query |
| `Actor` | `AActor` | Gameplay object with a transform |
| `Transform` | `FTransform` | Location, rotator (pitch/yaw/roll degrees), scale |
| `PlayerPawn` | default pawn | Java handle for the native player avatar (tick off) |
| `HallBeaconActor` | demo actor | Spawned at play start; bobs and yaws so tick is visible |

Package: `com.elitesavior.vasthall.engine`.

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

`setActorTickEnabled(false)` keeps the actor in the world but skips `tick`. `PlayerPawn` uses that because `libvasthall.so` still owns walk/look.

Pause / menu: the activity skips `world.tick` while overlays are open. Actors stay in the world; they just stop advancing.

## Destroy and query

```java
world.destroyActor(beacon);          // or beacon.destroy()
world.destroyAll();                  // on activity destroy

int n = world.actorCount();
Actor found = world.findActor("HallBeacon");
List<HallBeaconActor> beacons = world.actorsOf(HallBeaconActor.class);
List<Actor> snapshot = world.actors(); // copy; mutating it does not change the world
```

Destroy during `tick` is deferred until that frame finishes, so a ticking actor can spawn or destroy without concurrent-modification errors.

## What you see in the APK

On play start the activity spawns:

- `PlayerPawn` at the origin (logical stand-in for the native avatar)
- `HallBeacon` at `(0, 1.5, 4)` with tick on

A top-center HUD line shows `SCENE actors=2  HallBeacon y=… yaw=…`. Y and yaw change every frame while you are in the hall (not in Menu). **Menu → Debug → Copy dump** includes the same list under `[ENGINE]`:

```
world.actors=2
world.frame=…
actor id=1 name=PlayerPawn class=PlayerPawn tick=0 loc=0.0000,0.0000,0.0000 …
actor id=2 name=HallBeacon class=HallBeaconActor tick=1 loc=0.0000,1.5xxx,4.0000 …
```

## Out of scope (this version)

- Unreal Editor / Blueprint
- Networking
- Native mesh spawn through JNI
- Input / stick lockup changes

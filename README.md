# CobbleEntity Cleaner

Server-side Fabric utility for Minecraft 1.21.1 + Cobblemon 1.7.3.

CobbleEntity Cleaner periodically removes ordinary wild `PokemonEntity` instances to reduce unnecessary entity load. It deliberately ignores Cobblemon NPC/trainers because it only evaluates Pokémon entities.

## Protected by default

- Shiny Pokémon.
- Legendary Pokémon.
- Mythical Pokémon.
- Pokémon owned by players.
- Trainer/NPC-owned Pokémon.
- Pokémon currently in battle.
- Busy Pokémon during transient interactions.
- Tethered/Pasture Pokémon.
- Pokémon inside the safety radius of an online player.
- Any entity with the scoreboard tag `cobbleentitycleaner_protected`.

The old five-minute age requirement was removed in alpha.3. A normal wild Pokémon can now be cleaned as soon as a cleanup happens, provided it is not covered by any of the protections above. The `minimumEntityAgeMinutes` config key is retained only for migration and is automatically forced to `0`.

## Clickable vote skip

Before an automatic cleanup, players who were online when the vote started receive two clickable options:

```text
[ ✓ SÍ, SALTAR ]   [ ✕ NO, CONTINUAR ]
```

The eligible voter list is frozen when the vote opens. Players may change their vote while the 60-second window is active. The live tally shows YES, NO, and participation counts.

By default at least 40% of eligible players must participate. If that minimum is reached, the majority of votes actually cast decides the result. YES must have strictly more votes than NO to skip the cleanup; a tie or insufficient participation lets the cleanup continue.

Commands:

```text
/cobblecleaner status
/cobblecleaner vote yes
/cobblecleaner vote no
/cobblecleaner vote skip
/cobblecleaner preview
/cobblecleaner run
/cobblecleaner reload
```

`/cobblecleaner vote skip` remains as a backwards-compatible alias for YES. `preview`, `run`, and `reload` require permission level 2.

## Default timing

- Cleanup every 20 minutes.
- No minimum entity age is required.
- Pokémon within 48 blocks of any player are protected.
- Vote opens 60 seconds before cleanup.
- Minimum vote participation: 40%.
- Warning at 10 seconds.

Configuration is generated at `config/cobbleentitycleaner.json`.

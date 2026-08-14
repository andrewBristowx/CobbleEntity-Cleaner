# CobbleEntity Cleaner

Server-side Fabric utility for Minecraft 1.21.1 + Cobblemon 1.7.3.

CobbleEntity Cleaner periodically removes only old, ordinary wild `PokemonEntity` instances to reduce unnecessary entity load. It deliberately ignores Cobblemon NPC/trainers because it only evaluates Pokémon entities.

## Protected by default

- Shiny Pokémon.
- Legendary Pokémon.
- Mythical Pokémon.
- Pokémon owned by players.
- Trainer/NPC-owned Pokémon.
- Pokémon currently in battle.
- Busy Pokémon during transient interactions.
- Tethered/Pasture Pokémon.
- Pokémon younger than the configured minimum age.
- Pokémon inside the safety radius of an online player.
- Any entity with the scoreboard tag `cobbleentitycleaner_protected`.

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
- Wild Pokémon must be loaded for at least 5 minutes before becoming eligible.
- Pokémon within 48 blocks of any player are protected regardless of age.
- Vote opens 60 seconds before cleanup.
- Minimum vote participation: 40%.
- Warning at 10 seconds.

Configuration is generated at `config/cobbleentitycleaner.json`.

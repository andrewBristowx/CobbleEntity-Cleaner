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

## Vote skip

Before an automatic cleanup, online players receive a clickable `[ VOTAR PARA SALTAR ]` button. Each player can vote once. By default, 50% of the players who were online when the vote started must vote yes to cancel that cleanup round.

Commands:

```text
/cobblecleaner status
/cobblecleaner vote skip
/cobblecleaner preview
/cobblecleaner run
/cobblecleaner reload
```

`preview`, `run`, and `reload` require permission level 2.

## Default timing

- Cleanup every 20 minutes.
- Wild Pokémon must be loaded for at least 5 minutes before becoming eligible.
- Pokémon within 48 blocks of any player are protected regardless of age.
- Vote opens 60 seconds before cleanup.
- Warning at 10 seconds.

Configuration is generated at `config/cobbleentitycleaner.json`.

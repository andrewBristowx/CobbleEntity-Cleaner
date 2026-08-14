# Changelog

## 0.1.0-alpha.2

- Replaces the one-button skip vote with clickable YES / NO choices.
- Adds `[ ✓ SÍ, SALTAR ]` and `[ ✕ NO, CONTINUAR ]` chat buttons.
- Adds `/cobblecleaner vote yes` and `/cobblecleaner vote no`.
- Keeps `/cobblecleaner vote skip` as a backwards-compatible YES alias.
- Allows eligible players to change their vote while the voting window is active.
- Shows live YES, NO, and participation totals after votes and in `/cobblecleaner status`.
- Changes the default vote rule to 40% minimum participation plus simple majority of votes cast.
- Requires YES to beat NO; ties and insufficient participation continue the cleanup.
- Keeps the eligible voter set frozen to players who were online when the vote opened.

## 0.1.0-alpha.1

- Adds scheduled cleanup of old ordinary wild Cobblemon Pokémon entities.
- Protects shiny, legendary and mythical Pokémon.
- Protects player-owned and trainer-owned Pokémon.
- Protects Pokémon in battle, busy Pokémon and tethered/Pasture Pokémon.
- Adds a configurable minimum entity age before cleanup eligibility.
- Adds a configurable player safety radius so nearby Pokémon are never removed during a cleanup.
- Adds a permanent `cobbleentitycleaner_protected` scoreboard-tag escape hatch.
- Adds automatic warnings before cleanup.
- Adds a player vote-skip flow with one vote per eligible player and a configurable percentage threshold.
- Adds clickable vote button in chat.
- Adds `/cobblecleaner status`, `/cobblecleaner vote skip`, `/cobblecleaner preview`, `/cobblecleaner run`, and `/cobblecleaner reload`.
- Adds cleanup summaries and detailed preview protection counts.

## Character window tab warnings

All character window tabs now show a warning indicator when something on that tab needs attention — without having to switch to it. Alerts appear on the Combat, Quest, Info, Notes, and other tabs as relevant to the current game state.

## Guild join requirement fix (all guilds)

Fixed a bug where the guild join requirement could never be completed. When a character had an accepted join requirement pending (`_ccjr_` set), `getCurrentGuildStore(false)` would internally call `getCurrentGuild()` with its default `validateJoinRequirement=true`, which returns null while a join is pending — causing the guild store lookup to silently fail and the join to never complete. Affects all three guilds (Magic, Thieves, Fighters).

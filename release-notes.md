## Networking stability fixes

Fixed three bugs that could cause client disconnections, particularly in long sessions:

- **Thread-safe request queue**: The client's request queue was an `ArrayList` accessed from two threads concurrently, which could produce a null entry and crash the networking thread. Replaced with a `ConcurrentLinkedDeque`.
- **Reconnect sync timeout**: The socket timeout for the login sync was 2 minutes — not enough for a large saved game's master-to-game diff to transfer. Raised to 10 minutes.
- **Response code delivery**: The server wrote a response code to the socket buffer before writing a (potentially very large) payload, but didn't flush. If the payload filled the TCP send buffer, the leading code never arrived and the client timed out. Added a flush after the response code.

## Character window tab warnings

All character window tabs now show a warning indicator when something on that tab needs attention — without having to switch to it. Alerts appear on the Combat, Quest, Info, Notes, and other tabs as relevant to the current game state.

## Guild join requirement fix (all guilds)

Fixed a bug where the guild join requirement could never be completed. When a character had an accepted join requirement pending (`_ccjr_` set), `getCurrentGuildStore(false)` would internally call `getCurrentGuild()` with its default `validateJoinRequirement=true`, which returns null while a join is pending — causing the guild store lookup to silently fail and the join to never complete. Affects all three guilds (Magic, Thieves, Fighters).

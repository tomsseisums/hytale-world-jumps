# TODO

## 1. Block teleport to disconnected worlds (with permission override)

If the destination world has no portal pointing back at the source world, prevent
the teleport. Allow operators (or anyone holding a dedicated permission) to bypass.

- Detect "no return portal" by scanning the destination world's
  `PortalMapMarkersResource` for any marker whose `targetWorld` points back at the
  source world.
- Add a new permission node, e.g. `worldjumps.warp.disconnected` — "can enter
  disconnected worlds" — separate from OP. Grant manually in `permissions.json`
  or via a built-in group like `hytale:Builder`.
- When blocked, send a clear chat message explaining why ("no return portal in
  destination world") instead of silently failing.

## 2a. Spawn players away from the receiving portal on world entry

Today, warping in always drops the player at the world spawn or, on return,
exactly where they last were — which can be on top of the receiving portal,
immediately re-triggering it.

Mirror the Teleport interaction's behavior: when arriving in a world, offset the
player a fixed distance away from the receiving portal and orient them facing
away from it. Each portal needs an "exit anchor" derived from its position +
rotation.

If a per-world saved position exists for the player (already restored), still
nudge them out of the portal's hitbox before re-enabling collision.

## 2b. Switch the warp model from Portal to Teleport

The current implementation uses a `CollisionEnter` interaction on a portal block.
Hytale's Teleport interaction natively handles entry-point offsetting, rotation,
and out-of-hitbox placement.

Refactor warp blocks to use Teleport (or a custom interaction modeled on it) so
we inherit those behaviors instead of reimplementing them on top of Portal.

Should land before — or together with — 2a; doing 2a on the Portal model is
throwaway work if 2b follows shortly.

## 3. Architecture review

A pass over the codebase looking at:

- **Java practices**: nullability annotations consistency, immutability where
  reasonable, prefer dependency injection over static singletons
  (`WorldJumpsPlugin.getPluginConfig()`, `getInventoryManager()`, etc.).
- **Atomicity**: `WorldTransitionListener` now juggles inventory restore +
  gamemode + permission grants. Consider splitting into focused services.
- **DRY**: `PORTAL_DEFS` in `WorldJumpsReindexCommand` duplicates information
  also baked into asset JSONs and partly into `PortalMapMarker`. One source of
  truth.
- **Comments**: trim WHAT-comments, keep WHY-comments.
- **Verbosity**: extract block-position-from-`BlockStateInfo` into a shared
  helper (used in `PortalMapMarker.OnAddRemove` and `WorldJumpsReindexCommand`).
- **Public API — events**: expose `WorldJumpEvent.Pre` / `Post` (and probably a
  cancellable `Pre`) on the server `EventRegistry` so other plugins can react to
  warps, override destinations, or block them. Define the event surface
  intentionally.

## 4. Fix the "target server version" warning

A startup warning surfaces about target server version mismatch. Track down the
source (likely `manifest.json` or the scaffoldit/Hytale dependency pin in
`build.gradle.kts` / `settings.gradle.kts`), align the versions, and resolve.

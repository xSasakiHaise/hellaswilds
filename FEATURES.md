# HellasWilds

HellasWilds is the Hellas suite's custom wild-zone and gate management mod for Pixelmon servers.
It introduces dyeable barrier blocks, remote-editable spawn rules, and Pixelmon-aware zone control
so staff can carve out curated hunting grounds without touching server configs.

## Feature Highlights
- **Gate-anchored zones:** Operators place dyeable pillars, barrier segments, and a gate badge to
  declare a managed wild zone. The badge links to the nearest pillars, detects the enclosed area,
  and tracks ownership metadata.
- **Pixelmon spawn governance:** `ZoneSpawnController` overrides native Pixelmon spawning inside
  zones, enforces per-zone caps, and injects custom species via reflection even when Pixelmon APIs
  change between versions.
- **Live spawn editor:** `/hellas wilds spawns edit` launches an embedded HTTP server (optionally
  tunneled via Playit) so staff can edit rule sets from a browser without restarting the server.
- **Visual tooling:** `/hellas wilds visualize` renders translucent overlays for the detected zone
  along with emissive badge numbers so builders can verify gates at a glance.
- **Security-aware barriers:** Invisible `NonPlayerBarrierFieldBlock` columns stop Pixelmon, items,
  and projectiles while letting players pass through unlocked gates. Locking a gate toggles the
  column state in one call.

## Technical Overview
- **Entry point:** `HellasWilds` wires up Forge/NeoForge events, registers registries, and guards all
  features behind Pixelmon + HellasForms dependency checks.
- **Blocks & tiles:** `blocks.barrier` defines the gate badge, pillars, and barrier segments plus the
  `GateBadgeTile` that stores linkage/zone metadata. `util.BlocksUtil` manages invisible columns.
- **Zone system:** `zone` contains `ZoneDetector` (dual flood-fill), `ZoneCache` (JSON-backed storage),
  and `VisualOverlayS2CPacket` for client overlays.
- **Spawn control:** `spawns.ZoneSpawnController` owns runtime state, while `PixelmonHook` observes
  entity events and enforces override/cap logic. `SpawnStorage` persists rule sets.
- **Commands & web UI:** `commands.WildsCommands` implements the `/hellas wilds` command tree.
  `webui.WebServer` hosts the single-page spawn editor, consulting `PlayitIntegration` for URLs.
- **Client presentation:** `client.render` houses tint handlers, fade tracking, gate badge renderer,
  and overlay rendering.

## Extending the Mod
- **New spawn rules:** Either edit the JSON created by `SpawnStorage` or, preferably, use the
  built-in `/hellas wilds spawns edit` workflow which persists through `ZoneSpawnController`.
- **New gate variants:** Use `ColorVariantBlockItem` as a template for dye-aware items and follow
  the pattern in `blocks.barrier.*` to ensure sections/columns are generated.
- **Custom zone tooling:** See `ZoneDetector` and `ZoneCache` for how geometry is computed and stored
  if you plan to add alternate detection methods or storage backends.

## Dependencies & Environment
- Minecraft 1.16.5
- Forge 36.2.42 (as configured in `build.gradle`)
- Pixelmon 9.1.13+ (verified at runtime via `ModList`)
- HellasForms (feature gate remains closed until it is present)
- Optional Playit tunnel info for exposing the spawn editor beyond localhost

## Migration Notes
- The spawn controller uses reflection-heavy calls into Pixelmon (`PokemonBuilder`, setters, etc.).
  Future Pixelmon updates will likely require touching `ZoneSpawnController.PixelmonFactory`.
- Zone detection and gating rely on block identities from `blocks.barrier`; renaming or porting these
  blocks to a new version requires updating `ZoneDetector#isBlocked` and gate linking logic.
- Networking and overlay rendering use Forge 1.16 client hooks (`RenderWorldLastEvent`). Modern
  versions may require porting `VisualOverlayRenderer` to new rendering APIs.

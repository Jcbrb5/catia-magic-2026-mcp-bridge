# Changelog

## Unreleased - CATIA Magic 2026x / Windows fork

Fork of upstream 2.3.5 targeting CATIA Magic 2026x on Windows. Bridge version is now `2.3.6` on both sides (exact match required).

### Security

- The plugin now requires a shared token in the `X-Cameo-Bridge-Token` header on every route except `/status` and `/capabilities` (and their `/api/v1` aliases). `BridgeAuth` resolves it from `CAMEO_BRIDGE_TOKEN`, then `-Dcameo.mcp.token.file`, then `<user.home>/.cameo-mcp-bridge/token`, generating a 32-byte random token on first start. The token value is never logged or returned over HTTP; the status payload gains an `auth` block with the header name, source and file path
- `RequestGuard` filter on every HTTP context rejects requests whose `Host` header is not `127.0.0.1`, `localhost` or `[::1]` (`403 BAD_HOST`, DNS-rebinding defense) and unauthenticated requests (`401 UNAUTHORIZED`)
- `-Dcameo.mcp.macros=false` disables `POST /api/v1/macros/execute` (`403 MACROS_DISABLED`)
- README no longer claims CORS is `*`; the plugin never sends `Access-Control-Allow-Origin`
- `BridgeAuth`, `RequestGuard` and their JUnit tests were compiled and run against a plain JDK 21 (with a stub for `HttpBridgeServer`) and smoke-tested over HTTP on the authoring machine; the edits to `HttpBridgeServer`, `CameoMCPBridgePlugin` and `MacroHandler` need the CATIA Magic jars and get their first compile on the Windows box via `gradlew.bat test assemblePlugin`

### Added

- `install.ps1 -PluginOnly` patch mode (build + deploy only), a wait-for-close prompt instead of a hard failure when CATIA Magic is running, a timestamped backup of the installed plugin under `%LOCALAPPDATA%\cameo-mcp-bridge\backups`, and `install.ps1 -Rollback` to restore the newest backup
- Python client sends the token from `CAMEO_BRIDGE_TOKEN` or `CAMEO_BRIDGE_TOKEN_FILE` (default `~/.cameo-mcp-bridge/token`), re-reads it on each request, and turns `UNAUTHORIZED`, `BAD_HOST` and `MACROS_DISABLED` responses into actionable messages
- Unit tests: `BridgeAuthTest`, `RequestGuardTest` (JUnit) and `BridgeTokenTests` (Python)
- `docs/windows-patch-prompt.md`: a prompt for Claude Code on the CATIA Magic machine to apply and verify a plugin patch

### Changed

- Ported the plugin to CATIA Magic 2026x APIs through `Cameo2026Compat`: `getPropertyManager()` via `PropertyBasedPresentationElement`, diagram layout via `DiagramLayouter`, and diagram-window open/active checks via the project's active diagram
- Build targets Java 21 and compiles against all jars in the Cameo `lib/` and `plugins/` folders
- Gradle accepts `-PjdkHome`, `JDK21_HOME` and `JAVA21_HOME` (`jdk17Home`/`JDK17_HOME` kept as aliases) and honors `CAMEO_HOME`
- Default Cameo install path is now `C:/Program Files/Magic Cyber Systems Engineer`; capability probes detect the running install root
- Pinned the `mcp` Python dependency to `<2`

### Added

- `install.ps1` Windows installer with CATIA Magic and JDK 21 auto-detection. It runs from a normal PowerShell window and asks for elevation (UAC) only when copying the plugin into a protected install folder, then checks that the jar landed
- `docs/windows-install.md` step-by-step Windows guide: prerequisites, install, verification, Claude Code / Claude Desktop / WSL setup, updating, port change, uninstall and troubleshooting
- `.gitattributes` line-ending rules for clean Windows checkouts

## 2.3.5 - 2026-05-05

Evidence-gathering release for native Relation Map debugging and UI-created settings inspection.

### Added

- UI state inspection endpoints and MCP tools for active diagram, selected elements, selected presentations, and browser selection context
- Raw diagram, presentation, and Relation Map settings/property dump endpoints for before/after investigation
- In-memory snapshot and JSON diff endpoints for capturing CATIA UI-created changes and comparing them against bridge-created state
- Relation Map presentation listing and raw `GraphSettings` getter dumps to distinguish graph data, settings state, presentation state, and image export behavior
- Relation Map criteria template discovery, criteria apply, compare, expand, collapse, render, and verify endpoints with machine-readable receipts
- Controlled built-in probe templates for Relation Map, presentation, and UI-selection API discovery without enabling arbitrary script execution
- Probe-first advanced route families for validation, Report Wizard, Requirements/ReqIF, Simulation, Teamwork, DataHub, variants, and safety/cyber extensions, with guarded preview/refusal MCP tools instead of hard optional-product dependencies
- Native validation route family with suite discovery, bounded validation runs, cached result readback, Python MCP wrappers, and tests
- Specific advanced handler surfaces for Report Wizard, import/export, simulation, Teamwork, DataHub, criteria templates, profiles/DSL previews, variant patterns, safety/cyber extension scans, and typed diagram inspection/write previews
- Autopilot route-surface live validation script for smoke-testing the new route families after deploying the rebuilt plugin
- Live validation scripts for UI introspection and Relation Map rendering regression evidence capture
- A Relation Map UI introspection runbook for agent-led debugging workflows
- Native Report Wizard generation through `GenerateTask`, including template discovery with classloader fallback and inline output-file proof receipts
- JSON/CSV requirements import apply for explicit `dryRun=false`, `allowWrite=true` requests targeting a package, while keeping native ReqIF apply gated
- Explicit `allowWrite=true` gating for Report Wizard file generation

### Changed

- Bumped the in-repo Python/plugin/methodology compatibility line to `2.3.5`
- Hardened the Gradle `deploy` task so installed plugin directories do not accumulate stale `cameo-mcp-bridge-*.jar` versions across local deploys
- Replaced generic criteria placeholder templates with the UI-verified Relation Map template subset where prior UI-diff evidence exists
- Hardened Relation Map execution semantics after live CATIA validation showed native refresh can block the EDT for large maps:
  - Relation Map render/export, criteria, expand, collapse, create, and configure paths no longer perform implicit native refresh by default
  - Render/export uses an EDT read path without an undo session when no refresh/expand/layout mutation is requested
  - Relation Map refresh remains available as an explicit operation with caller-provided timeout evidence
  - CATIA write sessions are serialized so overlapping bridge writes fail clearly instead of colliding inside `SessionManager`

### Fixed

- Exposed standard UML `Parameter.direction` through the specification read/write route so Activity Parameter Node direction validation repairs do not require macro-only edits.
- Fixed bridge-owned requirements export traversal so scoped JSON export walks owned elements recursively instead of relying on a broad type finder that missed newly imported disposable requirements.
- Tightened bridge-owned requirements export filtering so packages or other elements with "requirement" in their names are not exported unless their metaclass/stereotype evidence is Requirement-like.

## 2.3.4 - 2026-04-20

Patch release focused on diagram-presentation cleanup controls needed for live artifact export and review workflows.

### Added

- A native `cameo_prune_diagram_presentations` repair endpoint/tool so clients can remove unwanted auto-displayed symbols from a diagram using keep/drop rules instead of deleting presentations one-by-one
- A native `cameo_prune_path_decorations` repair endpoint/tool so clients can strip child path decorations such as association end-role labels while leaving the underlying relationship path intact

### Fixed

- Expanded normalized compartment controls so `cameo_set_shape_compartments` can drive SysML-specific compartments such as parts, content, references, full ports, flow properties, proxy ports, values, behaviors, receptions, and structure
- Corrected `show*`/`suppress*` compartment alias resolution so `cameo_set_shape_compartments` actually maps client keys like `showParts`, `showAttributes`, and `showFlowProperties` onto Cameo's underlying `Suppress ...` properties
- Corrected `Requirement Diagram` type resolution for physical requirements-diagram creation on the local Cameo build

### Changed

- Bumped the in-repo Python/plugin/methodology compatibility line to `2.3.4`
- Updated release metadata and tracked validation/docs references to avoid local source-corpus details

## 2.3.3 - 2026-04-20

Patch release that consolidates internal naming on neutral methodology terminology and cleans residual mentions from the tracked docs surface.

### Fixed

- Moved `ActivityPartition` add-to-diagram handling onto the native plugin route so raw REST and MCP clients create non-destructive swimlane presentations consistently

### Changed

- Consolidated the internal workflow module on the `cameo_mcp.methodology_workflows` name; the public MCP tool surface is unchanged
- Updated README section heading and tool descriptions to use methodology-workflow wording
- Rewrote tracked release notes, plan docs, and changelog entries to use methodology/package terminology throughout
- Bumped the in-repo Python/plugin/methodology compatibility line to `2.3.3`

## 2.3.2 - 2026-04-20

Patch release focused on live-verifiable native matrix coverage, neutral methodology naming, and Codex workspace productization.

### Added

- A repo-local Codex plugin scaffold with marketplace metadata, MCP launcher wiring, and a verification-first live-validation skill
- A neutral physical-architecture competency execution plan under `docs/plans/2026-04-20-physical-architecture-competency-plan.md`

### Fixed

- Repaired native `Refine Requirement Matrix` population by binding `Refine` relationships to the requirements-profile stereotype that Cameo's matrix criteria actually consume
- Updated the live matrix regression harness to validate the live-proven activity-to-requirement refine shape instead of the earlier speculative row domains
- Corrected `Dependency` ownership so generic dependency creation resolves to a package owner instead of trying to attach to arbitrary source elements
- Corrected stale README tool-count claims so the documented MCP surface matches the actual bridge capability manifest

### Changed

- Bumped the in-repo Python/plugin/methodology compatibility line to `2.3.2`

## 2.3.1 - 2026-04-13

Patch release focused on hardening the new diagram-repair surface introduced in `2.3.0`.

### Fixed

- Restored the missing `RepairDefaults.hiddenLabelKeys` field so the plugin compiles cleanly after the `2.3.0` repair-endpoint additions
- Wrapped `setPresentationElementProperties(...)` and `resetLabelPositions(...)` on the affected repair/property paths so Cameo builds with checked exceptions compile correctly
- Changed diagram-repair batches to fail soft per target instead of aborting the entire request on the first unsupported presentation element
- Excluded comment/note presentations from allocation-compartment normalization candidates
- Improved transition/item-flow presentation receipts to report partial support explicitly when a Cameo build exposes only a subset of the requested properties
- Corrected `repairLabelPositions` receipts and `updatedCount` reporting for processed targets

### Changed

- Bumped the in-repo Python/plugin/methodology compatibility line to `2.3.1`

## 2.3.0 - 2026-04-13

Minor release focused on reducing end-to-end human intervention for review, cleanup, and methodology package workflows.

### Added

- Intent-level diagram presentation APIs for transition labels, item-flow labels, and allocation/full-port compartments
- A built-in bridge probe on the Python side so clients can discover whether local health lives at `/status` or `/api/v1/status`
- Legacy `/status` and `/capabilities` HTTP aliases alongside the versioned `/api/v1/...` endpoints
- Native diagram repair endpoints for hidden labels, label-position resets, conveyed-item labels, and diagram-type-aware compartment presets
- Python-side proofing helpers and MCP tools for requirements, comments, state/transition names, and diagram text, including preview patch plans and optional safe auto-apply
- Methodology workflow helpers and MCP tools for comparing expected artifacts, validating package scope, exporting required diagrams, and assembling PPT/PDF submission bundles
- Semantic auto-remediation planning that converts cross-diagram validation findings into previewable receipts and `patchPlan.steps`
- `python-pptx` as an MCP-server dependency so PPTX assembly can be automated instead of remaining a manual post-step

### Fixed

- Enum-valued stereotype tagged values now resolve actual `EnumerationLiteral` instances by ID or name instead of falling through the generic JSON coercion path
- `set_specification` now benefits from the same stereotype enum coercion path as `set_tagged_values`
- Direct Python client consumers can now omit, resize, transcode, page, filter, and summarize large diagram payloads instead of reimplementing the MCP-side shaping logic
- `ibd` / `bdd` artifact kinds now participate correctly in methodology export/assembly flows instead of being dropped as non-diagrams
- The bridge capability manifest now advertises the new diagram repair endpoints alongside the earlier presentation presets

### Changed

- Bumped the in-repo Python/plugin/methodology compatibility line to `2.3.0`
- Expanded the README tool reference to document repair, proofing, methodology workflow, and remediation surfaces

## 2.1.0 - 2026-04-13

Minor release focused on hardening the MCP contract around diagram inspection and activity swimlane editing.

### Added

- CamelCase compatibility aliases for common MCP arguments such as `diagramId`, `elementId`, `parentId`, `ownerId`, and `containerPresentationId` on the affected Python tools
- Token-safe diagram export controls on `cameo_get_diagram_image`, including metadata-only responses plus optional resize and JPEG/WEBP transcoding
- Paging, filtering, nested-parent filtering, and summary-only shape inventory support on `cameo_list_diagram_shapes`
- Regression coverage for MCP schema aliases, diagram response shaping, and the guarded activity-partition fallback path

### Fixed

- Stopped the `ActivityPartition` macro fallback from deleting and rebuilding an existing swimlane container when it cannot safely resolve the partition presentation
- Forced integer rectangle dimensions in the swimlane fallback so Groovy no longer produces `Rectangle(Integer, Integer, Double, Double)` constructor failures
- Corrected the effective scope contract for `cameo_query_elements` so owner/root/package ID aliases reach the underlying bridge as element IDs

### Changed

- Bumped the Python MCP server, plugin, and OOSEM methodology pack release line to `2.1.0`
- Updated the README and release notes to document the safer large-diagram workflow and the new argument/shape-handling behavior

## 2.0.0 - 2026-04-12

Major release focused on semantic MBSE support for Cameo-based OOSEM workflows.

### Added

- Structured state-machine semantics tools for transition triggers and state `entry` / `do` / `exit` behaviors
- Semantic validation tools for activity-flow coherence, port/interface boundary consistency, requirement quality, and cross-diagram traceability
- OOSEM methodology recipes for logical activity flows, logical port BDDs, and logical IBD traceability views
- Methodology runtime integration so semantic-validator failures appear in conformance results, evidence bundles, and review packets
- Release notes for the semantic MBSE major release in `docs/releases/2026-04-12-semantic-mbse-major-release.md`

### Changed

- Bumped the MCP server, plugin, and OOSEM pack release line to `2.0.0`
- Expanded the README to document the new semantic-validation and state-semantics tool surface
- Improved review packet output to summarize semantic validators and highlight the failing evidence behind each validator
- Hardened the live bridge for the `2.0.0` cut by fixing activity-edge ownership/query behavior, replacing the interface-flow-property macro read with a native plugin endpoint, and tightening port/IBD validator matching against live Cameo models

### Stabilized Before Cut

- Added a native plugin read path for interface blocks and owned flow properties so port-boundary and cross-diagram traceability validation no longer depend on Groovy macros
- Fixed live activity-flow execution by attaching `ControlFlow` / `ObjectFlow` to the owning `Activity` and exposing `ActivityEdge` reads through `cameo_get_relationships`
- Added a guarded `ActivityPartition` fallback path so swimlane placement works reliably until the swimlane path is promoted to a dedicated Java endpoint
- Verified the release end to end against a real Cameo session with clean live smoke for `logical_activity_flow`, `logical_port_bdd`, and `logical_ibd_traceability`

### Product Story

This release moves the project from a strong generic Cameo bridge toward a semantic MBSE copilot: one that can now help create, validate, and package reviewable OOSEM artifacts instead of only manipulating notation.

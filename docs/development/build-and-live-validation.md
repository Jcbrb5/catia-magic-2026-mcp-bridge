# Build And Live Validation

This fork targets CATIA Magic 2026x on Windows. Examples assume the install is at `C:\Program Files\Magic Cyber Systems Engineer` and a JDK 21 at `C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot`; adjust to your machine. For a one-shot build and deploy, run `install.ps1` from the repo root.

## Java Build

From the repo's `plugin` folder:

```powershell
cmd.exe /c gradlew.bat test -PcameoHome="C:/Program Files/Magic Cyber Systems Engineer" -PjdkHome="C:/Program Files/Eclipse Adoptium/jdk-21.0.5.11-hotspot"
```

Use an explicit JDK 21 path. The machine default `java` may not be compatible with the plugin build.

`gradlew.bat test` runs the JUnit suites under `plugin/src/test/java`, including `BridgeAuthTest` and `RequestGuardTest` for the token and Host guard. These two classes only need the JDK, gson and JUnit, so they also compile without the CATIA Magic jars.

## Python Tests

From the repo's `mcp-server` folder:

```powershell
py -3 -m pytest tests\test_client.py tests\test_server.py
```

## Deploy

From the repo's `plugin` folder:

```powershell
cmd.exe /c gradlew.bat deploy -PcameoHome="C:/Program Files/Magic Cyber Systems Engineer" -PjdkHome="C:/Program Files/Eclipse Adoptium/jdk-21.0.5.11-hotspot"
```

Restart CATIA Magic after deploying. Do not trust newly added endpoints until `/api/v1/status` and `/api/v1/capabilities` report the rebuilt plugin. For a guided patch with backup and rollback, use `install.ps1 -PluginOnly` / `install.ps1 -Rollback` from the repo root instead (see `docs/windows-install.md`).

Live validation scripts call the Python client, so they send the token automatically as long as `%USERPROFILE%\.cameo-mcp-bridge\token` exists or `CAMEO_BRIDGE_TOKEN` is set.

The deploy task removes stale `cameo-mcp-bridge-*.jar` files from the installed plugin directory before copying the current jar. If CATIA Magic is still running, the current jar may be locked; close CATIA, rerun deploy, then reopen CATIA. When redeploying without a version bump, also verify the CATIA `javaw.exe` start time is later than the installed jar `LastWriteTime`; `/api/v1/status` can still report the same plugin version while the process is running older in-memory bytecode.

## Autopilot Route Surface Smoke Test

After CATIA Magic is restarted and a project is open, from the repo's `mcp-server` folder:

```powershell
py -3 scripts\live_validate_autopilot_route_surface.py --base-url http://127.0.0.1:18740/api/v1 --timestamped --require-open-project
```

The script writes:

```text
validation-output/autopilot-route-surface/<timestamp>/
  manifest.json
  route-surface.json
  summary.md
```

Optional-product routes should return structured unsupported/probing payloads when the relevant CATIA plugin is not installed. Treat bridge crashes, unstructured errors, or missing capability responses as failures.

## Goal-Specific Live Checks

After the 2026-05-04 Autopilot patch is deployed and CATIA Magic is restarted, validate the promoted routes with the open project:

```powershell
$base = 'http://127.0.0.1:18740/api/v1'
Invoke-RestMethod "$base/reports/templates"
Invoke-RestMethod "$base/reports/generate" -Method Post -ContentType 'application/json' -Body (@{
  templateName = 'Use Case (Simple)'
  outputPath = 'Z:\cameo-mcp-bridge\mcp-server\validation-output\goal-live\report-endpoint-proof.docx'
  format = 'docx'
  recursive = $true
  displayInViewer = $false
  allowWrite = $true
} | ConvertTo-Json)
```

For requirements apply, create a disposable package first, target that package, and keep `dryRun=true` until the preview payload is correct. A write requires both `dryRun=false` and `allowWrite=true`. Native ReqIF stays gated until a sample ReqIF roundtrip is captured.

After previewing, prove JSON/CSV apply/export only against that disposable package:

```powershell
$applyBody = @{
  format = 'json'
  dryRun = $false
  allowWrite = $true
  targetPackageId = '<disposable-package-id>'
  requirements = @(
    @{ externalId = 'MCP-REQ-001'; name = 'Roundtrip proof requirement A'; text = 'Disposable proof row.' }
  )
} | ConvertTo-Json -Depth 20
Invoke-RestMethod "$base/import-export/requirements/apply" -Method Post -ContentType 'application/json' -Body $applyBody

Invoke-RestMethod "$base/import-export/requirements/export" -Method Post -ContentType 'application/json' -Body (@{
  format = 'json'
  rootId = '<disposable-package-id>'
  limit = 50
} | ConvertTo-Json)
```

Expected proof shape: dry run reports no write, `dryRun=false` without `allowWrite=true` returns `403`, the explicit write creates the expected Requirement elements, scoped export returns only those Requirement elements, and the disposable package is deleted afterward. CATIA may mark the project dirty after create/delete validation because the undo stack changed; do not save that state unless the model owner explicitly wants it.

# Prompt: apply and verify the 2.3.6 security patch on the CATIA Magic machine

Paste everything below the line into Claude Code on the Windows computer that runs CATIA Magic 2026x. Replace `<repo>` with the checkout path (for example `C:\Users\<you>\catia-magic-2026-mcp-bridge`) and `<commit>` with the commit that contains the patch. Keep the repo on the `master` branch unless told otherwise.

---

Apply and verify the CATIA Magic MCP bridge security patch (bridge version 2.3.6). I am explicitly asking you to rebuild the plugin, redeploy it, and restart CATIA Magic; that is the one exception to the "do not install or restart the plugin" rule in AGENTS.md. Do not create, modify or delete anything in a CATIA Magic model. Stop and report if any step fails; do not hand-edit jars or the installed plugin folder.

Repo: `<repo>`. Expected commit: `<commit>`.

## 1. Update the checkout

```powershell
cd <repo>
git status --short
git pull
git log --oneline -1
```

The working tree must be clean before the pull and the top commit must be `<commit>` afterwards. If there are local changes, stop and show them to me.

## 2. Build and run the Java tests

This is the first compile of the new Java classes (`BridgeAuth`, `RequestGuard`, their tests) on a machine with the CATIA Magic jars. From `<repo>\plugin`:

```powershell
cmd.exe /c gradlew.bat clean test assemblePlugin -PcameoHome="C:/Program Files/Magic Cyber Systems Engineer" -PjdkHome="<JDK 21 path>"
```

Find the JDK 21 path with `Get-ChildItem 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java' -Directory -ErrorAction SilentlyContinue` or from `$env:JDK21_HOME`. Report the JUnit summary from `plugin\build\reports\tests\test\index.html` or the console: `BridgeAuthTest`, `RequestGuardTest` and `BridgeCapabilitiesTest` must all pass. If compilation fails, paste the first error verbatim and stop.

## 3. Install the patch

```powershell
cd <repo>
powershell -ExecutionPolicy Bypass -File .\install.ps1 -PluginOnly -SkipBuild
```

`-SkipBuild` reuses the build from step 2. If CATIA Magic is running, the installer prints a prompt and waits; tell me to close CATIA Magic, then press Enter in the installer. Approve the UAC prompt if it appears. Record from the output:

- the backup folder path (`Previous plugin backed up to: ...`)
- the deployed jar name (must be `cameo-mcp-bridge-2.3.6.jar`)

Check the installed folder:

```powershell
Get-ChildItem "C:\Program Files\Magic Cyber Systems Engineer\plugins\com.claude.cameo.bridge"
```

Only `plugin.xml` and `cameo-mcp-bridge-2.3.6.jar` should be there (no 2.3.5 jar).

## 4. Restart CATIA Magic and confirm the plugin loaded

Ask me to start CATIA Magic and open any project. Then wait for the Notification Window line:

```text
CameoMCPBridge: HTTP server started on port 18740 (generated token file C:\Users\<you>\.cameo-mcp-bridge\token)
```

(`token file` instead of `generated token file` is also fine if the file already existed.) Confirm the file exists:

```powershell
Test-Path "$HOME\.cameo-mcp-bridge\token"
(Get-Content "$HOME\.cameo-mcp-bridge\token" -Raw).Trim().Length   # expect 64
```

Do not print the token value in your reply.

## 5. Verify the HTTP guard

Run each check and record pass/fail. `Invoke-RestMethod` throws on 4xx; catch it and read `$_.Exception.Response.StatusCode`.

```powershell
$base = 'http://127.0.0.1:18740/api/v1'
$token = (Get-Content "$HOME\.cameo-mcp-bridge\token" -Raw).Trim()
$auth = @{ 'X-Cameo-Bridge-Token' = $token }

# a. Status is open and reports the new version + auth block
$status = Invoke-RestMethod "$base/status"
$status.pluginVersion          # expect 2.3.6
$status.auth                   # expect required=True, header=X-Cameo-Bridge-Token, tokenFile=<path>, macrosEnabled=True

# b. Legacy alias also open
(Invoke-RestMethod 'http://127.0.0.1:18740/status').pluginVersion   # expect 2.3.6

# c. Protected route without token -> 401 UNAUTHORIZED
try { Invoke-RestMethod "$base/project"; 'FAIL: got 200 without token' } catch { $_.Exception.Response.StatusCode.value__ }   # expect 401

# d. Protected route with wrong token -> 401
try { Invoke-RestMethod "$base/project" -Headers @{ 'X-Cameo-Bridge-Token' = 'wrong' }; 'FAIL' } catch { $_.Exception.Response.StatusCode.value__ }   # expect 401

# e. Protected route with the token -> 200 and project info
(Invoke-RestMethod "$base/project" -Headers $auth) | Format-List

# f. Foreign Host header -> 403 BAD_HOST (curl.exe ships with Windows 10+)
curl.exe -s -o NUL -w "%{http_code}`n" -H "Host: evil.example" -H "X-Cameo-Bridge-Token: $token" "$base/project"   # expect 403
curl.exe -s -H "Host: evil.example" "$base/status"   # expect JSON with "code":"BAD_HOST"

# g. Macro endpoint still works with the token (read-only script) and is rejected without it
$body = '{"script":"return 1+1","engine":"groovy"}'
Invoke-RestMethod "$base/macros/execute" -Method Post -ContentType 'application/json' -Body $body -Headers $auth
try { Invoke-RestMethod "$base/macros/execute" -Method Post -ContentType 'application/json' -Body $body; 'FAIL' } catch { $_.Exception.Response.StatusCode.value__ }   # expect 401
```

## 6. Verify the Python side and the MCP tools

From `<repo>\mcp-server`:

```powershell
.\.venv\Scripts\python.exe -m pytest -q
```

All tests must pass (expect about 191). Then start a **new** Claude Code session (the running one keeps the old MCP server process) and in it run the `cameo_status` tool and one read tool such as `cameo_get_project` or `cameo_list_diagrams`. `cameo_status` must show `pluginVersion = 2.3.6`, `compatibility.clientCompatible = true`, and an `auth` block. If a tool returns `HTTP 401`, paste the full error message; it names the token file the server looked for.

## 7. Report

Give me a table with one row per check (2 build/tests, 3 install, 4 plugin loaded, 5a-5g, 6 pytest, 6 MCP tools) and pass/fail, plus:

- the backup folder path from step 3
- the JDK path and the Gradle test counts
- anything you had to deviate from

If any check failed and we cannot fix it quickly, roll back with:

```powershell
cd <repo>
powershell -ExecutionPolicy Bypass -File .\install.ps1 -Rollback
git checkout <previous commit>
```

and restart CATIA Magic, then report what failed.

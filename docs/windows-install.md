# Installing on Windows with CATIA Magic 2026x

This guide installs the Cameo MCP Bridge on a Windows 10/11 machine that has CATIA Magic 2026x (any bundle, such as Cyber Systems Engineer or Systems of Systems Architect). When you are done, Claude Code (or another MCP client) can read and edit the model that is open in CATIA Magic.

The bridge has two parts:

| Part | Where it runs | Installed to |
|------|---------------|--------------|
| Java plugin (`com.claude.cameo.bridge`) | Inside CATIA Magic. Starts an HTTP server on `127.0.0.1:18740`. | `<CATIA Magic install>\plugins\com.claude.cameo.bridge\` |
| Python MCP server (`cameo_mcp`) | Started by your MCP client over stdio. Calls the plugin over HTTP. | `<repo>\mcp-server\.venv\` |

## 1. Prerequisites

Install these first. The commands in the right column let you check each one in PowerShell.

| Requirement | Notes | Check |
|-------------|-------|-------|
| CATIA Magic 2026x | Default install folder is `C:\Program Files\Magic Cyber Systems Engineer`. 2024x and older are not supported by this fork; use [upstream](https://github.com/ajhcs/cameo-mcp-bridge). | `Test-Path "C:\Program Files\Magic Cyber Systems Engineer\lib\md.jar"` |
| JDK 21 | Must be a full JDK (it needs `javac`). The Java runtime that ships with CATIA Magic cannot compile the plugin. [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21) (MSI installer) works. | `& "C:\Program Files\Eclipse Adoptium\jdk-21*\bin\javac.exe" -version` |
| Python 3.10 or later | Install from [python.org](https://www.python.org/downloads/windows/). Tick **Add python.exe to PATH**. The Microsoft Store `python.exe` alias is not used. | `py -3 --version` |
| Git | Or download the repo as a ZIP from GitHub instead. | `git --version` |
| Internet access | The first build downloads Gradle 8.5, and the Python step downloads packages from PyPI. | |
| Claude Code (optional) | If the `claude` command is on PATH, the installer registers the MCP server for you. | `claude --version` |

You do not need to install Gradle. The repo includes the Gradle wrapper.

## 2. Get the code

Open a normal PowerShell window (not "Run as administrator") and clone the repo to a folder you own:

```powershell
cd $HOME
git clone https://github.com/Jcbrb5/catia-magic-2026-mcp-bridge.git
cd catia-magic-2026-mcp-bridge
```

Keep this folder after installing. The MCP server runs from `mcp-server\.venv` inside it.

## 3. Run the installer

Close CATIA Magic. Then run:

```powershell
powershell -ExecutionPolicy Bypass -File .\install.ps1
```

The installer:

1. Finds the CATIA Magic install. It looks for a folder that contains `lib\md.jar` under `Program Files`, `Program Files (x86)`, `C:\` and `D:\`.
2. Finds a JDK 21. It checks `JDK21_HOME`, `JAVA21_HOME` and `JAVA_HOME`, then common vendor folders (Eclipse Adoptium, Java, Microsoft, Amazon Corretto, Zulu, BellSoft).
3. Builds the plugin with `plugin\gradlew.bat clean assemblePlugin`.
4. Copies the plugin into `<CATIA Magic>\plugins\com.claude.cameo.bridge\` and removes older plugin jars. If that folder needs administrator rights (the usual case under `C:\Program Files`), Windows shows a UAC prompt for this copy step only.
5. Creates `mcp-server\.venv` and installs the Python MCP server into it.
6. Registers the server with Claude Code as `cameo-bridge` (user scope), if the `claude` command is on PATH.

If auto-detection fails, or you have more than one CATIA Magic install, give the paths yourself:

```powershell
powershell -ExecutionPolicy Bypass -File .\install.ps1 `
    -CameoHome "C:\Program Files\Magic Cyber Systems Engineer" `
    -JdkHome "C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot"
```

Installer options:

| Option | Effect |
|--------|--------|
| `-CameoHome <path>` | CATIA Magic install folder (the one that contains `lib\md.jar`). Defaults to `CAMEO_HOME`, then auto-detection. |
| `-JdkHome <path>` | JDK 21 folder. Defaults to auto-detection. |
| `-SkipBuild` | Do not build. Deploy the existing build in `plugin\build\plugin-dist`. |
| `-SkipDeploy` | Do not copy the plugin into CATIA Magic. |
| `-SkipPython` | Do not create the venv or install the Python server. |
| `-SkipClaudeRegister` | Do not register the server with Claude Code. |
| `-PluginOnly` | Patch mode: build and deploy only the plugin. Same as `-SkipPython -SkipClaudeRegister`. |
| `-Rollback` | Restore the plugin from the newest backup under `%LOCALAPPDATA%\cameo-mcp-bridge\backups`. No build. |

Run the installer from a normal PowerShell window. If you run the whole script as administrator, the Claude Code registration can go to the administrator account instead of yours.

## 4. Check the plugin

1. Start CATIA Magic and open a project.
2. In CATIA Magic's **Notification Window**, look for:

   ```text
   CameoMCPBridge: HTTP server started on port 18740 (generated token file C:\Users\<you>\.cameo-mcp-bridge\token)
   ```

   On later starts the line says `token file` instead of `generated token file`.

3. In PowerShell, run:

   ```powershell
   Invoke-RestMethod http://127.0.0.1:18740/api/v1/status
   ```

   A working plugin returns `status = ok`, `healthy = True`, `pluginVersion = 2.3.6` and an `auth` block that names the token file.

4. Every other route needs the token. Check that the guard is on and that the token file works:

   ```powershell
   $token = (Get-Content "$HOME\.cameo-mcp-bridge\token" -Raw).Trim()
   Invoke-RestMethod http://127.0.0.1:18740/api/v1/project                                              # expect: 401
   Invoke-RestMethod http://127.0.0.1:18740/api/v1/project -Headers @{ 'X-Cameo-Bridge-Token' = $token }  # expect: project info
   ```

   `Invoke-RestMethod` throws on the 401; that is the expected result for the first call.

## Authentication token

The plugin and the MCP server share a random token. The plugin creates it on its first start and stores it in `%USERPROFILE%\.cameo-mcp-bridge\token` (a single hex line). The MCP server reads the same file, so nothing needs configuring when both run as the same Windows user.

- Every route except `/api/v1/status` and `/api/v1/capabilities` (and their legacy aliases) requires the header `X-Cameo-Bridge-Token: <token>`.
- To rotate the token, close CATIA Magic, delete the file and start CATIA Magic again. The MCP server picks up the new file on its next request.
- `CAMEO_BRIDGE_TOKEN` (environment variable) overrides the file on either side. `CAMEO_BRIDGE_TOKEN_FILE` changes the file path for the MCP server; the JVM property `-Dcameo.mcp.token.file=<path>` does the same for the plugin.
- If you start CATIA Magic with **Run as administrator**, or as another Windows account, its `user.home` is that account's profile and the token lands there. Either run CATIA Magic as yourself (recommended) or point the MCP server at that profile's token with `CAMEO_BRIDGE_TOKEN_FILE`.
- Optional: `-Dcameo.mcp.macros=false` on CATIA Magic's JVM arguments turns off the `cameo_execute_macro` tool, which runs arbitrary Groovy inside CATIA Magic.

## 5. Connect an MCP client

### Claude Code on Windows

If the installer found `claude`, this is already done. Check with:

```powershell
claude mcp list
```

To register by hand (replace the path with your repo folder):

```powershell
claude mcp add cameo-bridge --scope user -- "C:\Users\<you>\catia-magic-2026-mcp-bridge\mcp-server\.venv\Scripts\python.exe" -m cameo_mcp.server
```

Start a new Claude Code session and say `Check cameo status`. The `cameo_status` tool reports the plugin version and whether the Python server and plugin are compatible.

### Claude Code inside WSL

The plugin listens on the Windows loopback address. A Python server started inside WSL cannot reach it with the default WSL network settings. Instead, register the Windows venv interpreter from WSL. WSL runs it as a Windows process, so it can reach `127.0.0.1:18740`:

```bash
claude mcp add cameo-bridge --scope user -- \
  "/mnt/c/Users/<you>/catia-magic-2026-mcp-bridge/mcp-server/.venv/Scripts/python.exe" -m cameo_mcp.server
```

Another option is to set `networkingMode=mirrored` under `[wsl2]` in `%USERPROFILE%\.wslconfig`, run `wsl --shutdown`, and then use a Linux venv. A Linux venv does not see the Windows profile, so pass the token file path:

```bash
claude mcp add cameo-bridge --scope user \
  -e CAMEO_BRIDGE_TOKEN_FILE=/mnt/c/Users/<you>/.cameo-mcp-bridge/token \
  -- /path/to/linux/venv/bin/python -m cameo_mcp.server
```

### Claude Desktop

Add this to `%APPDATA%\Claude\claude_desktop_config.json`, then restart Claude Desktop:

```json
{
  "mcpServers": {
    "cameo-bridge": {
      "command": "C:\\Users\\<you>\\catia-magic-2026-mcp-bridge\\mcp-server\\.venv\\Scripts\\python.exe",
      "args": ["-m", "cameo_mcp.server"]
    }
  }
}
```

No `env` block is needed: the server reads the token from `%USERPROFILE%\.cameo-mcp-bridge\token`. Add `"env": { "CAMEO_BRIDGE_TOKEN_FILE": "..." }` only if the file lives elsewhere.

### Other MCP clients

Use stdio transport. Command: `<repo>\mcp-server\.venv\Scripts\python.exe`. Arguments: `-m cameo_mcp.server`. The server needs read access to the token file (see [Authentication token](#authentication-token)).

## Updating and patching

CATIA Magic loads the plugin jar only at startup, so a patch always means: rebuild, swap the jar, restart CATIA Magic. The installer does the first two steps and waits for you to close CATIA Magic:

```powershell
cd $HOME\catia-magic-2026-mcp-bridge
git pull
powershell -ExecutionPolicy Bypass -File .\install.ps1 -PluginOnly
```

What `-PluginOnly` does:

1. Builds the plugin with Gradle.
2. If CATIA Magic is running, prints a prompt and waits until you close it (Ctrl+C aborts).
3. Copies the currently installed plugin folder to `%LOCALAPPDATA%\cameo-mcp-bridge\backups\<yyyyMMdd-HHmmss>\`. Backups are kept outside the `plugins` folder so CATIA Magic never loads two copies.
4. Replaces `<CATIA Magic>\plugins\com.claude.cameo.bridge` (with a UAC prompt if needed).

The Python server is installed in editable mode, so `git pull` already updated it; the Claude Code registration does not change. Start CATIA Magic again, then start a new Claude Code session.

If the new plugin misbehaves, put the previous one back:

```powershell
powershell -ExecutionPolicy Bypass -File .\install.ps1 -Rollback
```

Then check out the matching older commit (`git log --oneline` shows the versions) so the Python server's expected plugin version matches again. A full reinstall (venv and Claude Code registration included) is still `install.ps1` with no options.

## Changing the port

The default port is `18740`. To use another port:

1. Add `-Dcameo.mcp.port=18741` to CATIA Magic's JVM arguments: append it to the `JAVA_ARGS=` line of the `.properties` file in the install's `bin` folder (some versions use a `.vmoptions` file instead; there, put it on its own line). Restart CATIA Magic.
2. Set `CAMEO_BRIDGE_PORT=18741` for the MCP server. With Claude Code: `claude mcp add cameo-bridge --scope user -e CAMEO_BRIDGE_PORT=18741 -- <python.exe> -m cameo_mcp.server`.

## Uninstalling

1. Close CATIA Magic.
2. Delete `<CATIA Magic>\plugins\com.claude.cameo.bridge\` (needs administrator rights under `C:\Program Files`).
3. Run `claude mcp remove cameo-bridge --scope user`, or remove the entry from your MCP client's configuration.
4. Delete the repo folder.

## Troubleshooting

| Symptom | Cause and fix |
|---------|---------------|
| `running scripts is disabled on this system` | Start the installer with `powershell -ExecutionPolicy Bypass -File .\install.ps1` as shown above. |
| `Could not find a CATIA Magic install` or `Found several CATIA Magic installs` | Pass `-CameoHome` with the folder that contains `lib\md.jar`. |
| `A JDK 21 is required to build the plugin` | Install a JDK 21 and pass `-JdkHome`, or set `JDK21_HOME`. A JDK 17 or 25 does not work. |
| Gradle cannot download `gradle-8.5-bin.zip` | You are behind a proxy. Add `systemProp.https.proxyHost` and `systemProp.https.proxyPort` to `%USERPROFILE%\.gradle\gradle.properties`. |
| Build fails with `cannot find symbol` or `package ... does not exist` | `-CameoHome` points at a CATIA Magic version other than 2026x, or at the wrong folder. |
| `CATIA Magic is running from this install` | Close CATIA Magic (check Task Manager for `javaw.exe` / the CATIA Magic process) and rerun. |
| `Elevation was cancelled or failed` | Rerun and approve the UAC prompt, or copy `plugin\build\plugin-dist\com.claude.cameo.bridge\*` into `<CATIA Magic>\plugins\com.claude.cameo.bridge\` yourself. |
| `Python 3.10+ not found` | Install Python from python.org with **Add python.exe to PATH**, then open a new PowerShell window. |
| No `HTTP server started` message in the Notification Window | CATIA Magic did not load the plugin. Check that `plugin.xml` and `cameo-mcp-bridge-2.3.6.jar` are in `<CATIA Magic>\plugins\com.claude.cameo.bridge\`. The plugin needs the Diagram Table and Relation Map plugins, which ship with CATIA Magic; make sure they are not disabled. Search the CATIA Magic log (`md.log`) for `com.claude.cameo.bridge` to see the load error. Help > About shows where the log file is. |
| `Failed to start HTTP server: Address already in use` | Another CATIA Magic instance, or another program, uses port 18740. Close it or [change the port](#changing-the-port). |
| `Cannot connect to CameoMCPBridge plugin at http://127.0.0.1:18740/api/v1` | CATIA Magic is not running, the plugin did not load, or the ports do not match. Run the `Invoke-RestMethod` check in step 4. From WSL, see [Claude Code inside WSL](#claude-code-inside-wsl). |
| `cameo_status` reports `clientCompatible = false`, or a tool returns HTTP 404 | The plugin in CATIA Magic is older than the Python server. Rerun the installer with `-PluginOnly` and restart CATIA Magic. |
| A tool returns `HTTP 401` / `UNAUTHORIZED` | The MCP server has no token or the wrong one. Check that `%USERPROFILE%\.cameo-mcp-bridge\token` exists (CATIA Magic writes it on first start with plugin 2.3.6 or newer), that CATIA Magic runs as the same Windows user, and that `CAMEO_BRIDGE_TOKEN` / `CAMEO_BRIDGE_TOKEN_FILE` are not pointing somewhere stale. See [Authentication token](#authentication-token). |
| `HTTP 403` / `BAD_HOST` | The request reached the bridge with a `Host` header other than `127.0.0.1` or `localhost`, for example through a proxy or a port forward. Call `http://127.0.0.1:18740` directly. |
| `HTTP 403` / `MACROS_DISABLED` | CATIA Magic was started with `-Dcameo.mcp.macros=false`. Remove the option and restart it if you need `cameo_execute_macro`. |
| No `HTTP server started` message, but `md.log` mentions the token file | The plugin could not create `%USERPROFILE%\.cameo-mcp-bridge\token` (read-only profile, disk full). Create the folder by hand or set `-Dcameo.mcp.token.file=<writable path>`. |
| Claude Code does not show the `cameo_*` tools | Run `claude mcp list` and start a new session. Run the registered `python.exe -m cameo_mcp.server` by hand to see any import error. |
| Diagram image or Relation Map calls time out | Large diagrams take a long time in CATIA Magic. Use the `max_width`/`max_height` options, or `include_image=false`. |

## Security

The plugin listens on `127.0.0.1` only and requires the shared token on every route except the status and capabilities metadata. Anyone who can read `%USERPROFILE%\.cameo-mcp-bridge\token`, or run code as your Windows user, can drive CATIA Magic through the bridge, and `cameo_execute_macro` runs arbitrary Groovy code inside it unless you set `-Dcameo.mcp.macros=false`. Only install it on a single-user workstation, do not share the token file, and do not forward port 18740 to other machines. See [Security Considerations](../README.md#security-considerations) in the README.

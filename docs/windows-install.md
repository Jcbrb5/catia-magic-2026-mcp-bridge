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

Run the installer from a normal PowerShell window. If you run the whole script as administrator, the Claude Code registration can go to the administrator account instead of yours.

## 4. Check the plugin

1. Start CATIA Magic and open a project.
2. In CATIA Magic's **Notification Window**, look for:

   ```text
   CameoMCPBridge: HTTP server started on port 18740
   ```

3. In PowerShell, run:

   ```powershell
   Invoke-RestMethod http://127.0.0.1:18740/api/v1/status
   ```

   A working plugin returns `status = ok`, `healthy = True` and `pluginVersion = 2.3.5`.

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

Another option is to set `networkingMode=mirrored` under `[wsl2]` in `%USERPROFILE%\.wslconfig`, run `wsl --shutdown`, and then use a Linux venv.

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

### Other MCP clients

Use stdio transport. Command: `<repo>\mcp-server\.venv\Scripts\python.exe`. Arguments: `-m cameo_mcp.server`.

## Updating

```powershell
cd $HOME\catia-magic-2026-mcp-bridge
git pull
powershell -ExecutionPolicy Bypass -File .\install.ps1
```

Close CATIA Magic before you run the installer, and restart it afterwards. CATIA Magic only loads the plugin at startup.

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
| No `HTTP server started` message in the Notification Window | CATIA Magic did not load the plugin. Check that `plugin.xml` and `cameo-mcp-bridge-2.3.5.jar` are in `<CATIA Magic>\plugins\com.claude.cameo.bridge\`. The plugin needs the Diagram Table and Relation Map plugins, which ship with CATIA Magic; make sure they are not disabled. Search the CATIA Magic log (`md.log`) for `com.claude.cameo.bridge` to see the load error. Help > About shows where the log file is. |
| `Failed to start HTTP server: Address already in use` | Another CATIA Magic instance, or another program, uses port 18740. Close it or [change the port](#changing-the-port). |
| `Cannot connect to CameoMCPBridge plugin at http://127.0.0.1:18740/api/v1` | CATIA Magic is not running, the plugin did not load, or the ports do not match. Run the `Invoke-RestMethod` check in step 4. From WSL, see [Claude Code inside WSL](#claude-code-inside-wsl). |
| `cameo_status` reports `clientCompatible = false`, or a tool returns HTTP 404 | The plugin in CATIA Magic is older than the Python server. Rerun the installer and restart CATIA Magic. |
| Claude Code does not show the `cameo_*` tools | Run `claude mcp list` and start a new session. Run the registered `python.exe -m cameo_mcp.server` by hand to see any import error. |
| Diagram image or Relation Map calls time out | Large diagrams take a long time in CATIA Magic. Use the `max_width`/`max_height` options, or `include_image=false`. |

## Security

The plugin accepts requests from any program on the local machine, with no authentication, and `cameo_execute_macro` runs arbitrary Groovy code inside CATIA Magic. Only install it on a single-user workstation. Do not forward port 18740 to other machines. See [Security Considerations](../README.md#security-considerations) in the README.

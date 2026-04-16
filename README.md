# Remote Deploy

[![License](https://img.shields.io/github/license/lliyuu520/RemoteDeploy)](LICENSE)
[![Stars](https://img.shields.io/github/stars/lliyuu520/RemoteDeploy?style=social)](https://github.com/lliyuu520/RemoteDeploy/stargazers)
[![Issues](https://img.shields.io/github/issues/lliyuu520/RemoteDeploy)](https://github.com/lliyuu520/RemoteDeploy/issues)
[![IntelliJ Platform](https://img.shields.io/badge/intellij%20platform-261%2B-167DFF)](https://www.jetbrains.com/idea/)
[![Java](https://img.shields.io/badge/java-21-E76F00)](https://openjdk.org/projects/jdk/21/)

Remote Deploy is an IntelliJ Platform plugin for uploading a local file or directory to a remote server over SSH and then optionally running a follow-up command on that server.

It is designed for the common "edit locally, upload quickly, run one deploy command" workflow when a full CI/CD pipeline would be too heavy for the job.

## Why Remote Deploy

- Trigger a deploy from inside your IDE instead of switching between terminals and file transfer tools
- Reuse saved SSH targets and deploy commands across repeat deploys
- Run the same deploy flow from the `Tools` menu or a reusable Run Configuration
- Keep passwords and key passphrases in IntelliJ Password Safe instead of plain-text XML

## Project Status

Remote Deploy is currently an early-stage open source project and should be treated as an MVP.

That means:

- the core deploy flow is working
- contributor onboarding is now documented
- some production-grade hardening work is still on the roadmap

If you try it and hit rough edges, opening an issue is very helpful.

## Features

- Upload a single file or an entire directory with SFTP
- Run an optional remote command after upload finishes
- Support password authentication and private-key authentication
- Save reusable SSH server definitions
- Launch deploys from `Tools -> Remote Deploy...`
- Launch deploys from a `Remote Deploy` Run Configuration
- Package plugin zip and source zip with one-click scripts

## Screenshots

Screenshots are not published yet, but the main UI surfaces are:

- Deploy dialog for selecting a server, local path, remote directory, and optional command
- Server configuration dialog for SSH target setup and connection testing
- Run Configuration editor for repeatable deploy workflows

Planned follow-up: add annotated screenshots or a short GIF for the main deploy flow.

## Quick Start

### For plugin users

1. Build the plugin zip with `package-plugin.bat` or `.\gradlew.bat buildPlugin`
2. In IntelliJ IDEA, open `Settings/Preferences -> Plugins`
3. Open the gear menu and choose `Install Plugin from Disk...`
4. Select the generated zip from `build/distributions`
5. Open `Tools -> Remote Deploy...`
6. Add an SSH server, choose the local path, enter the remote directory, and optionally provide a deploy command

### For contributors

1. Install JDK 21
2. Point the build to a local IntelliJ installation with either:
   - `platformPath` in `~/.gradle/gradle.properties`
   - `IDEA_PLATFORM_PATH` environment variable
3. Run:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21"
$env:IDEA_PLATFORM_PATH="C:\Program Files\JetBrains\IntelliJ IDEA Ultimate"
./gradlew.bat buildPlugin
```

Successful builds generate a plugin archive under `build/distributions/`.

Example `~/.gradle/gradle.properties`:

```properties
platformPath=C:/Program Files/JetBrains/IntelliJ IDEA Ultimate
```

## Usage

### Quick deploy from the Tools menu

1. Open `Tools -> Remote Deploy...`
2. Add or select an SSH server
3. Choose a local file or directory
4. Enter the remote target directory
5. Optionally enter a remote command such as `bash deploy.sh`
6. Run the deploy and inspect the command output dialog if one is shown

### Reusable run configuration

1. Open `Run -> Edit Configurations...`
2. Add a `Remote Deploy` configuration
3. Pick a saved server
4. Fill in local path, remote directory, and optional command
5. Run it like any other IntelliJ run configuration

The plugin currently ships multiple configuration presets, including upload-only and deploy-script-oriented templates.

## Packaging

### Plugin package

```powershell
./package-plugin.bat
```

This wraps the Gradle `buildPlugin` task and prints the generated plugin zip path.

### Source package

```powershell
./package-source.bat
```

This creates a source archive while excluding local-only directories such as `.git`, `.gradle`, `.idea`, `.intellijPlatform`, `build`, and `out`.

## Security Notes

- SSH passwords and private-key passphrases are stored through IntelliJ Password Safe, not in the plugin XML settings file
- The current MVP uses a permissive SSH host key verifier to reduce first-run friction
- Before production-heavy usage, host key verification should be hardened

## Contributing

Issues and pull requests are welcome.

Before opening a pull request:

1. Make sure the change fits the current plugin scope
2. Run `.\gradlew.bat buildPlugin`
3. Update docs when behavior or setup changes
4. Keep secrets, machine-local paths, and generated build output out of the repository

Please read the community files before contributing:

- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Bug Report Template](.github/ISSUE_TEMPLATE/bug_report.md)
- [Feature Request Template](.github/ISSUE_TEMPLATE/feature_request.md)

## Development Notes

- Shared project settings live in `gradle.properties`
- Contributor-specific machine paths should stay out of the repository and go into `~/.gradle/gradle.properties` or environment variables
- The plugin zip is the distributable artifact; the source zip is intended for sharing the repository contents without local build output

## Roadmap

- Harden SSH host key verification
- Add automated tests for deploy and configuration flows
- Add screenshots and a Marketplace-ready plugin page
- Improve build automation for contributor onboarding

## License

MIT. See [LICENSE](LICENSE).

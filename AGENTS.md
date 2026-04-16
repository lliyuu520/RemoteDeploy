# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java/com/liliangyu/remotedeploy/` contains the plugin code, split by responsibility: `action`, `run`, `service`, `settings`, `ui`, `model`, and `i18n`.
- `src/main/resources/META-INF/plugin.xml` declares plugin actions, services, and run configurations; localized UI strings live in `src/main/resources/messages/`.
- `src/test/java/` is reserved for automated tests and should mirror the production package layout.
- `scripts/` contains PowerShell packaging helpers, while `docs/`, `CONTRIBUTING.md`, and `SECURITY.md` hold contributor-facing documentation.

## Build, Test, and Development Commands
- `.\gradlew.bat buildPlugin` builds the plugin and writes the ZIP to `build/distributions/`.
- `.\gradlew.bat test` runs JVM and IntelliJ Platform tests from `src/test/java`.
- `.\gradlew.bat runIde` starts a sandbox IDE for manual plugin verification.
- `.\package-plugin.bat` wraps `buildPlugin`; `.\package-source.bat` creates a clean source archive without local-only folders.
- Before running Gradle, set `IDEA_PLATFORM_PATH` or `platformPath` in `~/.gradle/gradle.properties`.

## Coding Style & Naming Conventions
- Use Java 21 with UTF-8, LF line endings, and 4-space indentation as defined in `.editorconfig`.
- Follow the existing package split and keep names explicit: classes in `PascalCase`, methods/fields in `camelCase`, constants in `UPPER_SNAKE_CASE`.
- Keep UI text in `RemoteDeployBundle*.properties` instead of hardcoding strings in dialogs or services.
- Use the IDE formatter with `.editorconfig`; no separate lint configuration is committed.

## Testing Guidelines
- The build is wired for IntelliJ Platform testing, but coverage is still light; add focused tests for new logic where practical.
- Place tests under `src/test/java` with names like `SshDeployServiceTest` that mirror the production package.
- Prioritize deploy flow logic, settings persistence, and command-building behavior; document manual `runIde` checks for UI-heavy changes.
- Run `.\gradlew.bat test` and at least one manual plugin verification before opening a PR.

## Commit & Pull Request Guidelines
- Match the existing history format: `type(scope): summary`, for example `docs(readme): add independence disclaimer`.
- Keep commits focused and use meaningful scopes such as `service`, `ui`, `docs`, `plugin`, or `packaging`.
- For non-trivial work, open or discuss an issue first.
- Pull requests should complete `.github/pull_request_template.md`: summarize the change, explain why it is needed, list validation steps, and note trade-offs. Include screenshots for UI changes.

## Security & Configuration Tips
- Never commit secrets, SSH credentials, private keys, or machine-local IDE paths.
- Keep contributor-specific IntelliJ paths in user Gradle properties or environment variables, not in shared files.
- Review `SECURITY.md` before reporting vulnerabilities or changing SSH/authentication behavior.

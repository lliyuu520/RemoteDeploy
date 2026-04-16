# Third-Party Materials and Notices

This file records third-party software, notices, and redistribution assumptions used by this repository.

It exists to reduce future ambiguity around licensing, attribution, and what has or has not been copied into the repository.

This document is not legal advice. If ownership or redistribution rights are unclear, stop and verify before publishing a release.

## Current Summary

At the time of writing, this repository appears to contain:

- original project source code for `Remote Deploy`
- standard Gradle Wrapper files generated for Gradle-based builds
- third-party dependencies resolved by the build system instead of vendored source copies

At the time of writing, this repository does **not** intentionally include:

- bundled third-party image assets
- third-party logos or icons committed as standalone files
- copied third-party source trees
- copied course materials, proprietary snippets, or closed-source plugin code

If that changes, update this file in the same pull request.

## Recorded Items

### 1. Gradle Wrapper

- Material: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`
- Source: Gradle Wrapper distribution
- Purpose: project build bootstrap
- License signal in repository: wrapper scripts include Apache 2.0 license header
- Local note: standard generated build tooling, not custom copied business logic

### 2. SSHJ dependency

- Material: Maven dependency `com.hierynomus:sshj:0.40.0`
- Declared in: `build.gradle.kts`
- Source URL in published POM: `https://github.com/hierynomus/sshj`
- License declared in published POM: Apache License 2.0
- Local note: resolved through Maven/Gradle at build time, not committed here as vendored source

## How To Add A New Third-Party Item

When introducing any third-party material, add an entry with:

- what it is
- where it came from
- whether it is committed into the repository or only resolved at build/runtime
- what license applies
- whether attribution, NOTICE text, or source disclosure is required
- whether commercial use and redistribution are allowed

Recommended template:

```text
### N. <name>

- Material:
- Source:
- Files or modules affected:
- License:
- Redistribution status:
- Attribution or notice required:
- Notes:
```

## Do Not Add Without Verification

Do not add the following until you have checked the license and redistribution rights:

- copied code snippets from blogs, forums, or tutorials
- screenshots from third-party products
- logos, icons, fonts, or illustrations
- vendored jars or binaries from unknown sources
- code generated from paid or restricted materials with unclear ownership terms

## Release Reminder

Before a public release, quickly review whether this file is still accurate.

If the repository ever starts bundling third-party assets or vendored code, this file should be updated before the release is published.

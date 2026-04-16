# Open Source Release Checklist

This checklist is for reducing avoidable open-source risk before publishing code or tagging a release.

It is a practical checklist, not legal advice.

## 1. Ownership and Provenance

Before publishing, confirm all of the following:

- the code was written by you, or you clearly have the right to publish it
- no files were copied from an employer, client, paid course, closed-source plugin, or private repository without permission
- no third-party snippets were pasted in without checking their license
- no screenshots, icons, logos, or demo assets are included unless you have redistribution rights

If you are uncertain about ownership, do not publish that material yet.

## 2. License

- keep a detectable `LICENSE` file in the repository root
- make sure the license choice matches your actual intent
- do not add code from sources whose license conflicts with this repository

Current project choice:

- `MIT`, which is permissive and requires preservation of copyright and license notices

## 3. Secrets and Private Data

Check for:

- API keys
- passwords
- tokens
- SSH keys
- `.env` values
- internal server addresses
- personal data
- customer data

If any such data was ever committed, removing it from the latest commit is not always enough; treat it as leaked and rotate it where applicable.

## 4. Third-Party Dependencies and Notices

Before release, review whether the repository contains:

- vendored third-party source code
- copied documentation text
- copied screenshots or design assets
- bundled binaries

If yes, document the source and license, and add notices if required.

If not, keep it that way unless you are ready to maintain a third-party notices file.

## 5. Trademark and Branding

Avoid:

- using another company's logo, icon, or branding unless their terms allow it
- naming that suggests official affiliation when none exists
- screenshots that expose someone else's private or proprietary UI without permission

For this project, the safe default is descriptive wording and your own branding only.

## 6. GitHub Policy Risk Checks

Before publishing, confirm the repository does **not** contain:

- infringing code or assets
- unauthorized license keys or license bypass material
- malware, attack tooling without a legitimate dual-use context, or abuse infrastructure
- private personal information
- deceptive or misleading project claims

## 7. Contributor Risk Controls

To reduce future disputes:

- keep `CONTRIBUTING.md`
- keep `SECURITY.md`
- keep `CODE_OF_CONDUCT.md`
- review external pull requests for ownership, licensing, and secrets, not only correctness
- reject contributions when provenance is unclear

## 8. Release Communication

Keep public claims accurate:

- do not promise security properties you have not actually implemented
- do not promise production readiness if the project is still MVP-grade
- do not imply official support, certification, or partnership unless that is real

## 9. Final Release Gate

Before each public release, do this quick pass:

1. run `git status` and confirm no accidental files are included
2. review changed files for private data, copied material, and local paths
3. run `.\gradlew.bat buildPlugin`
4. verify `LICENSE`, `README`, `CONTRIBUTING.md`, and `SECURITY.md` are present
5. if anything about ownership or license is unclear, stop and resolve that first

## 10. When to Stop and Ask for Help

Pause the release and get legal or organizational confirmation if any of these are true:

- the code was written during paid employment and ownership is unclear
- you copied code or assets from somewhere but do not know the license
- the repo includes customer, employer, or internal company information
- a takedown, trademark complaint, or formal legal notice arrives
- you are unsure whether a dependency or asset can be redistributed

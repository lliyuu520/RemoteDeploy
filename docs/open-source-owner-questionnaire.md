# Open Source Owner Questionnaire

Use this before publishing a first release, accepting outside contributions, or responding to a licensing concern.

This is a practical self-check, not legal advice.

## Part 1: Code Ownership

Answer each question with `yes`, `no`, or `unsure`.

1. Did you write all core project code yourself?
2. Was the code written outside of employer-controlled work that might claim ownership?
3. Did you avoid copying code from closed-source repositories, paid courses, or client projects?
4. If you used snippets from blogs, forums, or AI tools, did you review whether they could safely be included here?
5. If any other person contributed, do you know which files they authored?

If any answer is `no` or `unsure`, pause public expansion until you resolve it.

## Part 2: Third-Party Material

6. Does the repository include any third-party code copied into the repo?
7. Does the repository include any third-party images, icons, fonts, screenshots, or logos?
8. If yes, have you recorded the source and license in `THIRD_PARTY.md`?
9. Do you know whether each included dependency allows redistribution in this context?
10. Have you avoided using branding that implies official affiliation with JetBrains, GitHub, or another company?

## Part 3: Secrets and Private Information

11. Have you checked that no API keys, passwords, tokens, SSH keys, or certificates are committed?
12. Have you checked that no internal hostnames, server paths, customer data, or personal data are committed?
13. If anything sensitive was ever committed in the past, has it been rotated or treated as exposed?

## Part 4: Public Claims and Risk

14. Does the README avoid overstating security, reliability, or official support?
15. Does the repository avoid any content related to malware, cracking, license bypass, or abuse tooling?
16. If someone challenged ownership of part of the code, could you explain where it came from?
17. If GitHub or a third party asked about a file's origin, do you have enough notes to answer clearly?

## Part 5: Contribution Readiness

18. Do you have a `LICENSE` file?
19. Do you have a `CONTRIBUTING.md` file?
20. Do you have a `SECURITY.md` file?
21. Do you know how you will review incoming pull requests for provenance, secrets, and licensing?

## Interpreting The Result

Good release posture:

- all ownership questions are `yes`
- all secrecy questions are `yes`
- no unanswered third-party provenance questions remain

Stop-and-check posture:

- any `unsure` on ownership
- any third-party material with unknown license
- any past or current secret exposure
- any branding or screenshots you are not sure you can publish

## Suggested Next Action

If you answer `unsure` anywhere, write down:

- which file or material is in doubt
- where it came from
- what decision is blocked
- what proof or confirmation you still need

That turns a vague legal fear into a small, concrete checklist you can actually resolve.

# Security Policy

## Supported Versions

This project is currently maintained on a best-effort basis. The latest release on `main` should be treated as the supported line.

## How to Report a Vulnerability

If you believe you found a security issue:

1. Do **not** post secrets, credentials, private infrastructure details, or full exploit steps in a public issue
2. Contact the maintainer through the repository owner profile: [https://github.com/lliyuu520](https://github.com/lliyuu520)
3. Include enough detail to reproduce and assess the issue safely

Suggested report format:

- affected version or commit
- vulnerability summary
- impact
- reproduction steps
- whether credentials or private data may be exposed
- any suggested mitigation

## What to Avoid in Public Issues

Please avoid posting:

- passwords, tokens, SSH private keys, or certificates
- customer or employee data
- internal hostnames, server paths, or private endpoints
- exploit payloads that would create immediate abuse risk

## Response Expectations

This is a personal open source project, so response times are not guaranteed. The goal is to review good-faith reports reasonably and fix confirmed issues as time allows.

## Scope Notes

Current known security limitation:

- the plugin currently uses lightweight SSH host verification and should be treated as an MVP in this area

If you are evaluating the plugin for production-heavy use, review the current implementation and threat model before relying on it.

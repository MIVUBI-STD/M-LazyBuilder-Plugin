# Security Policy

## Sensitive data

Never commit credentials, API tokens, private keys, database passwords, production configuration, player/private operational data, or server access details.

Use environment variables or ignored local configuration for secrets. Provide sanitized examples only when configuration documentation is needed.

## Plugin-specific safety

Changes involving any of the following require explicit review and targeted verification:

- permissions or privilege checks;
- command execution as console or another player;
- file path construction and filesystem writes;
- network/database access;
- deserialization or externally supplied structured data;
- packet/NMS access;
- asynchronous access to Paper/Bukkit state;
- persistent player/world data migrations.

Fail closed for authorization-sensitive behavior. Validate untrusted input at system boundaries and log failures without exposing secrets.

## Reporting

Do not publish exploitable server details, credentials, or private production data in issues or commits. Report sensitive findings privately to the repository owner.

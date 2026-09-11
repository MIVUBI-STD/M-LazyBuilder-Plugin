# LazyBuilder Plugin

Minecraft Java 1.21.4 plugin workspace for LazyBuilder.

## Repository model

```text
Local  -> active development / working authority
main   -> stable / release authority
```

Normal implementation work belongs on `Local`. `main` changes only when the maintainer explicitly promotes a verified state.

## Start here

1. Read `AGENTS.md` for task routing and scope.
2. Read `GITHUB_RULES.md` before repository mutations.
3. Use `docs/README.md` as the documentation index.
4. Keep each feature behind one clear owner and one execution path.

The repository is intentionally minimal at bootstrap. Application architecture and plugin features will be added only when their responsibilities are known, rather than pre-creating empty framework layers.

# Launcher package.json policy

Recommended addition to `apps/launcher/package.json`:

```json
"engines": {
  "node": "24.x"
}
```

Keep `package-lock.json` committed and use `npm ci` for local and CI builds.

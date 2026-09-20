import { existsSync, statSync } from "node:fs";
import { resolve } from "node:path";
import { spawnSync } from "node:child_process";

const root = resolve(import.meta.dirname, "..");
const source = resolve(root, "src-tauri", "app-icon.svg");
const outputs = [
  resolve(root, "src-tauri", "icons", "32x32.png"),
  resolve(root, "src-tauri", "icons", "128x128.png"),
  resolve(root, "src-tauri", "icons", "128x128@2x.png"),
  resolve(root, "src-tauri", "icons", "icon.ico"),
];

const sourceMtime = statSync(source).mtimeMs;
const fresh = outputs.every((path) => existsSync(path) && statSync(path).mtimeMs >= sourceMtime);
if (fresh) {
  process.exit(0);
}

const executable = "npx";
const result = spawnSync(executable, ["tauri", "icon", source], {
  cwd: root,
  stdio: "inherit",
  shell: process.platform === "win32",
});
if (result.error) throw result.error;
process.exit(result.status ?? 1);

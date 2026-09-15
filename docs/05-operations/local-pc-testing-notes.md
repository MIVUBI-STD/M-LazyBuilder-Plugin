# Catatan Testing Local PC

Dokumen ini mencatat seluruh aktivitas validasi Local PC untuk source branch `Local`.

## Identitas source

- Repository: `halokaryamedia-source/LazyBuilder-Plugin`
- Branch: `Local`
- Commit yang diuji: `b5307737125872ac8fe17590979109e94c945aed`
- Checkout lokal: `D:\Work\AI Stuff\LazyBuilder`
- Tanggal pengujian: 15 September 2026
- Otoritas source: `origin/Local`

## 1. Sinkronisasi source

1. Folder lokal `D:\Work\AI Stuff\LazyBuilder` belum tersedia.
2. Repository di-clone langsung dari GitHub pada branch `Local`.
3. Checkout awal berada pada commit `b530773`.
4. Perubahan lokal pada `mvnw.cmd` ditemukan sebagai perbedaan line ending Windows, bukan perbedaan isi source.
5. Semua file lokal ignored/untracked di dalam checkout dibersihkan.
6. `HEAD` dan `origin/Local` diverifikasi sama.

## 2. Pemeriksaan source awal

Lulus:

- `scripts/verify_versions.py`
- `scripts/verify_local_launcher_contract.py`
- Struktur repository dan kontrak launcher

Awalnya gagal/terblokir:

- `scripts/verify_client_artifacts.py`: artefak `.artifacts/client-mods` belum tersedia.
- Toolchain repo belum terbaca siap oleh script bootstrap.

## 3. Persiapan developer environment

Komponen yang tersedia atau dipasang:

- Java Temurin 21
- Node.js 24 dan npm
- Rust `1.98.1` sesuai `rust-toolchain.toml`
- Cargo `1.98.1`
- Visual Studio 2022 Build Tools dengan workload C++/MSVC
- Maven wrapper repo 3.9.16
- Gradle wrapper repo 8.12

Kendala:

- Script PowerShell lama salah membaca output `java -version` dan `node --version` pada shell Windows tertentu, walaupun Java dan Node sebenarnya terpasang.
- Instalasi Rust sempat memiliki metadata Cargo parsial; toolchain di-uninstall dan dipasang ulang sampai `rustc` dan `cargo` normal.
- BuildTools awal belum memiliki `cl.exe`; workload C++ kemudian dipasang.

## 4. Validasi frontend dan Rust/Tauri

Lulus:

- `npm ci`
- `npm run typecheck`
- `npm run build:frontend`
- `npm run prepare:icons`
- `cargo check --locked --manifest-path apps/launcher/src-tauri/Cargo.toml`
- `cargo test --locked --manifest-path apps/launcher/src-tauri/Cargo.toml`
- Rust/Tauri tests: 15 passed, 0 failed

Catatan:

- Build Rust pertama berhenti karena ikon Tauri belum digenerate. `npm run prepare:icons` menyelesaikan masalah tersebut.
- Ada warning dead code pada `workspace_registry.rs`, tetapi tidak menggagalkan build.

## 5. Build Paper plugins

Lulus:

- Protocol tests: 12 passed
- World Manager tests: 140 passed
- Utilities Manager tests: 25 passed
- Maven reactor build: `BUILD SUCCESS`

Artefak yang dihasilkan:

- `World-Manager-0.1.0-SNAPSHOT.jar`
- `Utilities-Manager-0.1.0-SNAPSHOT.jar`

Ada warning Maven Shade tentang overlapping classes/resources. Build tetap sukses.

## 6. Build Fabric mods

Lulus setelah workaround environment:

- Map Manager: `BUILD SUCCESSFUL`
- Utility Manager: `BUILD SUCCESSFUL`
- Performance Manager: `BUILD SUCCESSFUL`
- Client artifact verification: `Client artifacts OK: 3 Fabric managers`

Kendala:

- Gradle awalnya gagal dengan `Unable to establish loopback connection` dan `Invalid argument: connect`.
- Penyebab praktisnya adalah Java/Gradle gagal membuat pipe loopback menggunakan TEMP Windows saat itu.
- Solusi tanpa restart: memakai folder temporary khusus:

```powershell
New-Item -ItemType Directory -Force C:\LazyBuilderTemp
$env:TEMP = 'C:\LazyBuilderTemp'
$env:TMP = 'C:\LazyBuilderTemp'
```

- Beberapa proses Gradle/Java yang tertinggal memegang lock Fabric Loom. Proses build stale dihentikan secara terarah, lalu build modul dijalankan satu per satu.

## 7. Build dan instalasi Launcher

Installer berhasil dibuat dengan mode:

```text
runtime-ready (server + Modrinth Client Setup)
```

Installer:

```text
D:\Work\AI Stuff\LazyBuilder\dist\LazyBuilder\LazyBuilder-Setup.exe
```

SHA256:

```text
10606317FAE9B19980DE1CDAC68978F6ECE086BE01949C4DAA9C16F51A0FC295
```

Instalasi berhasil:

```text
C:\Users\Administrator\AppData\Local\LazyBuilder
```

Launcher terdaftar di Windows dan berhasil dibuka dengan status responsif.

## 8. Server lama `Test`

Server lama ditemukan dan dipakai, bukan membuat server baru.

Path workspace:

```text
D:\Work\Minecraft\Java-Version\Java Build Server\Test\Test
```

Ditemukan:

- Entry server `Test` masih ada di `workspaces.json`.
- `server/paper.jar` tersedia.
- `server/eula.txt` tersedia.
- `server/plugins` tersedia.
- `world-system` dan world lama tersedia.

## 9. Crash Paper pertama

UI menampilkan:

```text
A previous managed Paper process is still running as PID 36740
```

PID tersebut kemudian tidak ditemukan; pesan itu adalah status proses lama/stale setelah crash.

Log Paper menunjukkan server berhasil memulai sampai port `25565`, lalu gagal dengan:

```text
java.io.IOException: Unable to establish loopback connection
java.net.SocketException: Invalid argument: connect
```

Paper crash report:

```text
D:\Work\Minecraft\Java-Version\Java Build Server\Test\Test\server\crash-reports\crash-2026-09-15_20.56.16-server.txt
```

Masalah bukan world rusak dan bukan plugin Paper. Ini sama dengan masalah Gradle: Java/Netty gagal membuat loopback pipe pada TEMP Windows.

Launcher kemudian dijalankan dengan TEMP khusus `C:\LazyBuilderTemp`; proses Launcher responsif.

## 10. Sinkronisasi plugin dan client mods

Paper plugins pada server `Test` sudah sesuai artefak build terbaru:

- `World-Manager-0.1.0-SNAPSHOT.jar`
- `Utilities-Manager-0.1.0-SNAPSHOT.jar`

Mod baru dipasang ke profile Modrinth yang digunakan untuk pengujian:

```text
C:\Users\Administrator\AppData\Roaming\ModrinthApp\profiles\1.21.4 Testing
```

Mod yang dipasang:

- `lazybuilder-map-manager-0.1.0-SNAPSHOT.jar`
- `lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar`
- `lazybuilder-performance-manager-0.1.0-SNAPSHOT.jar`

Mod lama dipindahkan menjadi:

```text
lazybuilder-client-0.1.0-SNAPSHOT.jar.disabled
```

Mod pihak ketiga tidak diubah.

## 11. Tes Map Manager dan Xaero

Lulus secara visual/fungsional awal:

- Map Manager terbuka di Minecraft.
- World `Overworld` terlihat.
- Peta dan data world tampil.
- Tombol `Worlds` digunakan untuk kembali ke daftar world.
- Tampilan Xaero dapat dipakai terpisah dari World Manager.

## 12. Tes Import/Export

Temuan:

- Pilihan export yang terlihat hanya `Java Edition 1.21.4`.
- Ini sesuai implementasi current UI untuk native Java export.
- Export Bedrock bukan pilihan aktif pada build ini karena conversion runtime Bedrock belum tersedia/terverifikasi pada PC.
- Source/dokumentasi memang mendeskripsikan Java↔Bedrock conversion, tetapi UI capability-driven menyembunyikan target yang belum dilaporkan backend sebagai supported.
- Ini bukan kerusakan Xaero atau crash.

## 13. Tes Utility Manager

Fitur yang seharusnya diuji:

- Keep Chat Draft
- Extended Chat History
- Reconnect Button
- Copy Connection Details
- Resource Reload Notice melalui `F3+T`
- Contextual Screenshot Names, jika diaktifkan
- Borderless Window, jika diaktifkan

Konfigurasi profile:

```text
C:\Users\Administrator\AppData\Roaming\ModrinthApp\profiles\1.21.4 Testing\config\lazybuilder-utility-manager.properties
```

Konfigurasi saat dicek:

```properties
chat.extended_history=true
chat.keep_draft=true
connection.reconnect_button=true
screenshots.contextual_names=false
window.borderless=false
```

### Isu Reconnect yang belum selesai

Profile yang dipakai sudah dikonfirmasi benar-benar `1.21.4 Testing`. Pernyataan sebelumnya yang mengasumsikan profile mungkin salah harus diabaikan.

Pengguna melaporkan bahwa setelah disconnect, Minecraft langsung kembali ke server list dan tombol `Reconnect` tidak muncul.

Source menunjukkan tombol hanya ditambahkan jika dua syarat terpenuhi:

```java
preferences.reconnectButton()
ReconnectState.canReconnect()
```

`ReconnectState` hanya menyimpan server terakhir di memory sesi melalui event:

```java
ClientPlayConnectionEvents.JOIN
```

Konfigurasi `connection.reconnect_button=true` sudah benar. Maka dugaan yang tersisa adalah:

1. event JOIN tidak menangkap `ServerInfo` pada jalur koneksi yang sedang dipakai;
2. `ReconnectState.lastServer` kosong saat `DisconnectedScreen.init` berjalan;
3. mixin `DisconnectedScreenMixin` tidak terpasang/terpicu pada runtime profile tersebut;
4. layar yang terlihat adalah layar server list yang dibuka melalui jalur lain, bukan `DisconnectedScreen`.

Status: **belum resolved**. Perlu log client/runtime atau reproduksi dengan memeriksa apakah Utility Manager benar-benar loaded pada sesi yang sama.

## 14. Kesimpulan saat handoff

Source/build/install pipeline sudah berhasil sampai installer runtime-ready dan server lama `Test` sudah digunakan.

Lulus utama:

- Source exact `origin/Local`
- Maven/Paper tests
- Fabric builds
- Frontend/Tauri tests
- Installer build/install
- Server lama terdeteksi
- Map Manager/Xaero tampil
- Plugin dan client mods terbaru terpasang

Belum selesai:

- Paper runtime loopback/TEMP workaround perlu dipertahankan saat Launcher/server dijalankan.
- Bedrock conversion belum aktif/terverifikasi.
- Utility Manager Reconnect Button tidak muncul walaupun profile `1.21.4 Testing` dan konfigurasi `connection.reconnect_button=true` sudah benar.
- Tes Utility Manager lain: chat draft, F3+T notification, screenshot naming, dan borderless window belum seluruhnya selesai.

## 15. Prioritas lanjutan

1. Reproduksi dan diagnosis `Reconnect Button` pada profile `1.21.4 Testing`.
2. Verifikasi Utility Manager benar-benar dimuat dari log client.
3. Jalankan tes chat draft dan `F3+T`.
4. Jalankan native Java export/import.
5. Baru lanjutkan conversion Bedrock dengan runtime yang tervalidasi.


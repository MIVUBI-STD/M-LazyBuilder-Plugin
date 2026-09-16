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

## 16. Kronologi lanjutan setelah sinkronisasi terbaru

Bagian ini melanjutkan catatan di atas untuk sesi sinkronisasi dan testing pada 16 September 2026.

### 16.1 Sinkronisasi source terbaru

- Repository diambil dari `https://github.com/halokaryamedia-source/LazyBuilder-Plugin.git`, branch `Local`.
- Local source diarahkan ke `origin/Local`; perubahan lokal lama tidak dijadikan source authority.
- Commit terbaru yang terdeteksi saat sinkronisasi: `7be4b29c test(world): assert canonical converter setting paths`.
- Sisa line-ending pada `gradlew.bat` dan `mvnw.cmd`, serta artefak generated `apps/launcher/src-tauri/gen/` dan `apps/launcher/src-tauri/icons/`, tetap muncul sebagai perubahan/untracked lokal dan belum menjadi perubahan source yang dipush.

### 16.2 Source verification yang berhasil

Focused Maven verification pada source terbaru:

```text
Terraform Core       8/8 tests passed
World Manager        147/147 tests passed
Utilities Manager    25/25 tests passed
```

Launcher verification:

```text
npm ci                passed; 0 vulnerabilities
npm run typecheck     0 errors, 0 warnings
npm run build:frontend passed
```

Paper runtime proof kemudian berhasil membuktikan:

```text
Paper boot
plugin enable
authenticated local control
status/world contracts
create
settings
archive/restore
duplicate
backup
native export
upload
import
delete
```

Paper restart persistence proof juga berhasil membuktikan clean shutdown, restart, registry reload, filesystem/world resolution, settings access, dan post-restart deletion.

### 16.3 Perbaikan kompatibilitas PowerShell yang dilakukan

Beberapa script memakai API `ProcessStartInfo.ArgumentList`, yang tidak tersedia pada Windows PowerShell 5.1. Perubahan lokal yang sudah dilakukan:

- `scripts/verify-paper-runtime.ps1`: memakai `ProcessStartInfo.Arguments` dengan path JAR yang di-quote.
- `scripts/verify-paper-restart.ps1`: memakai `ProcessStartInfo.Arguments` dengan path JAR yang di-quote.
- `tooling/windows-toolchain/scripts/verify/test-local.ps1`: menghapus pemeriksaan `$LASTEXITCODE` setelah memanggil script PowerShell internal; script sukses tidak lagi dianggap gagal hanya karena `$LASTEXITCODE` belum ada.
- `tooling/windows-toolchain/scripts/build/preflight.ps1`: otomatis menambahkan `%USERPROFILE%\.cargo\bin` ke PATH proses sebelum memeriksa `rustc`/`cargo`; pemeriksaan Java memakai `java --version` dan menerima format output OpenJDK/Java 21.
- `tooling/windows-toolchain/scripts/distribution/verify-installer.ps1`: akses properti registry `DisplayName`, `InstallLocation`, dan `DisplayIcon` dibuat aman jika properti tidak tersedia.

Validasi parser PowerShell untuk script terkait: **PASS**.

### 16.4 Fabric/Gradle environment blocker

Fabric Map Manager belum dapat diverifikasi dari terminal Codex karena Gradle/Java gagal membuat selector internal:

```text
java.io.IOException: Unable to establish loopback connection
java.net.SocketException: Invalid argument: connect
```

Percobaan yang dilakukan:

- `TEMP` dan `TMP` ke `C:\LazyBuilderTemp`, `C:\Windows\Temp`, dan path pendek lainnya.
- `GRADLE_USER_HOME` ke cache khusus.
- `GRADLE_OPTS` dengan dan tanpa `java.io.tmpdir`.
- Gradle `--no-daemon`.
- Java Temurin 21.0.12.
- Java Zulu 21.0.8.
- mematikan penggunaan native Gradle.

Error tetap terjadi sebelum kompilasi Fabric. TCP loopback Windows biasa berhasil, sehingga kegagalan berada pada Java NIO `PipeImpl`/AF_UNIX di sandbox Codex, bukan pada source Fabric. Build harus dijalankan dari PowerShell/Windows Terminal native di luar sandbox dengan TEMP sangat pendek.

### 16.5 Installer dan Local PC test

- `DEV.cmd test` awalnya gagal setelah Paper runtime proof karena `$LASTEXITCODE` belum tersedia; sudah diperbaiki.
- Paper restart proof kemudian lulus.
- Installer smoke verifier awalnya gagal pada item registry tanpa properti `DisplayName`; sudah diperbaiki.
- Artifact installer `dist/Local/LazyBuilder-Setup-Local.exe` belum konsisten tersedia sampai Fabric build berhasil pada terminal native.
- Installer smoke sengaja membuka Launcher sekitar lima detik lalu menutupnya; penutupan tersebut bukan crash.

### 16.6 Server dan profile yang dipakai

Server yang dipakai untuk testing:

```text
D:\Work\Minecraft\Java-Version\Java Build Server\1.21.4 - Testing\1.21.4 - Testing\server
```

Plugin server yang terdeteksi:

```text
World-Manager-0.1.0-SNAPSHOT.jar
Utilities-Manager-0.1.0-SNAPSHOT.jar
Terraform-Manager-0.1.0-SNAPSHOT.jar
```

Log Paper mengonfirmasi:

```text
World-Manager enabled.
Utilities-Manager enabled with 3 registered feature families and 8/8 command bindings ready.
```

Profile Modrinth yang dipakai:

```text
C:\Users\Administrator\AppData\Roaming\ModrinthApp\profiles\1.21.4 Testing
```

Mod LazyBuilder yang terdeteksi:

```text
lazybuilder-map-manager-0.1.0-SNAPSHOT.jar
lazybuilder-performance-manager-0.1.0-SNAPSHOT.jar
lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar
lazybuilder-terraform-manager-0.1.0-SNAPSHOT.jar
```

Catatan: `lazybuilder-client-0.1.0-SNAPSHOT.jar.disabled` tetap disabled dan bukan bagian dari empat mod manager terbaru.

### 16.7 OP dan permission

Player `Berchman` terdeteksi dengan UUID:

```text
7fee50f6-17ad-4ada-95e0-4595e943cc54
```

Player tersebut ditambahkan ke `ops.json` dengan level 4 dan bypass player limit. Paper harus direstart agar perubahan file dibaca ke memory. Error `Missing LazyBuilder.world permission` berarti sesi server belum memuat perubahan OP atau action yang diuji membutuhkan permission World Manager pada sesi tersebut.

### 16.8 UI blur terakhir — BELUM DIPERBAIKI

Pengguna melaporkan Map Manager/sidebar Minecraft terlihat sangat buram sehingga teks tidak terbaca.

Investigasi source menemukan:

- `WorldMapScreen` menggambar sidebar dengan warna solid dan tidak memiliki operasi blur.
- Visual proof test Map Manager memang mengatur `client.options.getMenuBackgroundBlurriness()` menjadi `0` selama proof.
- Profile `1.21.4 Testing` memiliki shader stack (Iris/Kappa) dan opsi menu background blur dapat memengaruhi tampilan screen.

Perubahan lokal sementara sudah dicoba di `WorldMapScreen.java`:

- menyimpan nilai blur sebelumnya;
- mengatur menu background blurriness ke `0` saat `WorldMapScreen.init()`;
- memulihkan nilai sebelumnya saat `close()`.

Status tetap: **UNRESOLVED**. Pengguna melaporkan tampilan masih buram setelah perubahan tersebut. Perubahan itu belum dibuktikan melalui build Fabric terbaru dan belum boleh dianggap sebagai fix. Diagnosis berikutnya harus memakai screenshot/runtime build exact-head, memeriksa apakah blur berasal dari shader/profile atau dari jalur screen lain, lalu menambah visual proof yang benar-benar menangkap sidebar.

### 16.9 Status handoff saat ini

Sudah terbukti:

- source Maven Paper dan unit tests terkait;
- Paper runtime dan restart persistence;
- Utilities Manager aktif di server;
- Terraform plugin/mod sudah dipasang ke server/profile Testing;
- launcher frontend build dan PowerShell verifier compatibility fixes.

Belum terbukti/masih bermasalah:

- full Fabric build dari terminal native pada exact current local source;
- installer Local exact-head setelah Fabric build;
- UI sidebar blur pada Minecraft runtime;
- permission World Manager setelah sesi server benar-benar restart;
- Utility reconnect flow dan Bedrock conversion end-to-end.

Jangan menyatakan Local PC acceptance selesai sebelum Fabric build, installer provenance, dan unresolved UI/runtime findings di atas ditutup atau diberi status environment-only dengan bukti yang memadai.

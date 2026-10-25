# GridCraft for Android

Standalone offline Android APK installer for **GridCraft** (`storytold/gridcraft`), the modern, clean-room open-source reimplementation of Microsoft Excel written in pure Rust.

Designed specifically for Android tablets and large screen devices like the **Xiaomi Pad 7** (11.2", 3.2K display, keyboard dock support).

---

## Highlights

- **100% Offline Standalone APK:** Bundles GridCraft's WebAssembly engine and assets directly inside the APK. No internet connection required.
- **Desktop Microsoft Excel Experience:** Full desktop ribbon UI, formula bar, Name Box, sheet tabs, autofill handle, and dark mode.
- **XLSX / Data Compatibility:** Reads and writes `.xlsx`, `.csv`, `.tsv`, with legacy `.xls` and `.ods` import.
- **Comprehensive Formula Engine:** 500+ standard worksheet functions, dynamic arrays, `XLOOKUP`, `INDEX/MATCH`, `LET`, `LAMBDA`, `FILTER`, `UNIQUE`.
- **Advanced Data Tools:** Pivot tables, conditional formatting rules, 17 chart types, sparklines, and auto-filters.
- **Hardware Acceleration:** Native WebGL2/WebGPU rendering on Android at 120Hz/144Hz.
- **Physical Keyboard & Shortcut Support:** Seamlessly supports Xiaomi Pad 7 keyboard dock with shortcuts like `Ctrl+C`, `Ctrl+V`, `Ctrl+Z`, `Ctrl+S`, `F2`.
- **Integrated Android Storage:** Saves workbooks directly to `Downloads/GridCraft/` and opens existing files using the native Android document picker.

---

## Installation

1. Download the latest `GridCraft-v0.11.1-android.apk` from [GitHub Releases](https://github.com/jimbon25/gridcraft-android/releases) or the latest Actions Artifact.
2. Install the APK on your Android tablet or phone.
3. Open **GridCraft** and start crunching numbers!

---

## Automated CI / CD

The entire build pipeline is executed automatically on **GitHub Actions (`ubuntu-latest`)**:
- Bundles official upstream WebAssembly assets from [storytold/gridcraft](https://github.com/storytold/gridcraft).
- Packages Android assets with secure `WebViewAssetLoader` and MIME streaming for `.wasm`.
- Builds and signs the release APK with Android Gradle.
- Publishes artifacts and GitHub Releases.

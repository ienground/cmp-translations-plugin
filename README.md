<p align="center">
  <img src="src/main/resources/META-INF/pluginIcon.png" width="150" alt="Compose Multiplatform Translations Logo" />
</p>

# Compose Multiplatform Translations

<p align="center">
  <b>English</b> | <a href="README_ko.md">한국어</a>
</p>

<p align="center">
  <a href="https://plugins.jetbrains.com"><img src="https://img.shields.io/badge/JetBrains%20Marketplace-v0.4.0%20(beta)-blue?logo=jetbrains" alt="JetBrains Marketplace" /></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.1.0-blue?logo=kotlin" alt="Kotlin" /></a>
  <a href="https://www.jetbrains.com/idea/"><img src="https://img.shields.io/badge/IDE-IntelliJ%20IDEA%20%7C%20Android%20Studio-green?logo=intellijidea" alt="Supported IDEs" /></a>
  <a href="https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html"><img src="https://img.shields.io/badge/Compatible%20Build-2024.2+-orange?logo=jetbrains" alt="Compatibility" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License" /></a>
</p>

A powerful IntelliJ Platform plugin for IntelliJ IDEA and Android Studio designed to manage Compose Multiplatform `composeResources` string resources with a dedicated translation grid, real-time validation, and safe PSI-based XML editing.

---

## Features & Support Matrix

- **Kotlin-First & Compose Multiplatform Native**: Built specifically for the official `composeResources` directory hierarchy across any source set (`commonMain`, `androidMain`, `iosMain`, etc.).
- **Centralized Translation Grid**: Edit default strings and localized translations (`values-ko`, `values-ja`, `values-es`, etc.) side-by-side in an intuitive tabular view.
- **Real-Time Validation & Linting**: Instantly flags missing translations, orphan keys, duplicate definitions, and printf-style placeholder (`%s`, `%d`) mismatches.
- **Safe PSI-Based Operations**: Employs IntelliJ XML PSI and `WriteCommandAction` for adding, modifying, deleting, and renaming keys with full undo/redo integration.
- **Zero Runtime Overhead**: Acts purely as IDE tooling; modifies raw XML resource files directly without introducing runtime dependencies or changing code generation behaviors.

### Support Matrix

| Feature | IntelliJ IDEA | Android Studio | Completion Rate | Under the Hood |
| :--- | :---: | :---: | :---: | :--- |
| **String Resource Editing (`<string>`)** | ✅ Supported | ✅ Supported | 100% | IntelliJ XML PSI & `WriteCommandAction` |
| **Multi-SourceSet Discovery** | ✅ Supported | ✅ Supported | 100% | Virtual File System (VFS) indexer |
| **Missing & Orphan Key Validation** | ✅ Supported | ✅ Supported | 100% | Key set differential analyzer |
| **Placeholder Mismatch Detection** | ✅ Supported | ✅ Supported | 100% | Regex printf token validator (`%s`, `%d`, etc.) |
| **Direct XML Tag Navigation** | ✅ Supported | ✅ Supported | 100% | PSI Element target locator (double-click cell) |
| **Status Filter (`All`, `Missing`, `Complete`)** | ✅ Supported | ✅ Supported | 100% | Dynamic table row filter model |
| **Plural Resources (`<plurals>`)** | 🟡 Planned | 🟡 Planned | 0% | In Roadmap |
| **String Array Resources (`<string-array>`)** | 🟡 Planned | 🟡 Planned | 0% | In Roadmap |
| **AI Translation Provider Integration** | 🟡 Planned | 🟡 Planned | 0% | In Roadmap |

---

## Installation

### JetBrains Marketplace

1. Open your IDE (**IntelliJ IDEA** or **Android Studio**).
2. Navigate to **Settings / Preferences** (`⌘,` on macOS or `Ctrl+Alt+S` on Windows/Linux) > **Plugins**.
3. Select the **Marketplace** tab and search for `Compose Multiplatform Translations`.
4. Click **Install** and restart the IDE if prompted.

> [!TIP]
> During beta releases, add the beta channel repository `https://plugins.jetbrains.com/plugins/beta/list` under **Plugins > ⚙️ > Manage Plugin Repositories...**.

### Manual Installation (ZIP)

1. Download the latest release `.zip` from [GitHub Releases](https://github.com/ienground/cmp-translations-plugin/releases).
2. In your IDE, go to **Settings / Preferences > Plugins**.
3. Click the gear icon (⚙️) and choose **Install Plugin from Disk...**.
4. Select the downloaded ZIP file and restart the IDE.

### Gradle Project Setup

Ensure your Compose Multiplatform project is configured with the official Compose Resources library in your `build.gradle.kts`:

```kotlin
plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
        }
    }
}
```

> [!IMPORTANT]
> **Minimum Requirements:**
> - **IDE**: IntelliJ IDEA 2024.2+ or Android Studio Ladybug (2024.2+) / Meerkat (2024.3+)
> - **JDK**: Java 21 or higher (when building from source)
> - **Compose Multiplatform**: 1.6.0 or higher with `composeResources` support

---

## Running the Sample App

To test and explore the plugin in action:

1. Clone this repository:
   ```bash
   git clone https://github.com/ienground/cmp-translations-plugin.git
   cd cmp-translations-plugin
   ```
2. Launch a sandboxed IDE instance containing the plugin:
   ```bash
   ./gradlew runIde
   ```
3. In the launched IDE instance, open any Compose Multiplatform project containing `composeResources`.
4. Open the **Compose Translations** Tool Window from the bottom or right sidebar.
5. Select a source set (such as `commonMain`) from the **Resource set** dropdown to view and manage strings.

---

## Usage Example

### 1. Directory Structure

The plugin automatically detects and manages string files structured according to Compose Multiplatform conventions:

```text
my-project/
└── src/
    └── commonMain/
        └── composeResources/
            ├── values/
            │   └── strings.xml        <-- Default (fallback) locale
            ├── values-ko/
            │   └── strings.xml        <-- Korean locale
            └── values-ja/
                └── strings.xml        <-- Japanese locale
```

### 2. XML Resource Definition

`src/commonMain/composeResources/values/strings.xml`:
```xml
<resources>
    <string name="app_name">My Application</string>
    <string name="welcome_user">Welcome, %s!</string>
    <string name="items_count">You have %d items.</string>
</resources>
```

`src/commonMain/composeResources/values-ko/strings.xml`:
```xml
<resources>
    <string name="app_name">내 애플리케이션</string>
    <string name="welcome_user">%s님, 환영합니다!</string>
    <string name="items_count">%d개의 항목이 있습니다.</string>
</resources>
```

### 3. Using in Compose Multiplatform Code

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import myproject.composeapp.generated.resources.Res
import myproject.composeapp.generated.resources.app_name
import myproject.composeapp.generated.resources.welcome_user
import myproject.composeapp.generated.resources.items_count

@Composable
fun WelcomeScreen(userName: String, count: Int) {
    Column {
        Text(text = stringResource(Res.string.app_name))
        Text(text = stringResource(Res.string.welcome_user, userName))
        Text(text = stringResource(Res.string.items_count, count))
    }
}
```

### 4. Translation Editor Operations

- **Add String Resource**: Click the **Add string** toolbar button to define a new key with default and localized values simultaneously.
- **Edit Inline**: Double-click or type directly inside any cell to update the corresponding XML element.
- **Navigate to XML**: Double-click any cell or header to jump straight to the source `<string>` tag in the respective `strings.xml`.
- **Search & Filter**: Search across keys, default values, and translated strings, or filter rows by `All`, `Missing translations`, or `Complete`.
- **Delete Resource**: Select a row and click **Delete selected** to remove the key across all locale files at once.

---

## Migration Guide

### Target Audience

- Teams migrating from Android-only localization (`res/values/strings.xml`) to Compose Multiplatform (`composeResources/**/strings.xml`).
- Multiplatform development teams looking for an Android Studio Translations Editor equivalent for shared Compose resources.

### Comparison & Namespace Mapping

| Aspect | Android Native Localization | Compose Multiplatform `composeResources` |
| :--- | :--- | :--- |
| **Directory Location** | `src/main/res/values*/strings.xml` | `src/<sourceSet>/composeResources/values*/strings.xml` |
| **Access Syntax** | `R.string.key_name` / `stringResource(R.string.key_name)` | `Res.string.key_name` / `stringResource(Res.string.key_name)` |
| **Target Platforms** | Android only | Android, iOS, Desktop (JVM), Web (Wasm/JS) |
| **IDE Translation Editor** | Android Studio Translations Editor (Android only) | **Compose Multiplatform Translations** (Cross-platform) |
| **Code Generation** | Android Gradle Plugin (AAPT2) | Compose Multiplatform Gradle Plugin (`Res`) |
| **Resource Root** | Resource directory configured in `android.sourceSets` | `composeResources` inside Kotlin source sets |

### Key Migration Notes

- **Non-Destructive Integration**: This plugin works directly on standard XML files without modifying Gradle build logic or generated Kotlin code (`Res.string.*`).
- **Formatter & Comment Preservation**: PSI-based writes preserve surrounding formatting, attributes, and comments wherever possible.
- **File System Synchronization**: Automatically updates when files are modified externally, switched via Git branches, or generated by scripts.

---

## Platform Limitations & Constraints

- **Supported Tags**: Currently supports single-value `<string name="...">...</string>` tags. Multi-value `<plurals>` and `<string-array>` tags will be supported in upcoming releases.
- **Directory Conventions**: Files must reside under standard `composeResources/**/values*/strings.xml` directories to be indexed automatically.
- **Platform Qualifiers**: Qualifier naming follows Android / Compose Multiplatform conventions (e.g. `values`, `values-ko`, `values-en-rUS`, `values-b+sr+Latn`).

---

## License

```yaml
Copyright (c) 2026. Compose Multiplatform Translations project and open source contributors.
Copyright (c) 2026. IENGROUND of IENLAB.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

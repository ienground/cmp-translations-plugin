<p align="center">
  <img src="src/main/resources/META-INF/pluginIcon.png" width="150" alt="Compose Multiplatform Translations 로고" />
</p>

# Compose Multiplatform Translations

<p align="center">
  <a href="README.md">English</a> | <b>한국어</b>
</p>

<p align="center">
  <a href="https://plugins.jetbrains.com"><img src="https://img.shields.io/badge/JetBrains%20Marketplace-v0.2.0%20(beta)-blue?logo=jetbrains" alt="JetBrains Marketplace" /></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.1.0-blue?logo=kotlin" alt="Kotlin" /></a>
  <a href="https://www.jetbrains.com/idea/"><img src="https://img.shields.io/badge/IDE-IntelliJ%20IDEA%20%7C%20Android%20Studio-green?logo=intellijidea" alt="지원 IDE" /></a>
  <a href="https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html"><img src="https://img.shields.io/badge/Compatible%20Build-2024.2+-orange?logo=jetbrains" alt="호환 빌드" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="라이선스" /></a>
</p>

IntelliJ IDEA 및 Android Studio에서 Compose Multiplatform의 `composeResources` 문자열 리소스를 직관적으로 관리할 수 있는 IntelliJ Platform 플러그인입니다. 전용 번역 그리드, 실시간 유효성 검사, PSI 기반의 안전한 XML 편집을 제공합니다.

---

## 주요 기능 및 지원 매트릭스

- **Kotlin-First & Compose Multiplatform 네이티브 지원**: 모든 소스 세트(`commonMain`, `androidMain`, `iosMain` 등)의 공식 `composeResources` 디렉터리 구조를 자동으로 인식합니다.
- **통합 번역 테이블**: 기본 문자열과 다국어 번역(`values-ko`, `values-ja`, `values-es` 등)을 한눈에 비교하고 나란히 편집할 수 있는 직관적인 테이블 UI를 제공합니다.
- **실시간 유효성 검사 및 린팅**: 누락된 번역, 고아(orphan) 키, 중복 정의, printf 형식 placeholder(`%s`, `%d` 등) 불일치를 즉시 감지하여 표시합니다.
- **안전한 PSI 기반 XML 편집**: IntelliJ XML PSI와 `WriteCommandAction`을 사용하여 키 추가, 수정, 삭제, 이름 변경을 수행하며 실행 취소/다시 실행(Undo/Redo)을 완벽하게 지원합니다.
- **런타임 오버헤드 제로**: 순수 IDE 툴링으로 작동하며, 소스 XML 파일만 직접 수정하므로 프로젝트의 런타임 의존성이나 코드 생성 동작을 변경하지 않습니다.

### 지원 매트릭스

| 기능 | IntelliJ IDEA | Android Studio | 완성도 | 내부 동작 원리 |
| :--- | :---: | :---: | :---: | :--- |
| **문자열 리소스 편집 (`<string>`)** | ✅ 지원 | ✅ 지원 | 100% | IntelliJ XML PSI 및 `WriteCommandAction` |
| **다중 소스 세트 자동 탐색** | ✅ 지원 | ✅ 지원 | 100% | 가상 파일 시스템(VFS) 인덱서 |
| **누락 및 고아 키 검증** | ✅ 지원 | ✅ 지원 | 100% | 키 세트 차분 분석기 |
| **Placeholder 불일치 감지** | ✅ 지원 | ✅ 지원 | 100% | 정규식 기반 printf 토큰 검증기 (`%s`, `%d` 등) |
| **XML 태그 직접 이동** | ✅ 지원 | ✅ 지원 | 100% | PSI 요소 타깃 로케이터 (셀 더블 클릭) |
| **상태 필터 (`전체`, `누락`, `완료`)** | ✅ 지원 | ✅ 지원 | 100% | 동적 테이블 행 필터 모델 |
| **복수형 리소스 (`<plurals>`)** | 🟡 예정 | 🟡 예정 | 0% | 로드맵 예정 |
| **문자열 배열 리소스 (`<string-array>`)** | 🟡 예정 | 🟡 예정 | 0% | 로드맵 예정 |
| **AI 번역 프로바이더 연동** | 🟡 예정 | 🟡 예정 | 0% | 로드맵 예정 |

---

## 설치 방법

### JetBrains Marketplace

1. IDE(**IntelliJ IDEA** 또는 **Android Studio**)를 실행합니다.
2. **설정(Settings / Preferences)**(macOS: `⌘,`, Windows/Linux: `Ctrl+Alt+S`) > **Plugins**로 이동합니다.
3. **Marketplace** 탭에서 `Compose Multiplatform Translations`를 검색합니다.
4. **Install** 버튼을 클릭하고 안내에 따라 IDE를 재시작합니다.

> [!TIP]
> 베타 릴리스를 사용하려면 **Plugins > ⚙️ > Manage Plugin Repositories...**에서 베타 채널 저장소 `https://plugins.jetbrains.com/plugins/beta/list`를 추가하세요.

### ZIP 파일 수동 설치

1. [GitHub Releases](https://github.com/ienground/cmp-translations-plugin/releases)에서 최신 릴리스 `.zip` 파일을 다운로드합니다.
2. IDE의 **Settings / Preferences > Plugins**로 이동합니다.
3. 톱니바퀴 아이콘(⚙️)을 클릭하고 **Install Plugin from Disk...**를 선택합니다.
4. 다운로드한 ZIP 파일을 선택하고 IDE를 재시작합니다.

### Gradle 프로젝트 설정

Compose Multiplatform 프로젝트의 `build.gradle.kts`에 공식 Compose Resources 라이브러리가 포함되어 있는지 확인합니다:

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
> **최소 요구사항:**
> - **IDE**: IntelliJ IDEA 2024.2+ 또는 Android Studio Ladybug (2024.2+) / Meerkat (2024.3+)
> - **JDK**: Java 21 이상 (소스 빌드 및 개발 시)
> - **Compose Multiplatform**: 1.6.0 이상 (`composeResources` 지원 버전)

---

## 샘플 실행 및 시작하기

플러그인을 직접 실행하고 테스트하려면 다음 단계를 따릅니다:

1. 저장소를 클론합니다:
   ```bash
   git clone https://github.com/ienground/cmp-translations-plugin.git
   cd cmp-translations-plugin
   ```
2. 플러그인이 포함된 샌드박스 IDE 인스턴스를 실행합니다:
   ```bash
   ./gradlew runIde
   ```
3. 실행된 IDE 인스턴스에서 `composeResources`가 포함된 Compose Multiplatform 프로젝트를 엽니다.
4. 하단 또는 우측 사이드바에서 **Compose Translations** 툴 윈도우를 엽니다.
5. **Resource set** 드롭다운에서 대상 소스 세트(`commonMain` 등)를 선택하여 문자열을 관리합니다.

---

## 사용 예제

### 1. 디렉터리 구조

플러그인은 Compose Multiplatform 규격에 따라 구성된 문자열 파일을 자동으로 인식합니다:

```text
my-project/
└── src/
    └── commonMain/
        └── composeResources/
            ├── values/
            │   └── strings.xml        <-- 기본(fallback) 로케일
            ├── values-ko/
            │   └── strings.xml        <-- 한국어 로케일
            └── values-ja/
                └── strings.xml        <-- 일본어 로케일
```

### 2. XML 리소스 정의

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

### 3. Compose Multiplatform 코드에서 사용

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

### 4. 번역 편집기 기능 활용

- **문자열 리소스 추가**: 툴바의 **문자열 추가(Add string)** 버튼을 클릭하여 새 키와 기본값, 로케일별 번역을 한 번에 추가합니다.
- **인라인 셀 편집**: 테이블의 셀을 더블 클릭하거나 직접 입력하여 해당 XML 요소를 즉시 수정합니다.
- **XML 위치로 바로가기**: 셀이나 헤더를 더블 클릭하면 해당 `strings.xml` 파일 내의 `<string>` 태그 위치로 즉시 이동합니다.
- **검색 및 필터링**: 키, 기본값, 번역 문자열을 통합 검색하거나 `전체(All)`, `누락된 번역(Missing)`, `완료(Complete)` 필터로 번역 진행 상태를 확인합니다.
- **리소스 삭제**: 행을 선택하고 **선택 항목 삭제(Delete selected)**를 누르면 모든 로케일 파일에서 해당 키를 안전하게 일괄 삭제합니다.

---

## 마이그레이션 가이드

### 대상 사용자

- 기존 Android 전용 다국어 리소스(`res/values/strings.xml`)에서 Compose Multiplatform(`composeResources/**/strings.xml`)으로 전환하는 팀.
- 여러 플랫폼에서 공통 문자열 리소스를 효율적으로 관리하고자 하는 멀티플랫폼 개발팀.

### 네임스페이스 및 구조 비교

| 항목 | Android 네이티브 리소스 | Compose Multiplatform `composeResources` |
| :--- | :--- | :--- |
| **디렉터리 위치** | `src/main/res/values*/strings.xml` | `src/<sourceSet>/composeResources/values*/strings.xml` |
| **접근 방식** | `R.string.key_name` / `stringResource(R.string.key_name)` | `Res.string.key_name` / `stringResource(Res.string.key_name)` |
| **지원 플랫폼** | Android 전용 | Android, iOS, Desktop (JVM), Web (Wasm/JS) |
| **IDE 번역 편집기** | Android Studio Translations Editor (Android 전용) | **Compose Multiplatform Translations** (멀티플랫폼 지원) |
| **코드 생성기** | Android Gradle Plugin (AAPT2) | Compose Multiplatform Gradle Plugin (`Res`) |
| **리소스 루트** | `android.sourceSets`에 설정된 리소스 경로 | Kotlin 소스 세트 내 `composeResources` |

### 주요 마이그레이션 특징

- **비침습적 통합**: 표준 XML 파일만 수정하므로 Gradle 빌드 로직이나 생성된 Kotlin 코드(`Res.string.*`)에 영향을 주지 않습니다.
- **포맷 및 주석 보존**: PSI 기반 쓰기 동작을 통해 기존 XML의 들여쓰기, 속성, 주석을 최대한 보존합니다.
- **파일 시스템 실시간 동기화**: 외부 편집기 수정, Git 브랜치 전환 등으로 파일이 변경되어도 VFS 이벤트를 통해 자동으로 동기화됩니다.

---

## 플랫폼 제약 사항 및 제한

- **지원 태그**: 현재는 단일 문자열 `<string name="...">...</string>` 태그를 지원합니다. 다중값 `<plurals>` 및 `<string-array>` 태그는 후속 릴리스에서 지원될 예정입니다.
- **디렉터리 규칙**: 표준 `composeResources/**/values*/strings.xml` 경로에 위치한 파일만 자동으로 탐지 및 인덱싱됩니다.
- **로케일 Qualifier**: Android 및 Compose Multiplatform 표준 규칙(`values`, `values-ko`, `values-en-rUS`, `values-b+sr+Latn` 등)을 지원합니다.

---

## 라이선스

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

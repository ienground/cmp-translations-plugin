# Compose Multiplatform Translations

Compose Multiplatform의 `composeResources` 문자열 리소스를 IntelliJ IDEA와 Android Studio에서 관리하는 IntelliJ Platform 플러그인입니다.

## 주요 기능

- `composeResources/**/values*/strings.xml` 자동 탐색
- source set별 번역 테이블 제공
- key·기본 문자열·번역 문자열 검색
- `All`, `Missing`, `Complete` 상태 필터
- 누락·orphan·중복 key와 printf-style placeholder 불일치 검증
- XML PSI 기반 문자열 추가·수정·삭제·key 이름 변경
- 셀 더블 클릭을 통한 해당 XML 위치 이동
- 리소스 파일 변경 감지 및 새로고침

플러그인은 XML 리소스만 수정하며 Compose Multiplatform runtime이나 `stringResource(Res.string.*)` 사용 방식은 변경하지 않습니다.

자세한 사용법은 [compose-resources-editor.md](docs/compose-resources-editor.md)를 참고하세요.

## 설치

### JetBrains Marketplace

Marketplace에서 `Compose Multiplatform Translations`를 검색한 뒤 설치합니다.

### ZIP 파일

저장소의 [Releases](https://github.com/ienground/cmp-translations-plugin/releases)에서 ZIP 파일을 내려받아 IDE의 `Settings/Preferences > Plugins > ⚙️ > Install Plugin from Disk...`로 설치합니다.

## 개발 및 검증

Java 21과 Gradle Wrapper를 사용합니다.

```bash
./gradlew check
./gradlew buildPlugin
./gradlew verifyPlugin
```

개발용 IDE에서 플러그인을 실행하려면 다음 명령을 사용합니다.

```bash
./gradlew runIde
```

생성된 배포 파일은 `build/distributions/` 아래에 있습니다.

> [!NOTE]
> `build.gradle.kts`는 로컬에 설치된 Android Studio를 개발용 플랫폼으로 우선 사용할 수 있습니다. Marketplace 최초 업로드는 GitHub Actions의 Build workflow가 생성한 ZIP을 사용하는 것이 안전합니다. 로컬 IDE 버전에 따라 플러그인의 `since-build`가 달라질 수 있습니다.

## Marketplace 출시

GitHub Actions가 빌드·테스트·Plugin Verifier를 통과한 뒤 GitHub Release 초안을 만듭니다. 초안을 검토하고 `Publish release`를 누르면 `release.yml`이 해당 태그의 플러그인을 JetBrains Marketplace에 게시합니다.

### 최초 등록

최초 등록은 [JetBrains Marketplace의 Upload plugin](https://plugins.jetbrains.com/docs/marketplace/uploading-a-new-plugin.html)에서 한 번 수동으로 진행해야 합니다.

1. JetBrains Marketplace에서 Vendor 프로필을 만들고 Developer Agreement에 동의합니다.
2. `build/distributions/*.zip` 파일을 업로드합니다.
3. Apache 2.0 라이선스와 [소스 저장소](https://github.com/ienground/cmp-translations-plugin)를 등록합니다.
4. `beta` custom release channel을 선택합니다.
5. 플러그인 설명·태그·스크린샷·지원 링크를 입력하고 검토를 요청합니다.

플러그인 XML ID는 `zone.ien.cmp_translation_plugin`입니다. 첫 업로드 후 Marketplace에서 발급한 영구 토큰과 서명 정보를 GitHub 저장소의 `Settings > Secrets and variables > Actions`에 다음 이름으로 등록합니다.

| Secret | 용도 |
| --- | --- |
| `PUBLISH_TOKEN` | Marketplace 업로드 인증 |
| `PRIVATE_KEY` | 플러그인 서명 개인 키 |
| `PRIVATE_KEY_PASSWORD` | 개인 키 암호 |
| `CERTIFICATE_CHAIN` | 서명 인증서 체인 |

### 새 버전 출시

1. `gradle.properties`의 `version`을 올리고 `CHANGELOG.md`의 `[Unreleased]`에 변경 내용을 작성합니다.
2. 변경 사항을 `main`에 반영합니다.
3. Build workflow의 테스트·검증 결과를 확인합니다.
4. 생성된 GitHub Release 초안을 `Pre-release`로 게시합니다.
5. Release workflow가 서명 후 Marketplace에 업로드하는지 확인합니다.

Marketplace 서명 및 업로드 설정은 [Publishing a Plugin](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)과 [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)을 따릅니다.

현재 배포 채널은 `beta`입니다. Beta 플러그인을 설치하려면 IDE에 `https://plugins.jetbrains.com/plugins/beta/list`를 custom plugin repository로 추가해야 합니다.

## 라이선스

이 프로젝트는 [Apache License 2.0](LICENSE)으로 배포합니다.

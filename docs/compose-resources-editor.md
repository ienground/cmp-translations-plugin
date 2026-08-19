# Compose Multiplatform Translations Editor

## 개요

`Compose Multiplatform Translations`는 Compose Multiplatform의 `composeResources` 문자열 리소스를 IntelliJ IDEA와 Android Studio에서 관리하는 플러그인입니다. 플러그인을 제거해도 프로젝트의 Compose Multiplatform runtime이나 `stringResource(Res.string.*)` 사용에는 영향을 주지 않습니다.

## 지원 파일 구조

다음 패턴의 파일을 자동으로 탐색합니다.

```text
src/<source-set>/composeResources/values/strings.xml
src/<source-set>/composeResources/values-<qualifier>/strings.xml
```

`commonMain`을 우선 표시하며, 다른 source set도 탐지 결과에 포함됩니다. `values-ko`, `values-ja`, `values-zh-rCN`처럼 디렉터리 이름의 qualifier를 그대로 locale 열 이름으로 사용합니다.

## Translation Editor 사용법

1. 프로젝트를 열고 `Compose Translations` Tool Window를 엽니다.
2. `Resource set`에서 source set을 선택합니다.
3. 검색창은 key, 기본 문자열, 번역 문자열을 모두 검색합니다.
4. `All`, `Missing`, `Complete` 필터로 번역 상태를 좁힙니다.
5. 셀을 직접 편집하면 해당 `strings.xml`이 XML PSI와 `WriteCommandAction`을 통해 변경됩니다.
6. `Add string`은 기본 `values/strings.xml`에 key와 기본 문자열을 추가합니다.
7. 행을 선택하고 `Delete selected`를 누르면 모든 locale 파일에서 해당 key를 삭제합니다.
8. 셀을 두 번 클릭하면 해당 locale의 XML tag 위치로 이동합니다.

선택한 resource set의 `composeResources` 하위에서 `values*/strings.xml`이 추가·수정·삭제되면 테이블이 자동으로 갱신됩니다. `Refresh` 버튼은 수동으로 다시 읽고 싶을 때 사용할 수 있습니다.

누락 값은 `[Missing]`으로 표시됩니다. 누락·orphan·중복 key와 placeholder 불일치가 있는 행은 별도로 강조됩니다.

## 구현 구조

- `resource`: composeResources 탐지, qualifier 해석, XML PSI 파싱, resource set 모델
- `validation`: missing/orphan/duplicate key와 printf-style placeholder 비교
- `editor`: Tool Window, 테이블 모델, 검색·필터·navigation
- `write`: XML PSI와 `WriteCommandAction`을 사용하는 추가·수정·삭제

현재는 단일 Gradle 모듈을 사용하며, 기능 경계는 패키지 단위로 분리합니다. AI 번역 provider, plural, string-array, CSV 입출력은 core resource/editor 로직과 분리해 후속 기능으로 추가할 수 있습니다.

## 검증

```bash
./gradlew test
```

테스트는 XML PSI 파싱, composeResources 경로 탐지, locale/key 검증, 테이블 필터·검색, PSI 기반 쓰기 동작을 포함합니다.

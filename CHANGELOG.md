# Compose Multiplatform Translations Changelog

## [Unreleased]

### Changed

- Prepare the `1.0.0-beta` plugin release metadata
- Upgrade the Kotlin Gradle plugin to `2.4.20`

### Added

- Automatically discover `composeResources` string resources and provide translation tables for each source set
- Search translations and filter entries by `All`, `Missing`, or `Complete` status
- Validate missing, orphan, and duplicate keys as well as printf-style placeholder mismatches
- Add, edit, delete, and rename keys through XML PSI
- Navigate from a translation cell to its source XML location
- Detect resource changes and refresh the translation table manually

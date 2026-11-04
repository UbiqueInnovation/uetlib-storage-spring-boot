# CHANGELOG

## 3.0.0 (unreleased)

- Rename integration packages to `ch.ubique.uetlib.storage.spring.boot`; keep configuration keys unchanged.

### Breaking changes

- Move Java packages from `ch.ubique.backend` to `ch.ubique.uetlib`, including class-name resources. Existing Spring configuration property keys are unchanged.
- Remove the inherited Ubique Artifactory repository and deployment destination; private consumers must configure their own registry.
- Add Apache-2.0 licensing for original code, preserve third-party licenses, and add public project metadata and contribution/security guidance.

- Move Maven coordinates from `ch.ubique:uetlib-*` to `ch.uetlib:uetlib-*`.
- Align uetlib parent, BOM and library references with the migration releases.

### Migration

After the migration releases and `uetlib-rewrite:0.1.0` are published, run this from the consuming project, then review the diff and run its tests:

```sh
mvn org.openrewrite.maven:rewrite-maven-plugin:6.46.1:run \
  -Drewrite.recipeArtifactCoordinates=ch.uetlib:uetlib-rewrite:0.1.0 \
  -Drewrite.activeRecipes=ch.ubique.uetlib.MigrateToPublicUetlib
```

The recipe updates Maven coordinates/versions, Java references and known class-name resources. Review application-owned packages under the old namespace, Kotlin, reflection patterns, native-access module flags and persisted class names separately. Use `:dryRun` instead of `:run` to preview a patch.


## 2.3.0

- Add an opt-in storage HTTP proxy with a configurable path and shared client contract.
- Extract filesystem streaming and conditional responses from the Ridian/SchweizMobil backends,
  with root/symlink confinement and configurable media types.
- Add an S3/R2 proxy adapter using a single conditional GET, streamed bodies and HTTP metadata.

## 2.2.4
- update uetlib-storage `2.3.4` -> `2.3.5`
- latest uetlib parent
- latest uetlib testcontainers postgresql

## 2.2.3

- update uetlib-storage `2.3.2` -> `2.3.4`

## 2.2.2

- latest uetlib parent
- version updates

## 2.2.1

- updated actions to not run tests twice for sonar
- update uetlib version

## 2.2.0

### Improvements

- Java 26 Runtime for storage documentation

## 2.1.0

### Improvements

- Null-marked it all
- Updated `uetlib` dependecies to their newest versions

## 2.0.2

### Bugfixes

- Changed default for `Storage::documentationBaseUrl` to not create empty strings for the
  `StorageDocumentationController`

## 2.0.0

### Improvements

- Support for Spring Boot 4.0

## 1.2.2

### Bugfixes

- OutputMapping StorageObject type encountered a non-documentable generic subtype of `Output`:
  `java.util.Map<java.lang.Integer, java.lang.String>` and alike for generic types that are not subtypes of `Output`.

## 1.2.0

### Improvements

- Latest uetlib dependencies with Spring Boot 3.5 support

## 1.0.0

First release.

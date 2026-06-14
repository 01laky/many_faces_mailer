# Changelog

All notable changes to **`many_faces_mailer`** are documented here.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) — **version headings only, no dates**. SemVer: [`VERSION`](./VERSION).

### Release index

| Version       | Theme                                      |
| ------------- | ------------------------------------------ |
| [0.4.4](#044) | TLS-loader + SMTP-probe unit tests         |
| [0.4.3](#043) | Proto pointer sync                         |
| [0.4.2](#042) | Patch release index sync                   |
| [0.4.1](#041) | Patch                                      |
| [0.4.0](#040) | SMTP override and TestSmtpConnection       |
| [0.3.0](#030) | many_faces_proto and registration template |
| [0.2.0](#020) | gRPC mailer and Mailpit dev                |
| [0.1.0](#010) | Worker skeleton                            |

## [Unreleased]

### Added

### Changed

### Fixed

---

## [0.4.4]

### Added

- Unit tests for two previously-untested classes (unit-test-gap-fill): `ServerTlsLoader` (blank cert+key paths → cleartext `Optional.empty()`; a half-configured cert-or-key pair throws `IllegalArgumentException`; missing/invalid PEM material is rejected) and `SmtpConnectionProbe.sessionProperties` (host/port + connection/read/write timeouts mapped through, `mail.smtp.auth` toggled by whether an SMTP user is set, STARTTLS flag propagated). `MailerWorkerMain` is a process entry point and is left to integration coverage.

---

## [0.4.3]

### Changed

- Align the `many_faces_proto` submodule pointer to `d03301c` (latest), matching `many_faces_backend`/`many_faces_ai` for monorepo-wide proto consistency. The bump only adds the additive `health.proto` `GenerateRequest` fields (per-call temperature/stop/model); the `manyfaces.mailer.v1` contract and mailer-worker code are unchanged.

---

## [0.4.2]

### Added

- Add README shield badges (version, CI, stack tech) via sync-readme-badges.py.

---

## [0.4.1]

### Changed

- Document project author (Ladislav Kostolny, 01laky@gmail.com) in README and standard manifests.

---

## [0.4.0]

### Added

- Per-request SMTP override and TestSmtpConnection RPC; verify-edge-contracts; lint.sh.

### Changed

- gRPC and protobuf-java bumps; pinned many_faces_proto.

## [0.3.0]

### Added

- Resolve .proto from many_faces_proto; account_registration_code email template.

### Fixed

- Blank recipient rejection; vendored health.proto for grpcurl.

## [0.2.0]

### Added

- gRPC mailer worker with SMTP, Pebble templates, Mailpit dev; TLS/mTLS smoke compose.

### Fixed

- Docker build on eclipse-temurin; TLS smoke grpcurl permissions.

## [0.1.0]

### Added

- many_faces_mailer skeleton with README, compose, and CI.

[Unreleased]: https://github.com/01laky/many_faces_mailer/compare/v0.4.4...HEAD
[0.4.4]: https://github.com/01laky/many_faces_mailer/compare/v0.4.3...v0.4.4
[0.4.3]: https://github.com/01laky/many_faces_mailer/compare/v0.4.2...v0.4.3
[0.4.2]: https://github.com/01laky/many_faces_mailer/compare/v0.4.1...v0.4.2
[0.4.1]: https://github.com/01laky/many_faces_mailer/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/01laky/many_faces_mailer/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/01laky/many_faces_mailer/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/01laky/many_faces_mailer/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/01laky/many_faces_mailer/releases/tag/v0.1.0

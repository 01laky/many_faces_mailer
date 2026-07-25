# Changelog

All notable changes to **`many_faces_mailer`** are documented here.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) — **version headings only, no dates**. SemVer: [`VERSION`](./VERSION).

### Release index

| Version       | Theme                                      |
| ------------- | ------------------------------------------ |
| [0.4.5](#045) | Recipient domain policy documented (MAIL-5) |
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

## [0.4.5]

### Added

- **Documented the recipient domain policy gap (SHV2 MAIL-5).** The README's security notes now record that the worker sends to whatever recipient it is given: `MailerServiceImpl` and `SmtpMailSender` validate message shape (required template params, address parseability, `transport.validateForSend()`) but never the recipient's domain, so anyone who can reach the gRPC port with the shared token can have a Many-Faces-branded mail delivered from the configured `From` address to any mailbox. That is acceptable only because the port is internal and `many_faces_backend` — the sole caller — derives recipients from its own database rather than from client input. Added a decision table for production (domain allow-list for internal/staff faces; volume and per-recipient rate caps plus aligned DKIM/SPF/DMARC for a public product; never expose the port either way) and a note on where such a check would belong if moved into the worker (alongside `transport.validateForSend()`, failing with `INVALID_ARGUMENT` rather than filtering silently, so the backend never records a send that did not happen).

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

[Unreleased]: https://github.com/01laky/many_faces_mailer/compare/v0.4.5...HEAD
[0.4.5]: https://github.com/01laky/many_faces_mailer/compare/v0.4.4...v0.4.5
[0.4.4]: https://github.com/01laky/many_faces_mailer/compare/v0.4.3...v0.4.4
[0.4.3]: https://github.com/01laky/many_faces_mailer/compare/v0.4.2...v0.4.3
[0.4.2]: https://github.com/01laky/many_faces_mailer/compare/v0.4.1...v0.4.2
[0.4.1]: https://github.com/01laky/many_faces_mailer/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/01laky/many_faces_mailer/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/01laky/many_faces_mailer/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/01laky/many_faces_mailer/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/01laky/many_faces_mailer/releases/tag/v0.1.0

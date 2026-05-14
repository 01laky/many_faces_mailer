# many_faces_mailer

Standalone **Java gRPC mailer worker** (SMTP, templated email, UTF-8 i18n) for Many Faces.  
Linked as a **git submodule** from `many_faces_main` at `many_faces_mailer/`.

**Toolchain:** **Java 21** (Gradle toolchain + Foojay resolver for reproducible CI). **No Spring** — plain `main`, gRPC-Netty, Angus Mail, Pebble.

## Architecture

1. **`many_faces_backend`** decides policy (who receives which template) and calls **`SendTemplatedEmail`** over gRPC.
2. This worker **renders** HTML + plain text from **`src/main/resources/templates/`** and subject lines from **`src/main/resources/i18n/`**.
3. **SMTP** delivers to Mailpit (dev) or a transactional relay (prod).

## Template catalog (v1)

| `template_id` | Required `params` | Supported locales (bundles) |
| ------------- | ----------------- | --------------------------- |
| `identity_email_confirm` | `action_link`, `user_name` | `en`, `sk` |
| `identity_password_reset` | `action_link`, `user_name` | `en`, `sk` |

## Ports

| Component | Internal gRPC | Default host map |
| --------- | ------------- | ---------------- |
| **mailer-worker** | **50054** | **59204** (`MAILER_WORKER_GRPC_HOST_PORT`) |
| **mailpit** SMTP | **1025** | **51025** (`MAILPIT_SMTP_HOST_PORT`) |
| **mailpit** UI | **8025** | **58025** (`MAILPIT_UI_HOST_PORT`) |

## Quick start (Docker)

```bash
./scripts/start-mailer-worker.sh
```

Stop:

```bash
./scripts/stop-mailer-worker.sh
```

## TLS / mTLS smoke (CI + local)

- **`docker-compose.tls-smoke.yml`** — isolated worker with server TLS + mTLS (PEMs under **`MAILER_TLS_SMOKE_CERT_DIR`**). Default published gRPC: **`127.0.0.1:59216`** (does not collide with push TLS smoke **59215**).
- **`scripts/smoke-grpc-tls.sh`** — generates a throwaway CA + certs, runs **`grpcurl`** `Health/Check`, then optional **`dotnet test`** filtered to **`MailerWorkerTlsEndToEndSmokeTests`** (`MAILER_TLS_SMOKE=1`). Docker Compose project: **`mf-mailer-tls-smoke`** (same name as `clear-all-dev.sh`).

Monorepo guide: **`docs/guides/mailer-grpc-tls-mtls.md`**.

## C# client stubs (many_faces_backend)

The canonical contract is **`proto/manyfaces/mailer/v1/mailer.proto`**. The backend references this path from `BeDemo.Api.csproj` (`Protobuf` item with `GrpcServices="Client"`) so `dotnet build` regenerates `ManyFaces.Mailer.V1` types — same policy as push/search.

## Environment

See **`.env.example`**. For monorepo wiring (Mailpit, `Mail:*`, grpcurl), read **`docs/guides/mailer-local-dev.md`** in the parent repository.

## License

See the root monorepo or team policy (demo stack).

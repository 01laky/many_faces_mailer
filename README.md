# many_faces_mailer

Standalone **Java gRPC mailer worker** (SMTP, templated email) for Many Faces.  
This repository is linked as a **git submodule** from [`many_faces_main`](https://github.com/01laky/many_faces_main) at `many_faces_mailer/`.

**Toolchain:** The skeleton build targets **Java 17** for local and CI compatibility. The product prompt targets **Java 21+** for the full worker — bump `build.gradle.kts` when the implementation lands.

**Status:** Skeleton only — Gradle, Docker Compose, scripts, and a placeholder container. No gRPC server yet (see monorepo `docs/prompts/smtp-mailer-java-grpc-worker-agent-prompt.md`).

## Ports (reserved)

| Component | Internal gRPC | Default host map |
| --------- | ------------- | ---------------- |
| **mailer-worker** | **50054** | **59204** |

## Quick start (placeholder container)

From this directory:

```bash
./scripts/start-mailer-worker.sh
```

Stop:

```bash
./scripts/stop-mailer-worker.sh
```

## Environment

See `.env.example`. No secrets are committed.

## License

See the root monorepo or team policy (demo stack).

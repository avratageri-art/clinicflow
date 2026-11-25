# Security Policy

## Supported Versions

| Version | Supported |
| :--- | :--- |
| 1.0.x | Yes |

## Security Practices in ClinicFlow

- **Local-First Architecture**: ClinicFlow runs as a local Java Swing desktop application with local CSV persistence (`data/app/*.csv`). No external network endpoints or cloud credentials are required.
- **Configuration & Secrets**: Environment overrides are read from `.env` or OS environment variables (see `.env.example`). Never commit `.env` or any real patient PII to version control.
- **Input Validation**: All patient, doctor, and appointment inputs are validated via `DataValidator` and domain service checks before persistence or ML inference.
- **Atomic Persistence**: CSV repositories write to temporary files and perform atomic moves (`StandardCopyOption.REPLACE_EXISTING` / `ATOMIC_MOVE` fallback) to prevent data corruption.
- **Clinical Disclaimer**: ClinicFlow is a decision-support and educational/research tool trained on the public 2016 Vitória, Brazil SUS dataset. It must not be used as a sole clinical decision-maker without local validation and regulatory compliance (e.g., HIPAA, GDPR, or local health data regulations).

## Reporting a Vulnerability

If you discover a security vulnerability, please **do not** open a public issue. Instead, report it privately to the repository maintainers via GitHub Security Advisories or email, including steps to reproduce and potential impact.

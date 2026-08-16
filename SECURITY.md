# Security Notice

This repository is a privacy-scrubbed portfolio snapshot of a first full-stack project. It is not a production-ready deployment template.

## Data policy

- Never commit real participant data, payment proofs, database exports, credentials, tokens, invite codes, or server identifiers.
- Use only synthetic data in screenshots, tests, demonstrations, and issues.
- Keep runtime secrets in environment variables or a dedicated secret manager.
- Run `python scripts/repository_check.py` before every commit.

## Known architectural limitations

- Authorization is still implemented mainly inside controllers rather than as a centralized Spring Security policy.
- Automated coverage is currently limited to an application-context smoke test.
- Database migrations and a reproducible production deployment are not included.
- External CDN assets and the original monolithic pages require further supply-chain and maintainability review.

Do not deploy this snapshot to accept real registrations without a complete security review, authorization tests, migration plan, monitoring, backups, and incident response process.

## Historical credential warning

The source package used to reconstruct this repository contained database and JWT secrets. They are not present in this Git history. If those values were ever used outside local development, they must be rotated in the original environment.

## Reporting

Because the repository is initially private, report suspected security or privacy issues through a private GitHub issue or directly to the repository owner. Do not include real participant data in the report.

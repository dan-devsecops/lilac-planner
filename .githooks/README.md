# Git hooks

Not enabled by default - git doesn't auto-discover a tracked hooks directory,
you opt in once per clone:

```bash
git config core.hooksPath .githooks
```

## `pre-commit`

Blocks committing any `vault.yml` (ansible-vault secrets - see
`deploy/ansible/group_vars/droplet/vault.yml`) that isn't actually
vault-encrypted yet. Catches "forgot to run `ansible-vault encrypt`" at
commit time instead of after it's permanently in git history.

## `post-commit`

Runs `../sonar-scan.sh` in the background after each commit, scanning only the
component(s) (`backend/`, `frontend/`, `mobile/`) touched by that commit, against the
local SonarQube instance (`docker-compose-sonarqube.yml`). No-op - doesn't even
start SonarQube or fail your commit - unless `SONAR_TOKEN` is set (directly, or
via `.env.local`, see below) and that instance is actually running, so it's
safe to enable even if you haven't set SonarQube up locally. Output goes to
`.git/sonar-scan.log`, not the terminal.

To opt in without a one-off `export` that won't survive a reboot:

```bash
cp .env.local.example .env.local   # fill in SONAR_TOKEN
```
`sonar-scan.sh` reads `.env.local` itself on every run (no shell profile edit,
no extra tool). `.env.local` is gitignored - the token never gets committed.

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

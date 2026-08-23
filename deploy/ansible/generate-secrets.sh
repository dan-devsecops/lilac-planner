#!/usr/bin/env bash
# Fills the CHANGE_ME password placeholders in group_vars/droplet/vault.yml with fresh `openssl rand -base64 24` values, it's safe to re-run

set -euo pipefail
cd "$(dirname "$0")"

FILE=group_vars/droplet/vault.yml
KEYS=(planner_jwt_secret planner_admin_password mariadb_password mariadb_root_password planner_metrics_password grafana_admin_password)

if [ ! -f "$FILE" ]; then
  echo "$FILE not found - run: cp group_vars/droplet/vault.yml.example $FILE" >&2
  exit 1
fi

if head -n1 "$FILE" | grep -q '^\$ANSIBLE_VAULT'; then
  echo "$FILE is vault-encrypted - run 'ansible-vault decrypt $FILE' first, then re-run this." >&2
  exit 1
fi

for key in "${KEYS[@]}"; do
  bytes=24
  [ "$key" = planner_jwt_secret ] && bytes=32
  python3 - "$FILE" "$key" "$(openssl rand -base64 "$bytes")" <<'PY'
import sys
path, key, new_value = sys.argv[1:4]
prefix = f"{key}: "
with open(path) as f:
    lines = f.readlines()
for i, line in enumerate(lines):
    if line.startswith(prefix):
        rest = line[len(prefix):]
        value_part, _, comment_part = rest.partition("#")
        stripped = value_part.strip()
        quote = ""
        if len(stripped) >= 2 and stripped[0] == stripped[-1] and stripped[0] in "\"'":
            quote = stripped[0]
            stripped = stripped[1:-1]
        if stripped != "CHANGE_ME":
            print(f"  {key}: already set, skipping")
            break
        new_line = f"{prefix}{quote}{new_value}{quote}"
        if comment_part:
            new_line += f"   #{comment_part.rstrip()}"
        lines[i] = new_line + "\n"
        with open(path, "w") as f:
            f.writelines(lines)
        print(f"  {key}: generated")
        break
else:
    sys.exit(f"key '{key}' not found in {path}")
PY
done

echo "Done. Review $FILE, then: ansible-vault encrypt $FILE"

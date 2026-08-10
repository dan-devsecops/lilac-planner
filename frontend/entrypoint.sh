#!/bin/sh
set -eu

# Generate runtime config consumed by the SPA.
# AUTH_PROVIDER (keycloak|native|none) is the canonical switch. For backward
# compatibility, if AUTH_PROVIDER is unset we fall back to the legacy AUTH_ENABLED
# boolean: false -> none, otherwise -> keycloak.
if [ -z "${AUTH_PROVIDER:-}" ]; then
  if [ "${AUTH_ENABLED:-false}" = "false" ]; then
    AUTH_PROVIDER=none
  else
    AUTH_PROVIDER=keycloak
  fi
fi

cat > /usr/share/nginx/html/config.js << EOF
window.__LILAC_CONFIG__ = {
  authProvider: "${AUTH_PROVIDER}"
};
EOF

exec /docker-entrypoint.sh "$@"

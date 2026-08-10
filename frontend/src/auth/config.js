// Runtime auth configuration, read from window.__LILAC_CONFIG__ (public/config.js,
// regenerated at container start by entrypoint.sh).
//
// New switch: authProvider = 'keycloak' | 'native' | 'none'.
// Backward compatible with the old boolean: authEnabled === false ⇒ 'none', else 'keycloak'.
const cfg = (typeof window !== 'undefined' && window.__LILAC_CONFIG__) || {};

export const authProvider =
  cfg.authProvider || (cfg.authEnabled === false ? 'none' : 'keycloak');

export const isKeycloak = authProvider === 'keycloak';
export const isNative = authProvider === 'native';
export const isNone = authProvider === 'none';

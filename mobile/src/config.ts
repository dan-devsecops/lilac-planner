import { Platform } from 'react-native';

/**
 * API base URL, configurable per build profile (see eas.json / .env). The web app proxies
 * `/api` in dev and is served same-origin in prod; the mobile app has no such proxy, so it
 * always talks to an absolute origin - Expo inlines `EXPO_PUBLIC_*` env vars at build time.
 *
 * Dev default is platform-aware: the Android emulator only reaches the host machine via the
 * `10.0.2.2` alias, while the iOS Simulator shares the host's network stack directly, so
 * `10.0.2.2` isn't even a routable address there - see the timeout in app/_layout.tsx for
 * what happens when this is misconfigured. Override via EXPO_PUBLIC_API_BASE_URL for a
 * physical device on the same LAN (http://<LAN-IP>:8090) or a production build (https://<DOMAIN>).
 */
export const API_ORIGIN =
  process.env.EXPO_PUBLIC_API_BASE_URL || (Platform.OS === 'android' ? 'http://10.0.2.2:8090' : 'http://localhost:8090');

export const API_BASE = `${API_ORIGIN}/api/v1`;

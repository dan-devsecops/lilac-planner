/** Simple dotted-numeric version compare (e.g. "1.2.0" vs "1.10.0") - no pre-release/build
 *  metadata support, which is all app.json's plain "version" field ever needs. */
export function isBelowMinVersion(appVersion: string, minVersion: string): boolean {
  const a = appVersion.split('.').map(Number);
  const b = minVersion.split('.').map(Number);
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const av = a[i] ?? 0;
    const bv = b[i] ?? 0;
    if (av !== bv) return av < bv;
  }
  return false;
}

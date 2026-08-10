// Minimal base64url JWT payload decode for display purposes only (name/roles) - no signature
// verification, mirroring the web app's tokenPayload(). React Native/Hermes has no built-in
// atob, hence the small table-driven decoder below.

const CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

function base64Decode(input: string): string {
  const clean = input.replace(/[^A-Za-z0-9+/]/g, '');
  let output = '';
  for (let i = 0; i < clean.length; i += 4) {
    const enc1 = CHARS.indexOf(clean[i]);
    const enc2 = CHARS.indexOf(clean[i + 1]);
    const enc3 = CHARS.indexOf(clean[i + 2]);
    const enc4 = CHARS.indexOf(clean[i + 3]);
    const chr1 = (enc1 << 2) | (enc2 >> 4);
    const chr2 = ((enc2 & 15) << 4) | (enc3 >> 2);
    const chr3 = ((enc3 & 3) << 6) | enc4;
    output += String.fromCharCode(chr1);
    if (enc3 !== -1) output += String.fromCharCode(chr2);
    if (enc4 !== -1) output += String.fromCharCode(chr3);
  }
  return output;
}

export interface JwtPayload {
  sub?: string;
  preferred_username?: string;
  name?: string;
  roles?: string[];
  exp?: number;
  [key: string]: unknown;
}

export function decodeJwtPayload(token: string): JwtPayload | null {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      base64Decode(payload)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join('')
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

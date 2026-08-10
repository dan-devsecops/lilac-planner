import { Link } from 'react-router-dom';
import keycloak from '../keycloak.js';
import { isKeycloak, isNative } from '../auth/config.js';
import * as nativeAuth from '../auth/nativeAuth.js';

export default function UserInfo() {
  if (isNative) {
    return (
      <div className="user-info">
        <Link className="user-info-name" to="/account">👤 {nativeAuth.displayName()}</Link>
        <button
          className="ghost"
          onClick={async () => { await nativeAuth.logout(); window.location.assign('/login'); }}
        >
          Log out
        </button>
      </div>
    );
  }

  if (isKeycloak) {
    const displayName = keycloak.tokenParsed?.name
      || keycloak.tokenParsed?.preferred_username
      || 'User';
    return (
      <div className="user-info">
        <Link className="user-info-name" to="/account">👤 {displayName}</Link>
        <button
          className="ghost"
          onClick={() => keycloak.logout({ redirectUri: window.location.origin })}
        >
          Log out
        </button>
      </div>
    );
  }

  // none (dev) - no login/logout, but timezone/device settings still apply
  return (
    <div className="user-info">
      <Link className="user-info-name" to="/account">👤 Dev User</Link>
    </div>
  );
}

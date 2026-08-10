import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import * as nativeAuth from './nativeAuth';

interface AuthContextValue {
  /** undefined while the initial SecureStore read is in flight. */
  isAuthenticated: boolean | undefined;
  isAdmin: boolean;
  displayName: string;
}

const initialValue: AuthContextValue = {
  isAuthenticated: undefined,
  isAdmin: false,
  displayName: '',
};

const AuthContext = createContext<AuthContextValue>(initialValue);

function readAuthState(): AuthContextValue {
  const authed = nativeAuth.isAuthenticated();
  return {
    isAuthenticated: authed,
    isAdmin: authed ? nativeAuth.isAdmin() : false,
    displayName: authed ? nativeAuth.displayName() : '',
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [value, setValue] = useState<AuthContextValue>(initialValue);

  useEffect(() => {
    let cancelled = false;
    nativeAuth.init().then(() => {
      if (!cancelled) setValue(readAuthState());
    });
    // Re-derive on every token change, not just login/logout - a silent background refresh
    // rotates the access token (and can change its claims) without isAuthenticated ever
    // flipping, so displayName/isAdmin must be re-read from the new token each time too.
    const unsubscribe = nativeAuth.subscribe(() => setValue(readAuthState()));
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, []);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}

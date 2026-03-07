import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { loginApi, meApi, registerApi } from "@/features/auth/api/authApi";

const STORAGE_KEY = "loan_dss_auth";
const AuthContext = createContext(null);

function readStoredAuth() {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => readStoredAuth());
  const [isInitializing, setIsInitializing] = useState(true);

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      const stored = readStoredAuth();
      if (!stored?.accessToken) {
        if (active) {
          setIsInitializing(false);
        }
        return;
      }

      try {
        const user = await meApi(stored.accessToken);
        if (!active) {
          return;
        }
        const nextSession = {
          accessToken: stored.accessToken,
          user
        };
        localStorage.setItem(STORAGE_KEY, JSON.stringify(nextSession));
        setSession(nextSession);
      } catch {
        if (!active) {
          return;
        }
        localStorage.removeItem(STORAGE_KEY);
        setSession(null);
      } finally {
        if (active) {
          setIsInitializing(false);
        }
      }
    }

    bootstrap();
    return () => {
      active = false;
    };
  }, []);

  const login = async ({ email, password }) => {
    const payload = await loginApi({ email, password });
    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
    setSession(payload);
    return payload.user;
  };

  const register = async ({ email, password, role }) => {
    const payload = await registerApi({ email, password, role });
    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
    setSession(payload);
    return payload.user;
  };

  const logout = () => {
    localStorage.removeItem(STORAGE_KEY);
    setSession(null);
  };

  const value = useMemo(
    () => ({
      session,
      user: session?.user ?? null,
      isAuthenticated: Boolean(session?.accessToken),
      accessToken: session?.accessToken ?? null,
      isInitializing,
      login,
      register,
      logout
    }),
    [session, isInitializing]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth phải được sử dụng bên trong AuthProvider");
  }
  return ctx;
}

import React, { createContext, useContext, useMemo, useState } from "react";
import { setApiAccessToken } from "../services/api";
import { ToastMessage, UserProfile } from "../types";

type Role = "USER" | "ADMIN" | "DEVELOPER";

interface AppContextValue {
  profile: UserProfile | null;
  role: Role;
  authReady: boolean;
  accessToken: string | null;
  refreshToken: string | null;
  pendingAuthTxId: string | null;
  setPendingAuthTxId: (value: string | null) => void;
  setProfile: (profile: UserProfile | null) => void;
  setRole: (role: Role) => void;
  setAuthReady: (ready: boolean) => void;
  setTokens: (accessToken: string | null, refreshToken: string | null) => void;
  toasts: ToastMessage[];
  addToast: (type: ToastMessage["type"], message: string) => void;
  dismissToast: (id: string) => void;
}

const AppContext = createContext<AppContextValue | undefined>(undefined);

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [role, setRole] = useState<Role>("USER");
  const [authReady, setAuthReady] = useState(false);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [refreshToken, setRefreshToken] = useState<string | null>(null);
  const [pendingAuthTxId, setPendingAuthTxId] = useState<string | null>(null);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const addToast = (type: ToastMessage["type"], message: string) => {
    const id = `${Date.now()}_${Math.random().toString(16).slice(2, 8)}`;
    setToasts((prev) => [...prev, { id, type, message }]);
    window.setTimeout(() => {
      setToasts((prev) => prev.filter((toast) => toast.id !== id));
    }, 3600);
  };

  const dismissToast = (id: string) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  };

  const setTokens = (nextAccessToken: string | null, nextRefreshToken: string | null) => {
    setAccessToken(nextAccessToken);
    setRefreshToken(nextRefreshToken);
    setApiAccessToken(nextAccessToken);
  };

  const value = useMemo(
    () => ({
      profile,
      role,
      authReady,
      accessToken,
      refreshToken,
      pendingAuthTxId,
      setPendingAuthTxId,
      setProfile,
      setRole,
      setAuthReady,
      setTokens,
      toasts,
      addToast,
      dismissToast,
    }),
    [profile, role, authReady, accessToken, refreshToken, pendingAuthTxId, toasts],
  );

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useAppContext(): AppContextValue {
  const ctx = useContext(AppContext);
  if (!ctx) {
    throw new Error("useAppContext must be used within AppProvider");
  }
  return ctx;
}

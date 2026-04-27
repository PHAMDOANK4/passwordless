import React from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAppContext } from "../context/AppContext";

type Role = "USER" | "ADMIN" | "DEVELOPER";

interface RouteGuardProps {
  requireAuth?: boolean;
  allowedRoles?: Role[];
  children: React.ReactElement;
}

export function RouteGuard({ requireAuth = false, allowedRoles, children }: RouteGuardProps) {
  const location = useLocation();
  const { accessToken, profile, role, authReady } = useAppContext();
  const isAuthenticated = Boolean(accessToken || profile?.email);

  if (requireAuth && !authReady) {
    return <div className="guard-loading">Đang khôi phục phiên đăng nhập...</div>;
  }

  if (requireAuth && !isAuthenticated) {
    return <Navigate to="/error/401" replace state={{ from: location.pathname }} />;
  }

  if (allowedRoles && !allowedRoles.includes(role as Role)) {
    return <Navigate to="/error/403" replace state={{ from: location.pathname }} />;
  }

  return children;
}

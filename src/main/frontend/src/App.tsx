import React, { useEffect } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { RouteGuard } from "./components/RouteGuard";
import { ToastHost } from "./components/ToastHost";
import { useAppContext } from "./context/AppContext";
import { api } from "./services/api";
import { AdminApiKeysPage } from "./pages/admin/AdminApiKeysPage";
import { AdminAuditPage } from "./pages/admin/AdminAuditPage";
import { AdminClientsPage } from "./pages/admin/AdminClientsPage";
import { AdminDashboardPage } from "./pages/admin/AdminDashboardPage";
import { AdminDomainsPage } from "./pages/admin/AdminDomainsPage";
import { AdminSystemPage } from "./pages/admin/AdminSystemPage";
import { AdminUsersPage } from "./pages/admin/AdminUsersPage";
import { AuthErrorPage } from "./pages/AuthErrorPage";
import { DeveloperRegisterClientPage } from "./pages/developer/DeveloperRegisterClientPage";
import { DeveloperSwaggerPage } from "./pages/developer/DeveloperSwaggerPage";
import { TokenInspectorPage } from "./pages/developer/TokenInspectorPage";
import { LoginPage } from "./pages/end-user/LoginPage";
import { LogoutPage } from "./pages/end-user/LogoutPage";
import { MfaChallengePage } from "./pages/end-user/MfaChallengePage";
import { ProfilePage } from "./pages/end-user/ProfilePage";
import { OAuthConsentPage } from "./pages/oauth/OAuthConsentPage";
import { OAuthErrorPage } from "./pages/oauth/OAuthErrorPage";

export default function App() {
  const { accessToken, profile, setProfile, setRole, setAuthReady } = useAppContext();

  useEffect(() => {
    let active = true;

    const bootstrap = async () => {
      if (accessToken || profile?.email) {
        setAuthReady(true);
        return;
      }

      try {
        const me = await api.getProfile();
        if (!active) {
          return;
        }

        setProfile(me);
        if (me.role === "ADMIN") {
          setRole("ADMIN");
        } else if (me.role === "DEVELOPER") {
          setRole("DEVELOPER");
        } else {
          setRole("USER");
        }
      } catch {
        if (!active) {
          return;
        }
        setProfile(null);
        setRole("USER");
      } finally {
        if (active) {
          setAuthReady(true);
        }
      }
    };

    void bootstrap();
    return () => {
      active = false;
    };
  }, [accessToken, profile?.email, setAuthReady, setProfile, setRole]);

  return (
    <>
      <Routes>
        <Route path="/" element={<Navigate to="/idp" replace />} />

        <Route path="/idp" element={<LoginPage />} />
        <Route path="/mfa" element={<MfaChallengePage />} />
        <Route
          path="/profile"
          element={
            <RouteGuard requireAuth>
              <ProfilePage />
            </RouteGuard>
          }
        />
        <Route
          path="/logout"
          element={
            <RouteGuard requireAuth>
              <LogoutPage />
            </RouteGuard>
          }
        />

        <Route
          path="/admin/dashboard"
          element={
            <RouteGuard requireAuth allowedRoles={["ADMIN"]}>
              <AdminDashboardPage />
            </RouteGuard>
          }
        />
        <Route
          path="/admin/users"
          element={
            <RouteGuard requireAuth allowedRoles={["ADMIN"]}>
              <AdminUsersPage />
            </RouteGuard>
          }
        />
        <Route
          path="/admin/clients"
          element={
            <RouteGuard requireAuth allowedRoles={["ADMIN"]}>
              <AdminClientsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/admin/domains"
          element={
            <RouteGuard requireAuth allowedRoles={["ADMIN"]}>
              <AdminDomainsPage />
            </RouteGuard>
          }
        />
        <Route
          path="/admin/system"
          element={
            <RouteGuard requireAuth allowedRoles={["ADMIN"]}>
              <AdminSystemPage />
            </RouteGuard>
          }
        />
        <Route
          path="/admin/audit"
          element={
            <RouteGuard requireAuth allowedRoles={["ADMIN"]}>
              <AdminAuditPage />
            </RouteGuard>
          }
        />
        <Route
          path="/admin/api-keys"
          element={
            <RouteGuard requireAuth allowedRoles={["ADMIN"]}>
              <AdminApiKeysPage />
            </RouteGuard>
          }
        />

        <Route
          path="/developer/register-client"
          element={
            <RouteGuard requireAuth allowedRoles={["DEVELOPER", "ADMIN"]}>
              <DeveloperRegisterClientPage />
            </RouteGuard>
          }
        />
        <Route
          path="/developer/swagger"
          element={
            <RouteGuard requireAuth allowedRoles={["DEVELOPER", "ADMIN"]}>
              <DeveloperSwaggerPage />
            </RouteGuard>
          }
        />
        <Route
          path="/developer/token-inspector"
          element={
            <RouteGuard requireAuth allowedRoles={["DEVELOPER", "ADMIN"]}>
              <TokenInspectorPage />
            </RouteGuard>
          }
        />

        <Route path="/oauth/consent" element={<OAuthConsentPage />} />
        <Route path="/oauth/error" element={<OAuthErrorPage />} />
        <Route path="/error/401" element={<AuthErrorPage code={401} />} />
        <Route path="/error/403" element={<AuthErrorPage code={403} />} />

        <Route path="*" element={<Navigate to="/idp" replace />} />
      </Routes>
      <ToastHost />
    </>
  );
}

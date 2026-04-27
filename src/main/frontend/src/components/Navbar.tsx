import React from "react";
import { NavLink } from "react-router-dom";
import { useAppContext } from "../context/AppContext";

export function Navbar() {
  const { role, profile } = useAppContext();
  const allowRoleSwitch = (process.env.IDP_ALLOW_ROLE_SWITCH as string | undefined) === "true";

  return (
    <header className="topbar">
      <div className="brand-block">
        <span className="brand-dot" />
        <div>
          <p className="brand-title">Passwordless IdP</p>
          <p className="brand-subtitle">OAuth2 / OIDC Control Plane</p>
        </div>
      </div>

      <nav className="topbar-nav">
        <NavLink to="/idp" className="pill-link">IdP Login</NavLink>
        <NavLink to="/profile" className="pill-link">Profile</NavLink>
        <NavLink to="/admin/dashboard" className="pill-link">Admin</NavLink>
        <NavLink to="/developer/register-client" className="pill-link">Developer</NavLink>
      </nav>

      <div className="topbar-right">
        <label>Role</label>
        <span className="badge-email">{allowRoleSwitch ? `${role} (dev switch enabled)` : role}</span>
        <span className="badge-email">{profile?.email ?? "guest@example.com"}</span>
      </div>
    </header>
  );
}

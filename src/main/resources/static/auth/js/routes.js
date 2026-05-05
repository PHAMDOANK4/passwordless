export const ROUTES = Object.freeze({
  login: "/login/",
  register: "/register/",
  verifyOtp: "/verify-otp/",
  setupAuthMethods: "/setup-auth-methods/"
});

export function route(name) {
  return ROUTES[name] || name;
}

export function goTo(path) {
  window.location.assign(path);
}

export function toLocalTime(iso: string): string {
  return new Date(iso).toLocaleString("vi-VN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function maskSecret(secret: string): string {
  if (!secret || secret.length < 8) {
    return "********";
  }

  return `${secret.slice(0, 4)}••••${secret.slice(-4)}`;
}

export function randomId(prefix: string): string {
  return `${prefix}_${Math.random().toString(36).slice(2, 10)}`;
}

export function decodeJwtPayload(token: string): Record<string, unknown> {
  const segments = token.split(".");
  if (segments.length < 2) {
    throw new Error("Token format is invalid");
  }

  const payload = segments[1].replace(/-/g, "+").replace(/_/g, "/");
  const normalized = payload.padEnd(payload.length + ((4 - (payload.length % 4)) % 4), "=");
  const json = atob(normalized);
  return JSON.parse(json);
}

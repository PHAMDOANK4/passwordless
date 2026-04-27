const API_BASE = (process.env.IDP_API_BASE as string | undefined) ?? "";

function getCookie(name: string): string | null {
  const cookieValue = document.cookie
    .split(";")
    .map((item) => item.trim())
    .find((item) => item.startsWith(`${name}=`));

  return cookieValue ? decodeURIComponent(cookieValue.slice(name.length + 1)) : null;
}

function getCsrfToken(): string | null {
  const fromCookie = getCookie("XSRF-TOKEN");
  if (fromCookie) {
    return fromCookie;
  }

  const meta = document.querySelector("meta[name='csrf-token']");
  return meta?.getAttribute("content") ?? null;
}

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  token?: string | null;
  body?: BodyInit | null;
  headers?: Record<string, string>;
  includeCsrf?: boolean;
}

export async function httpRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? "GET";
  const headers: Record<string, string> = {
    Accept: "application/json",
    ...(options.headers ?? {}),
  };

  if (options.token) {
    headers.Authorization = `Bearer ${options.token}`;
  }

  const needsCsrf = options.includeCsrf ?? method !== "GET";
  if (needsCsrf) {
    const csrfToken = getCsrfToken();
    if (csrfToken) {
      headers["X-CSRF-TOKEN"] = csrfToken;
      headers["X-XSRF-TOKEN"] = csrfToken;
    }
    headers["X-Requested-With"] = "XMLHttpRequest";
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: options.body ?? null,
    credentials: "include",
  });

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    try {
      const data = (await response.json()) as { error?: string; message?: string };
      message = data.error ?? data.message ?? message;
    } catch {
      // ignore parse failure
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export function formBody(values: Record<string, string>): URLSearchParams {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    params.set(key, value);
  });
  return params;
}

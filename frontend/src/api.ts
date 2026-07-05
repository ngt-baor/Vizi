export type HealthResponse = {
  status: string;
  service: string;
  time: string;
};

export type TemplateListItem = {
  id: number;
  name: string;
  category: string;
  previewUrl: string | null;
  widthMm: number;
  heightMm: number;
};

export type TemplateDetail = TemplateListItem & {
  canvasJson: string;
};

export type AuthUser = {
  id: number;
  email: string;
  fullName: string;
  role: string;
};

type CsrfResponse = {
  headerName: string;
  token: string;
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL
  ?? `${window.location.protocol}//${window.location.hostname}:8080`;

export async function getHealth(): Promise<HealthResponse> {
  const response = await fetch(`${apiBaseUrl}/api/health`);

  if (!response.ok) {
    throw new Error(`Health check failed: ${response.status}`);
  }

  return response.json() as Promise<HealthResponse>;
}

export async function getTemplates(): Promise<TemplateListItem[]> {
  const response = await fetch(`${apiBaseUrl}/api/templates`);

  if (!response.ok) {
    throw new Error(`Template list failed: ${response.status}`);
  }

  return response.json() as Promise<TemplateListItem[]>;
}

export async function getTemplate(id: number): Promise<TemplateDetail> {
  const response = await fetch(`${apiBaseUrl}/api/templates/${id}`);

  if (!response.ok) {
    throw new Error(`Template detail failed: ${response.status}`);
  }

  return response.json() as Promise<TemplateDetail>;
}

async function getCsrf(): Promise<CsrfResponse> {
  const response = await fetch(`${apiBaseUrl}/api/auth/csrf`, {
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(`CSRF token failed: ${response.status}`);
  }

  return response.json() as Promise<CsrfResponse>;
}

async function writeAuth(path: string, body?: BodyInit, contentType?: string): Promise<Response> {
  const csrf = await getCsrf();
  const headers = new Headers({ [csrf.headerName]: csrf.token });
  if (contentType) {
    headers.set("Content-Type", contentType);
  }

  return fetch(`${apiBaseUrl}${path}`, {
    method: "POST",
    credentials: "include",
    headers,
    body,
  });
}

async function authError(response: Response, fallback: string): Promise<Error> {
  try {
    const body = await response.json() as { message?: string };
    return new Error(body.message || fallback);
  } catch {
    return new Error(fallback);
  }
}

export async function registerAccount(
  email: string,
  password: string,
  fullName: string,
): Promise<AuthUser> {
  const response = await writeAuth(
    "/api/auth/register",
    JSON.stringify({ email, password, fullName }),
    "application/json",
  );

  if (!response.ok) {
    throw await authError(response, `Registration failed: ${response.status}`);
  }

  return response.json() as Promise<AuthUser>;
}

export async function loginAccount(email: string, password: string): Promise<void> {
  const form = new URLSearchParams({ email, password });
  const response = await writeAuth(
    "/api/auth/login",
    form,
    "application/x-www-form-urlencoded;charset=UTF-8",
  );

  if (!response.ok) {
    throw new Error("Email or password is incorrect");
  }
}

export async function getCurrentUser(): Promise<AuthUser | null> {
  const response = await fetch(`${apiBaseUrl}/api/auth/me`, {
    credentials: "include",
  });

  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Account check failed: ${response.status}`);
  }

  return response.json() as Promise<AuthUser>;
}

export async function logoutAccount(): Promise<void> {
  const response = await writeAuth("/api/auth/logout");
  if (!response.ok) {
    throw new Error(`Logout failed: ${response.status}`);
  }
}

export { apiBaseUrl };

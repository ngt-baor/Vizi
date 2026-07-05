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

export type DesignDetail = {
  id: number;
  templateId: number | null;
  name: string;
  canvasJson: string;
  widthMm: number;
  heightMm: number;
  updatedAt: string;
};

export type DesignListItem = Omit<DesignDetail, "canvasJson">;

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

async function writeWithCsrf(
  path: string,
  method: "DELETE" | "POST" | "PUT",
  body?: BodyInit,
  contentType?: string,
): Promise<Response> {
  const csrf = await getCsrf();
  const headers = new Headers({ [csrf.headerName]: csrf.token });
  if (contentType) {
    headers.set("Content-Type", contentType);
  }

  return fetch(`${apiBaseUrl}${path}`, {
    method,
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
  const response = await writeWithCsrf(
    "/api/auth/register",
    "POST",
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
  const response = await writeWithCsrf(
    "/api/auth/login",
    "POST",
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
  const response = await writeWithCsrf("/api/auth/logout", "POST");
  if (!response.ok) {
    throw new Error(`Logout failed: ${response.status}`);
  }
}

export async function getDesigns(): Promise<DesignListItem[]> {
  const response = await fetch(`${apiBaseUrl}/api/designs`, {
    credentials: "include",
  });
  if (response.status === 401) {
    throw new Error("Sign in to view your drafts");
  }
  if (!response.ok) {
    throw await authError(response, `Draft list failed: ${response.status}`);
  }

  return response.json() as Promise<DesignListItem[]>;
}

export async function getDesign(designId: number): Promise<DesignDetail> {
  const response = await fetch(`${apiBaseUrl}/api/designs/${designId}`, {
    credentials: "include",
  });
  if (response.status === 401) {
    throw new Error("Sign in to view your drafts");
  }
  if (!response.ok) {
    throw await authError(response, `Draft detail failed: ${response.status}`);
  }

  return response.json() as Promise<DesignDetail>;
}

export async function createDesignFromTemplate(templateId: number): Promise<DesignDetail> {
  const response = await writeWithCsrf(`/api/designs/from-template/${templateId}`, "POST");
  if (response.status === 401) {
    throw new Error("Sign in to save your draft");
  }
  if (!response.ok) {
    throw await authError(response, `Create draft failed: ${response.status}`);
  }

  return response.json() as Promise<DesignDetail>;
}

export async function updateDesign(
  designId: number,
  name: string,
  canvasJson: string,
): Promise<DesignDetail> {
  const response = await writeWithCsrf(
    `/api/designs/${designId}`,
    "PUT",
    JSON.stringify({ name, canvasJson }),
    "application/json",
  );
  if (response.status === 401) {
    throw new Error("Sign in to save your draft");
  }
  if (!response.ok) {
    throw await authError(response, `Save draft failed: ${response.status}`);
  }

  return response.json() as Promise<DesignDetail>;
}

export async function deleteDesign(designId: number): Promise<void> {
  const response = await writeWithCsrf(`/api/designs/${designId}`, "DELETE");
  if (response.status === 401) {
    throw new Error("Sign in to delete your draft");
  }
  if (!response.ok) {
    throw await authError(response, `Delete draft failed: ${response.status}`);
  }
}

export { apiBaseUrl };

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
  canvasJson: string;
};

export type TemplateDetail = TemplateListItem;

export type AuthUser = {
  id: number;
  email: string;
  fullName: string;
  role: string;
  phone?: string | null;
  address?: string | null;
};

export type AdminOrder = {
  id: number;
  status: string;
  totalAmount: number;
  items: OrderItemResponse[];
  userId: number | null;
  userEmail: string | null;
  userFullName: string | null;
  customerNote: string | null;
  createdAt?: string;
  updatedAt?: string;
};

export type AdminTemplate = TemplateDetail & {
  active: boolean;
};

export type PaperStock = {
  id: number;
  code: string;
  name: string;
  description: string | null;
  gsm: number | null;
  pricePer100: number;
  status: "IN_STOCK" | "OUT_OF_STOCK";
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export type PaperStockInput = Omit<PaperStock, "id" | "createdAt" | "updatedAt">;
export type AdminUser = {
  id: number;
  email: string;
  fullName: string;
  role: string;
  phone?: string | null;
  address?: string | null;
  designCount: number;
};

export type AdminDesignItem = {
  id: number;
  userId: number;
  name: string;
  widthMm: number;
  heightMm: number;
  updatedAt: string;
};

export type AdminDesignDetail = AdminDesignItem & {
  userEmail: string;
  canvasJson: string;
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

export type AiEditStrength = "light" | "balanced" | "creative" | "direct_command";

export type AiTextRewriteResponse = {
  schemaVersion: 1;
  editStrength: AiEditStrength;
  targetSide: "front" | "back";
  summary: string;
  actions: Array<{
    op: "update_text";
    layerId: string;
    text: string;
  }>;
};

export type ImageUploadResponse = {
  assetId: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  storageKey: string;
  url: string;
};


export type Icons8Icon = {
  id: string;
  name: string;
  category: string;
  subcategory: string;
  platform: string;
  previewUrl: string;
  sourceUrl: string;
  free: boolean;
  color: boolean;
  animated: boolean;
};

export type Icons8SearchResponse = {
  configured: boolean;
  creditRequired: boolean;
  creditText: string;
  creditUrl: string;
  message: string;
  icons: Icons8Icon[];
};

export type StockApiAsset = {
  id: string;
  title: string;
  kind: string;
  collection: string;
  previewUrl: string;
  sourceUrl: string;
  creator: string;
  license: string;
  licenseVersion: string;
  tags: string[];
  credit: string;
};

export type StockSearchResponse = {
  page: number;
  pageSize: number;
  total: number;
  hasMore: boolean;
  source: string;
  message: string;
  assets: StockApiAsset[];
};

export type OrderItemResponse = {
  id: number;
  quantity: number;
  subtotal: number;
  designId: number;
  designName: string;
  designSnapshotJson: string;
  widthMm: number;
  heightMm: number;
  printConfigJson: string;
};

export type OrderResponse = {
  id: number;
  status: string;
  totalAmount: number;
  items: OrderItemResponse[];
};

export type PreflightIssue = {
  level: string;
  code: string;
  message: string;
  layerIndex: number | null;
  side: "front" | "back" | null;
};

export type PreflightReport = {
  valid: boolean;
  issues: PreflightIssue[];
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

export async function listPapers(): Promise<PaperStock[]> {
  const response = await fetch(`${apiBaseUrl}/api/papers`);

  if (!response.ok) {
    throw new Error(`Paper catalog failed: ${response.status}`);
  }

  return response.json() as Promise<PaperStock[]>;
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

export async function updateProfile(
  fullName: string,
  phone: string,
  address: string,
): Promise<AuthUser> {
  const response = await writeWithCsrf(
    "/api/auth/me",
    "PUT",
    JSON.stringify({ fullName, phone, address }),
    "application/json",
  );
  if (!response.ok) {
    throw await authError(response, `Update profile failed: ${response.status}`);
  }
  return response.json() as Promise<AuthUser>;
}

export async function changeAccountEmail(
  newEmail: string,
  currentPassword: string,
): Promise<AuthUser> {
  const response = await writeWithCsrf(
    "/api/auth/me/email",
    "PUT",
    JSON.stringify({ newEmail, currentPassword }),
    "application/json",
  );
  if (!response.ok) {
    throw await authError(response, `Change email failed: ${response.status}`);
  }
  return response.json() as Promise<AuthUser>;
}

export async function changeAccountPassword(
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  const response = await writeWithCsrf(
    "/api/auth/me/password",
    "PUT",
    JSON.stringify({ currentPassword, newPassword }),
    "application/json",
  );
  if (!response.ok) {
    throw await authError(response, `Change password failed: ${response.status}`);
  }
}

export async function adminListOrders(): Promise<AdminOrder[]> {
  const response = await fetch(`${apiBaseUrl}/api/admin/orders`, { credentials: "include" });
  if (!response.ok) {
    throw await authError(response, `Admin orders failed: ${response.status}`);
  }
  return response.json() as Promise<AdminOrder[]>;
}

export async function adminUpdateOrderStatus(orderId: number, status: string): Promise<AdminOrder> {
  const response = await writeWithCsrf(
    `/api/admin/orders/${orderId}/status`,
    "PUT",
    JSON.stringify({ status }),
    "application/json",
  );
  if (!response.ok) {
    throw await authError(response, `Update order status failed: ${response.status}`);
  }
  return response.json() as Promise<AdminOrder>;
}

export async function adminListTemplates(): Promise<AdminTemplate[]> {
  const response = await fetch(`${apiBaseUrl}/api/admin/templates`, { credentials: "include" });
  if (!response.ok) {
    throw await authError(response, `Admin templates failed: ${response.status}`);
  }
  return response.json() as Promise<AdminTemplate[]>;
}

export async function adminSetTemplateActive(id: number, active: boolean): Promise<AdminTemplate> {
  const response = await writeWithCsrf(
    `/api/admin/templates/${id}/active`,
    "PUT",
    JSON.stringify({ active }),
    "application/json",
  );
  if (!response.ok) {
    throw await authError(response, `Update template failed: ${response.status}`);
  }
  return response.json() as Promise<AdminTemplate>;
}

export async function adminPublishTemplate(payload: {
  name: string;
  category: string;
  previewUrl?: string | null;
  widthMm: number;
  heightMm: number;
  canvasJson: string;
  active: boolean;
}): Promise<AdminTemplate> {
  const response = await writeWithCsrf(
    "/api/admin/templates",
    "POST",
    JSON.stringify(payload),
    "application/json",
  );
  if (!response.ok) {
    throw await authError(response, `Publish template failed: ${response.status}`);
  }
  return response.json() as Promise<AdminTemplate>;
}

export async function adminListPapers(): Promise<PaperStock[]> {
  const response = await fetch(`${apiBaseUrl}/api/admin/papers`, { credentials: "include" });
  if (!response.ok) {
    throw await authError(response, `Admin paper catalog failed: ${response.status}`);
  }
  return response.json() as Promise<PaperStock[]>;
}

export async function adminCreatePaper(payload: PaperStockInput): Promise<PaperStock> {
  const response = await writeWithCsrf(
    "/api/admin/papers",
    "POST",
    JSON.stringify(payload),
    "application/json",
  );
  if (!response.ok) {
    throw await authError(response, `Create paper failed: ${response.status}`);
  }
  return response.json() as Promise<PaperStock>;
}

export async function adminUpdatePaper(id: number, payload: PaperStockInput): Promise<PaperStock> {
  const response = await writeWithCsrf(
    `/api/admin/papers/${id}`,
    "PUT",
    JSON.stringify(payload),
    "application/json",
  );
  if (!response.ok) {
    throw await authError(response, `Update paper failed: ${response.status}`);
  }
  return response.json() as Promise<PaperStock>;
}

export async function adminDeletePaper(id: number): Promise<void> {
  const response = await writeWithCsrf(`/api/admin/papers/${id}`, "DELETE");
  if (!response.ok) {
    throw await authError(response, `Delete paper failed: ${response.status}`);
  }
}
export async function adminListUsers(): Promise<AdminUser[]> {
  const response = await fetch(`${apiBaseUrl}/api/admin/users`, { credentials: "include" });
  if (!response.ok) {
    throw await authError(response, `Admin users failed: ${response.status}`);
  }
  return response.json() as Promise<AdminUser[]>;
}

export async function adminGetDesign(designId: number): Promise<AdminDesignDetail> {
  const response = await fetch(`${apiBaseUrl}/api/admin/designs/${designId}`, {
    credentials: "include",
  });
  if (!response.ok) {
    throw await authError(response, `Admin design failed: ${response.status}`);
  }
  return response.json() as Promise<AdminDesignDetail>;
}

export async function adminListUserDesigns(userId: number): Promise<AdminDesignItem[]> {
  const response = await fetch(`${apiBaseUrl}/api/admin/users/${userId}/designs`, {
    credentials: "include",
  });
  if (!response.ok) {
    throw await authError(response, `Admin album failed: ${response.status}`);
  }
  return response.json() as Promise<AdminDesignItem[]>;
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

export async function createBlankDesign(payload: {
  name: string;
  widthMm: number;
  heightMm: number;
  canvasJson?: string;
}): Promise<DesignDetail> {
  const response = await writeWithCsrf(
    "/api/designs",
    "POST",
    JSON.stringify(payload),
    "application/json",
  );
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

export async function uploadImageAsset(file: File): Promise<ImageUploadResponse> {
  const form = new FormData();
  form.set("file", file);
  const response = await writeWithCsrf("/api/uploads/images", "POST", form);
  if (response.status === 401) {
    throw new Error("Sign in to upload images");
  }
  if (!response.ok) {
    throw await authError(response, `Image upload failed: ${response.status}`);
  }

  return response.json() as Promise<ImageUploadResponse>;
}


export async function removeBackgroundImageAsset(file: File): Promise<ImageUploadResponse> {
  const form = new FormData();
  form.set("file", file);
  const response = await writeWithCsrf("/api/uploads/images/remove-background", "POST", form);
  if (response.status === 401) {
    throw new Error("Sign in to remove image backgrounds");
  }
  if (!response.ok) {
    throw await authError(response, "Background removal failed: " + response.status);
  }

  return response.json() as Promise<ImageUploadResponse>;
}

export async function searchIcons8(
  term: string,
  language = "en",
  platform = "ios7",
  amount = 24,
): Promise<Icons8SearchResponse> {
  const params = new URLSearchParams({
    term,
    language,
    platform,
    amount: String(amount),
  });
  const response = await fetch(`${apiBaseUrl}/api/icons8/search?${params}`, {
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(`Icons8 search failed: ${response.status}`);
  }

  return response.json() as Promise<Icons8SearchResponse>;
}

export async function searchStock(
  query = "",
  kind = "all",
  page = 1,
  pageSize = 12,
): Promise<StockSearchResponse> {
  const params = new URLSearchParams({
    q: query,
    kind,
    page: String(page),
    pageSize: String(pageSize),
  });
  const response = await fetch(apiBaseUrl + "/api/stock/search?" + params, {
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error("Stock search failed: " + response.status);
  }

  return response.json() as Promise<StockSearchResponse>;
}

export async function rewriteDesignText(

  designId: number,
  layerId: string,
  prompt: string,
  editStrength: AiEditStrength,
  targetSide: "front" | "back",
): Promise<AiTextRewriteResponse> {
  const response = await writeWithCsrf(
    "/api/ai/text/rewrite",
    "POST",
    JSON.stringify({ designId, layerId, prompt, editStrength, targetSide }),
    "application/json",
  );
  if (response.status === 401) {
    throw new Error("Sign in to use AI rewrite");
  }
  if (!response.ok) {
    throw await authError(response, `AI rewrite failed: ${response.status}`);
  }

  return response.json() as Promise<AiTextRewriteResponse>;
}

export async function preflightDesign(designId: number): Promise<PreflightReport> {
  const response = await writeWithCsrf(`/api/designs/${designId}/preflight`, "POST");
  if (response.status === 401) {
    throw new Error("Sign in to run preflight");
  }
  if (!response.ok) {
    throw await authError(response, `Preflight failed: ${response.status}`);
  }

  return response.json() as Promise<PreflightReport>;
}

export async function createOrder(
  designId: number,
  paper: string,
  quantity: number,
  roundedCorners: boolean,
): Promise<OrderResponse> {
  const response = await writeWithCsrf(
    "/api/orders",
    "POST",
    JSON.stringify({ designId, paper, quantity, roundedCorners }),
    "application/json",
  );
  if (response.status === 401) {
    throw new Error("Sign in to create your order");
  }
  if (!response.ok) {
    throw await authError(response, `Create order failed: ${response.status}`);
  }

  return response.json() as Promise<OrderResponse>;
}

export async function getOrder(orderId: number): Promise<OrderResponse> {
  const response = await fetch(`${apiBaseUrl}/api/orders/${orderId}`, {
    credentials: "include",
  });
  if (response.status === 401) {
    throw new Error("Sign in to view your order");
  }
  if (!response.ok) {
    throw await authError(response, `Order detail failed: ${response.status}`);
  }

  return response.json() as Promise<OrderResponse>;
}

export { apiBaseUrl };

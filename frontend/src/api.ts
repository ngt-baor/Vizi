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

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

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

export { apiBaseUrl };

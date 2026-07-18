export type DesignSizePreset = {
  id: string;
  name: string;
  widthMm: number;
  heightMm: number;
  note: string;
};

export const designSizePresets: DesignSizePreset[] = [
  { id: "vn-standard", name: "Vietnam standard", widthMm: 90, heightMm: 54, note: "Most common" },
  { id: "vn-slim", name: "Vietnam slim", widthMm: 90, heightMm: 50, note: "Modern profile" },
  { id: "us", name: "US standard", widthMm: 89, heightMm: 51, note: "3.5 x 2 in" },
  { id: "europe", name: "Europe standard", widthMm: 85, heightMm: 55, note: "3.35 x 2.17 in" },
  { id: "asia", name: "Asia standard", widthMm: 91, heightMm: 55, note: "Japan and Korea" },
  { id: "iso-id1", name: "ISO ID-1", widthMm: 85.6, heightMm: 53.9, note: "Bank card size" },
  { id: "square", name: "Square card", widthMm: 50, heightMm: 50, note: "Compact" },
  { id: "mini", name: "Mini card", widthMm: 90, heightMm: 30, note: "Slim format" },
  { id: "folded", name: "Folded card", widthMm: 180, heightMm: 54, note: "Folds to 90 x 54" },
];

export const customDesignSizeId = "custom";

export function defaultDesignName(widthMm: number, heightMm: number): string {
  return `Business card ${widthMm} x ${heightMm} mm`;
}

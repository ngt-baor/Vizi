import tablerIconSet from "@iconify-json/tabler/icons.json";
import lucideIconSet from "@iconify-json/lucide/icons.json";
import { appleEmojiIcons } from "../generated/appleEmojiIcons";

export type LocalIconSource = "tabler" | "lucide" | "emoji";

export type LocalIcon = {
  id: string;
  source: LocalIconSource;
  label: string;
  keywords: string[];
  body?: string;
  glyph?: string;
};

type IconSet = {
  icons: Record<string, { body: string }>;
};

const iconSets: Array<{ source: Exclude<LocalIconSource, "emoji">; set: IconSet }> = [
  { source: "tabler", set: tablerIconSet as IconSet },
  { source: "lucide", set: lucideIconSet as IconSet },
];

const iconCatalog: LocalIcon[] = iconSets.flatMap(({ source, set }) => Object.entries(set.icons).map(([id, icon]) => ({
  id: source + "-" + id,
  source,
  label: humanizeIconName(id),
  keywords: [id, ...id.split("-")],
  body: icon.body,
})));

const emojiCatalog: LocalIcon[] = appleEmojiIcons.map((icon) => ({
  id: "emoji-" + icon.id,
  source: "emoji",
  label: icon.label,
  keywords: [icon.label, icon.description, ...icon.keywords, ...icon.tags],
  glyph: icon.glyph,
}));

const allLocalIcons = [...iconCatalog, ...emojiCatalog];
const assetCache = new Map<string, string>();

export const localIconCounts = {
  all: allLocalIcons.length,
  tabler: iconCatalog.filter((icon) => icon.source === "tabler").length,
  lucide: iconCatalog.filter((icon) => icon.source === "lucide").length,
  emoji: emojiCatalog.length,
};

export function searchLocalIcons(term: string, source: LocalIconSource | "all", limit = 60): LocalIcon[] {
  const query = normalizeSearch(term);
  return allLocalIcons
    .filter((icon) => source === "all" || icon.source === source)
    .filter((icon) => !query || normalizeSearch([icon.label, ...icon.keywords].join(" ")).includes(query))
    .slice(0, limit);
}

export function localIconAsset(icon: LocalIcon): string {
  const cached = assetCache.get(icon.id);
  if (cached) return cached;

  const svg = icon.source === "emoji"
    ? emojiSvg(icon.glyph ?? "?")
    : `<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 24 24" color="#151817">${icon.body ?? ""}</svg>`;
  const asset = "data:image/svg+xml;base64," + encodeBase64(svg);
  assetCache.set(icon.id, asset);
  return asset;
}

function humanizeIconName(value: string): string {
  return value
    .split("-")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function normalizeSearch(value: string): string {
  return value
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .trim();
}

function emojiSvg(glyph: string): string {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 96 96"><text x="48" y="66" text-anchor="middle" font-family="Apple Color Emoji, Segoe UI Emoji, Noto Color Emoji, sans-serif" font-size="58">${escapeXml(glyph)}</text></svg>`;
}

function escapeXml(value: string): string {
  const replacements: Record<string, string> = {
    "<": "&lt;",
    ">": "&gt;",
    "&": "&amp;",
    "'": "&apos;",
    '"': "&quot;",
  };
  return value.replace(/[<>&'"]/g, (character) => replacements[character] ?? character);
}

function encodeBase64(value: string): string {
  const bytes = new TextEncoder().encode(value);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}
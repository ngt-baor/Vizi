type ColorBucket = {
  count: number;
  red: number;
  green: number;
  blue: number;
};

type Rgb = {
  red: number;
  green: number;
  blue: number;
};

function colorDistance(left: Rgb, right: Rgb): number {
  return Math.hypot(
    left.red - right.red,
    left.green - right.green,
    left.blue - right.blue,
  );
}

function toHex(color: Rgb): string {
  return `#${[color.red, color.green, color.blue]
    .map((channel) => Math.round(channel).toString(16).padStart(2, "0"))
    .join("")}`;
}

export function quantizeImagePalette(
  pixels: Uint8ClampedArray,
  maxColors = 6,
): string[] {
  const buckets = new Map<number, ColorBucket>();
  for (let index = 0; index < pixels.length; index += 4) {
    const alpha = pixels[index + 3];
    if (alpha < 48) continue;
    const red = pixels[index];
    const green = pixels[index + 1];
    const blue = pixels[index + 2];
    const key = (red >> 5) << 6 | (green >> 5) << 3 | (blue >> 5);
    const bucket = buckets.get(key) ?? { count: 0, red: 0, green: 0, blue: 0 };
    bucket.count += 1;
    bucket.red += red;
    bucket.green += green;
    bucket.blue += blue;
    buckets.set(key, bucket);
  }

  const candidates = [...buckets.values()]
    .sort((left, right) => right.count - left.count)
    .map((bucket) => ({
      red: bucket.red / bucket.count,
      green: bucket.green / bucket.count,
      blue: bucket.blue / bucket.count,
    }));
  const selected: Rgb[] = [];
  for (const candidate of candidates) {
    if (selected.every((color) => colorDistance(color, candidate) >= 28)) {
      selected.push(candidate);
      if (selected.length === maxColors) break;
    }
  }
  return selected.map(toHex);
}

function decodeImage(source: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.decoding = "async";
    image.addEventListener("load", () => resolve(image), { once: true });
    image.addEventListener("error", () => reject(new Error("Cannot decode this image.")), { once: true });
    image.src = source;
  });
}

export async function extractImagePalette(source: string, maxColors = 6): Promise<string[]> {
  if (source.startsWith("data:") && (
    !/^data:image\/(png|jpeg|webp|gif);base64,/i.test(source)
    || source.length > 20 * 1024 * 1024
  )) {
    throw new Error("The image source is invalid or too large.");
  }
  let objectUrl = "";
  try {
    let decodedSource = source;
    if (!source.startsWith("data:")) {
      const response = await fetch(source, { credentials: "include" });
      if (!response.ok) throw new Error(`Cannot load this image (${response.status}).`);
      const blob = await response.blob();
      if (!blob.type.startsWith("image/") || blob.size > 15 * 1024 * 1024) {
        throw new Error("The image response is invalid or too large.");
      }
      objectUrl = URL.createObjectURL(blob);
      decodedSource = objectUrl;
    }

    const image = await decodeImage(decodedSource);
    const scale = Math.min(64 / image.naturalWidth, 64 / image.naturalHeight);
    const width = Math.max(1, Math.round(image.naturalWidth * scale));
    const height = Math.max(1, Math.round(image.naturalHeight * scale));
    const canvas = window.document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d", { willReadFrequently: true });
    if (!context) throw new Error("Image color analysis is unavailable.");
    context.drawImage(image, 0, 0, width, height);
    const palette = quantizeImagePalette(context.getImageData(0, 0, width, height).data, maxColors);
    if (palette.length === 0) throw new Error("The image has no visible colors.");
    return palette;
  } finally {
    if (objectUrl) URL.revokeObjectURL(objectUrl);
  }
}

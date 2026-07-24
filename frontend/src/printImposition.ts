export type PrintSheetKey = "A0" | "A1" | "A2" | "A3" | "A4";
export type CardRotation = 0 | 90;
export type PlacementReflection = "none" | "x" | "y";
export type PrintOrientation = "portrait" | "landscape";

export type PrintSheet = {
  key: PrintSheetKey;
  widthMm: number;
  heightMm: number;
};

export type ImpositionPlacement = {
  x: number;
  y: number;
  width: number;
  height: number;
  rotation: CardRotation;
};

export type ImpositionResult = {
  sheet: PrintSheet;
  orientation: PrintOrientation;
  cardRotation: CardRotation;
  cardWidthMm: number;
  cardHeightMm: number;
  columns: number;
  rows: number;
  cardsPerSheet: number;
  sheetCount: number;
  quantity: number;
  bleedMm: number;
  horizontalGapMm: number;
  verticalGapMm: number;
  /** @deprecated Use horizontalGapMm or verticalGapMm. */
  gutterMm: number;
  marginMm: number;
  placements: ImpositionPlacement[][];
};

export const PRINT_SHEETS: Record<PrintSheetKey, PrintSheet> = {
  A0: { key: "A0", widthMm: 841, heightMm: 1189 },
  A1: { key: "A1", widthMm: 594, heightMm: 841 },
  A2: { key: "A2", widthMm: 420, heightMm: 594 },
  A3: { key: "A3", widthMm: 297, heightMm: 420 },
  A4: { key: "A4", widthMm: 210, heightMm: 297 },
};

export const DEFAULT_PRINT_BLEED_MM = 2;
export const DEFAULT_PRINT_GUTTER_MM = 4;
export const DEFAULT_PRINT_MARGIN_MM = 10;

export function reflectPlacement(
  placement: ImpositionPlacement,
  sheet: PrintSheet,
  reflection: PlacementReflection,
): ImpositionPlacement {
  if (reflection === "x") {
    return {
      ...placement,
      x: sheet.widthMm - placement.x - placement.width,
    };
  }
  if (reflection === "y") {
    return {
      ...placement,
      y: sheet.heightMm - placement.y - placement.height,
    };
  }
  return { ...placement };
}

type Candidate = {
  sheet: PrintSheet;
  rotation: CardRotation;
  columns: number;
  rows: number;
  cardsPerSheet: number;
  wasteMm2: number;
};

function fitCount(availableMm: number, footprintMm: number, gutterMm: number): number {
  if (availableMm < footprintMm) {
    return 0;
  }
  return Math.floor((availableMm + gutterMm) / (footprintMm + gutterMm));
}

function makeCandidate(
  sheet: PrintSheet,
  cardWidthMm: number,
  cardHeightMm: number,
  rotation: CardRotation,
  bleedMm: number,
  horizontalGapMm: number,
  verticalGapMm: number,
  marginMm: number,
): Candidate {
  const trimWidth = rotation === 90 ? cardHeightMm : cardWidthMm;
  const trimHeight = rotation === 90 ? cardWidthMm : cardHeightMm;
  const footprintWidth = trimWidth + bleedMm * 2;
  const footprintHeight = trimHeight + bleedMm * 2;
  const availableWidth = sheet.widthMm - marginMm * 2;
  const availableHeight = sheet.heightMm - marginMm * 2;
  const columns = fitCount(availableWidth, footprintWidth, horizontalGapMm);
  const rows = fitCount(availableHeight, footprintHeight, verticalGapMm);
  const cardsPerSheet = columns * rows;
  const usedWidth = cardsPerSheet === 0 ? 0 : columns * footprintWidth + Math.max(0, columns - 1) * horizontalGapMm;
  const usedHeight = cardsPerSheet === 0 ? 0 : rows * footprintHeight + Math.max(0, rows - 1) * verticalGapMm;
  const wasteMm2 = Math.max(0, availableWidth * availableHeight - usedWidth * usedHeight);

  return { sheet, rotation, columns, rows, cardsPerSheet, wasteMm2 };
}

function compareCandidates(left: Candidate, right: Candidate): number {
  if (left.cardsPerSheet !== right.cardsPerSheet) {
    return right.cardsPerSheet - left.cardsPerSheet;
  }
  if (left.wasteMm2 !== right.wasteMm2) {
    return left.wasteMm2 - right.wasteMm2;
  }
  if (left.sheet.widthMm !== right.sheet.widthMm) {
    return left.sheet.widthMm - right.sheet.widthMm;
  }
  return left.rotation - right.rotation;
}

export function calculateImposition(input: {
  cardWidthMm: number;
  cardHeightMm: number;
  quantity: number;
  sheet: PrintSheetKey;
  orientation?: PrintOrientation;
  bleedMm?: number;
  gutterMm?: number;
  horizontalGapMm?: number;
  verticalGapMm?: number;
  marginMm?: number;
}): ImpositionResult {
  const cardWidthMm = Number.isFinite(input.cardWidthMm) ? input.cardWidthMm : 0;
  const cardHeightMm = Number.isFinite(input.cardHeightMm) ? input.cardHeightMm : 0;
  const quantity = Math.max(1, Math.floor(input.quantity));
  const nonNegative = (value: number | undefined, fallback: number) =>
    Number.isFinite(value) ? Math.max(0, value as number) : fallback;
  const bleedMm = nonNegative(input.bleedMm, DEFAULT_PRINT_BLEED_MM);
  const legacyGapMm = nonNegative(input.gutterMm, DEFAULT_PRINT_GUTTER_MM);
  const horizontalGapMm = nonNegative(input.horizontalGapMm, legacyGapMm);
  const verticalGapMm = nonNegative(input.verticalGapMm, legacyGapMm);
  const marginMm = nonNegative(input.marginMm, DEFAULT_PRINT_MARGIN_MM);
  const orientation = input.orientation ?? "portrait";
  const baseSheet = PRINT_SHEETS[input.sheet];
  if (!baseSheet || cardWidthMm <= 0 || cardHeightMm <= 0) {
    throw new Error("Card and sheet dimensions must be positive");
  }
  const sheet = orientation === "landscape"
    ? { ...baseSheet, widthMm: baseSheet.heightMm, heightMm: baseSheet.widthMm }
    : baseSheet;

  const candidates = [0, 90]
    .map((rotation) => makeCandidate(
      sheet,
      cardWidthMm,
      cardHeightMm,
      rotation as CardRotation,
      bleedMm,
      horizontalGapMm,
      verticalGapMm,
      marginMm,
    ))
    .filter((candidate) => candidate.cardsPerSheet > 0)
    .sort(compareCandidates);
  if (candidates.length === 0) {
    throw new Error("The selected card does not fit on the selected sheet");
  }

  const chosen = candidates[0];
  const trimWidth = chosen.rotation === 90 ? cardHeightMm : cardWidthMm;
  const trimHeight = chosen.rotation === 90 ? cardWidthMm : cardHeightMm;
  const footprintWidth = trimWidth + bleedMm * 2;
  const footprintHeight = trimHeight + bleedMm * 2;
  const sheetCount = Math.ceil(quantity / chosen.cardsPerSheet);
  const placements = Array.from({ length: sheetCount }, (_, sheetIndex) => {
    const count = Math.min(chosen.cardsPerSheet, quantity - sheetIndex * chosen.cardsPerSheet);
    return Array.from({ length: count }, (_, cardIndex) => {
      const column = cardIndex % chosen.columns;
      const row = Math.floor(cardIndex / chosen.columns);
      return {
        x: marginMm + bleedMm + column * (footprintWidth + horizontalGapMm),
        y: marginMm + bleedMm + row * (footprintHeight + verticalGapMm),
        width: trimWidth,
        height: trimHeight,
        rotation: chosen.rotation,
      };
    });
  });

  return {
    sheet,
    orientation,
    cardRotation: chosen.rotation,
    cardWidthMm,
    cardHeightMm,
    columns: chosen.columns,
    rows: chosen.rows,
    cardsPerSheet: chosen.cardsPerSheet,
    sheetCount,
    quantity,
    bleedMm,
    horizontalGapMm,
    verticalGapMm,
    gutterMm: horizontalGapMm,
    marginMm,
    placements,
  };
}
export type PrintSheetKey = "A0" | "A1" | "A2" | "A3" | "A4";
export type CardRotation = 0 | 90;

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
  cardRotation: CardRotation;
  columns: number;
  rows: number;
  cardsPerSheet: number;
  sheetCount: number;
  quantity: number;
  bleedMm: number;
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
  gutterMm: number,
  marginMm: number,
): Candidate {
  const trimWidth = rotation === 90 ? cardHeightMm : cardWidthMm;
  const trimHeight = rotation === 90 ? cardWidthMm : cardHeightMm;
  const footprintWidth = trimWidth + bleedMm * 2;
  const footprintHeight = trimHeight + bleedMm * 2;
  const availableWidth = sheet.widthMm - marginMm * 2;
  const availableHeight = sheet.heightMm - marginMm * 2;
  const columns = fitCount(availableWidth, footprintWidth, gutterMm);
  const rows = fitCount(availableHeight, footprintHeight, gutterMm);
  const cardsPerSheet = columns * rows;
  const usedWidth = cardsPerSheet === 0 ? 0 : columns * footprintWidth + Math.max(0, columns - 1) * gutterMm;
  const usedHeight = cardsPerSheet === 0 ? 0 : rows * footprintHeight + Math.max(0, rows - 1) * gutterMm;
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
  bleedMm?: number;
  gutterMm?: number;
  marginMm?: number;
}): ImpositionResult {
  const cardWidthMm = Number.isFinite(input.cardWidthMm) ? input.cardWidthMm : 0;
  const cardHeightMm = Number.isFinite(input.cardHeightMm) ? input.cardHeightMm : 0;
  const quantity = Math.max(1, Math.floor(input.quantity));
  const bleedMm = Math.max(0, input.bleedMm ?? DEFAULT_PRINT_BLEED_MM);
  const gutterMm = Math.max(0, input.gutterMm ?? DEFAULT_PRINT_GUTTER_MM);
  const marginMm = Math.max(0, input.marginMm ?? DEFAULT_PRINT_MARGIN_MM);
  const sheet = PRINT_SHEETS[input.sheet];
  if (!sheet || cardWidthMm <= 0 || cardHeightMm <= 0) {
    throw new Error("Card and sheet dimensions must be positive");
  }

  const candidates = [0, 90]
    .map((rotation) => makeCandidate(
      sheet,
      cardWidthMm,
      cardHeightMm,
      rotation as CardRotation,
      bleedMm,
      gutterMm,
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
        x: marginMm + bleedMm + column * (footprintWidth + gutterMm),
        y: marginMm + bleedMm + row * (footprintHeight + gutterMm),
        width: trimWidth,
        height: trimHeight,
        rotation: chosen.rotation,
      };
    });
  });

  return {
    sheet,
    cardRotation: chosen.rotation,
    columns: chosen.columns,
    rows: chosen.rows,
    cardsPerSheet: chosen.cardsPerSheet,
    sheetCount,
    quantity,
    bleedMm,
    gutterMm,
    marginMm,
    placements,
  };
}
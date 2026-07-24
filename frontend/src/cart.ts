const CART_STORAGE_KEY = "vizi.cart.designIds.v1";

export const CART_UPDATED_EVENT = "vizi-cart-updated";

function normalizeDesignIds(value: unknown): number[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return [...new Set(value.filter(
    (item): item is number => Number.isSafeInteger(item) && item > 0,
  ))];
}

export function getCartDesignIds(): number[] {
  try {
    return normalizeDesignIds(JSON.parse(window.localStorage.getItem(CART_STORAGE_KEY) ?? "[]"));
  } catch {
    return [];
  }
}

function writeCartDesignIds(ids: number[]): void {
  window.localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(normalizeDesignIds(ids)));
  window.dispatchEvent(new CustomEvent(CART_UPDATED_EVENT));
}

export function addCartDesign(designId: number): void {
  writeCartDesignIds([...getCartDesignIds(), designId]);
}

export function removeCartDesign(designId: number): void {
  writeCartDesignIds(getCartDesignIds().filter((id) => id !== designId));
}

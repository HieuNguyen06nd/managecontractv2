
export function normalizeBase64Image(input?: string | null): string | null {
  if (!input) return null;
  const idx = input.indexOf(',');
  if (idx >= 0) return input.substring(idx + 1);
  return input;
}


export function normalizeBase64Image(input?: string | null): string | null {
  if (!input) return null;
  // nếu có dạng "data:image/png;base64,AAAA..." -> lấy phần sau dấu phẩy
  const idx = input.indexOf(',');
  if (idx >= 0) return input.substring(idx + 1);
  return input;
}

const vndFormatter = new Intl.NumberFormat("vi-VN", {
  maximumFractionDigits: 0
});

export function formatVnd(value) {
  const amount = Number(value ?? 0);
  return `${vndFormatter.format(Number.isFinite(amount) ? amount : 0)} VND`;
}

const nonDigitPattern = /\D/g;

export function formatVndInput(value) {
  if (value === "" || value == null) {
    return "";
  }
  const digits = String(value).replace(nonDigitPattern, "");
  if (!digits) {
    return "";
  }
  return vndFormatter.format(Number(digits));
}

export function parseVndInput(value) {
  if (value === "" || value == null) {
    return null;
  }
  const digits = String(value).replace(nonDigitPattern, "");
  if (!digits) {
    return null;
  }
  const parsed = Number(digits);
  return Number.isFinite(parsed) ? parsed : null;
}

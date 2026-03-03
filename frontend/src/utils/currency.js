const vndFormatter = new Intl.NumberFormat("vi-VN", {
  maximumFractionDigits: 0
});

export function formatVnd(value) {
  const amount = Number(value ?? 0);
  return `${vndFormatter.format(Number.isFinite(amount) ? amount : 0)} VND`;
}

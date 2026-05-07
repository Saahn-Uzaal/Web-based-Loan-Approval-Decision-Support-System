const invalidPercentCharactersPattern = /[^0-9.,]/g;

export function normalizePercentInput(value) {
  if (value == null) {
    return "";
  }
  const compactValue = String(value).replace(/\s/g, "").replace(invalidPercentCharactersPattern, "");
  if (!compactValue) {
    return "";
  }

  const lastCommaIndex = compactValue.lastIndexOf(",");
  const lastDotIndex = compactValue.lastIndexOf(".");
  const separatorIndex = Math.max(lastCommaIndex, lastDotIndex);
  if (separatorIndex === -1) {
    return compactValue;
  }

  const separator = compactValue[separatorIndex];
  const integerPart = compactValue.slice(0, separatorIndex).replace(/[.,]/g, "");
  const fractionPart = compactValue.slice(separatorIndex + 1).replace(/[.,]/g, "");
  return `${integerPart}${separator}${fractionPart}`;
}

export function parsePercentInput(value) {
  if (value === "" || value == null) {
    return null;
  }
  const normalized = normalizePercentInput(value).replace(",", ".");
  if (!normalized || normalized === ".") {
    return null;
  }
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
}

export function percentInputToFraction(value) {
  const parsedPercent = parsePercentInput(value);
  return parsedPercent == null ? null : parsedPercent / 100;
}

export function formatPercentInputFromFraction(value, maximumFractionDigits = 4) {
  if (value === "" || value == null) {
    return "";
  }
  const percentage = Number(value) * 100;
  if (!Number.isFinite(percentage)) {
    return "";
  }
  const fixed = percentage.toFixed(maximumFractionDigits).replace(/\.?0+$/, "");
  return fixed.replace(".", ",");
}

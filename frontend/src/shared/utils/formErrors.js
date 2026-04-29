export function normalizeErrorText(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLowerCase();
}

export function mapFieldErrors(message, fieldKeywords) {
  const normalizedMessage = normalizeErrorText(message);
  return Object.fromEntries(
    Object.entries(fieldKeywords)
      .filter(([, keywords]) =>
        keywords.some((keyword) => normalizedMessage.includes(normalizeErrorText(keyword)))
      )
      .map(([field]) => [field, message])
  );
}

export function fieldErrorProps(fieldErrors, field, helperText = undefined) {
  return {
    error: Boolean(fieldErrors?.[field]),
    helperText: fieldErrors?.[field] || helperText
  };
}

export function clearFieldError(fieldErrors, field) {
  if (!fieldErrors?.[field]) {
    return fieldErrors;
  }
  const next = { ...fieldErrors };
  delete next[field];
  return next;
}

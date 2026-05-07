package com.loanapproval.dss.profile;

import java.text.Normalizer;
import java.util.Locale;

public enum EmploymentStatus {
    EMPLOYED,
    SELF_EMPLOYED,
    BUSINESS_OWNER,
    PART_TIME,
    CONTRACTOR,
    UNEMPLOYED,
    STUDENT,
    RETIRED,
    OTHER;

    public static EmploymentStatus fromInput(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String normalized = normalize(rawValue);
        if (normalized.isBlank()) {
            return null;
        }

        if (containsAny(normalized, "UNEMPLOY", "THAT NGHIEP")) {
            return UNEMPLOYED;
        }
        if (containsAny(normalized, "SELF", "FREELANCE", "TU DO")) {
            return SELF_EMPLOYED;
        }
        if (containsAny(normalized, "BUSINESS", "OWNER", "KINH DOANH", "CHU DOANH NGHIEP")) {
            return BUSINESS_OWNER;
        }
        if (containsAny(normalized, "PART", "BAN THOI GIAN")) {
            return PART_TIME;
        }
        if (containsAny(normalized, "CONTRACT", "HOP DONG", "TEMP")) {
            return CONTRACTOR;
        }
        if (containsAny(normalized, "STUDENT", "SINH VIEN")) {
            return STUDENT;
        }
        if (containsAny(normalized, "RETIRED", "NGHI HUU")) {
            return RETIRED;
        }
        if (containsAny(normalized, "FULL", "PERMANENT", "EMPLOYED", "NHAN VIEN", "CHUYEN VIEN", "SALARIED")) {
            return EMPLOYED;
        }
        return OTHER;
    }

    private static boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String rawValue) {
        String withoutDiacritics = Normalizer.normalize(rawValue, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
        return withoutDiacritics
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", " ")
            .trim();
    }
}

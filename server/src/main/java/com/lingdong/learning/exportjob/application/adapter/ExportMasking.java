package com.lingdong.learning.exportjob.application.adapter;

/** 导出场景统一使用姓氏加星号，避免泄露完整姓名。 */
final class ExportMasking {
    private ExportMasking() {
    }

    static String familyName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String normalized = name.trim();
        int firstLength = Character.charCount(normalized.codePointAt(0));
        return normalized.substring(0, firstLength) + "*";
    }
}

package com.lingdong.learning.dictionary.application;

import com.lingdong.learning.dictionary.domain.DictionaryItem;
import com.lingdong.learning.dictionary.infrastructure.cache.DictionaryItemCache;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Reads selectable dictionary items through the cache-backed query boundary. */
@Service
public class DictionaryQueryService {
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");

    private final DictionaryItemCache dictionaryItemCache;

    public DictionaryQueryService(DictionaryItemCache dictionaryItemCache) {
        this.dictionaryItemCache = dictionaryItemCache;
    }

    /** Returns enabled items only; disabled records remain available to historical-data rendering. */
    public List<DictionaryItem> findEnabledItems(String typeCode) {
        String normalizedTypeCode = normalizeTypeCode(typeCode);
        return dictionaryItemCache.findEnabledItems(normalizedTypeCode);
    }

    private String normalizeTypeCode(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("字典类型编码不能为空");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("字典类型编码格式不合法");
        }
        return normalized;
    }
}

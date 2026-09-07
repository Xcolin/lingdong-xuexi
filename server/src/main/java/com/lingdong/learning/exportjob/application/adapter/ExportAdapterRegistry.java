package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.exportjob.domain.ExportJobType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 按受控类型定位唯一适配器，启动时即拒绝重复注册。 */
@Component
public class ExportAdapterRegistry {
    private final Map<ExportJobType, ExportDatasetAdapter> adapters;

    public ExportAdapterRegistry(List<ExportDatasetAdapter> candidates) {
        EnumMap<ExportJobType, ExportDatasetAdapter> registered = new EnumMap<>(ExportJobType.class);
        for (ExportDatasetAdapter candidate : candidates) {
            Objects.requireNonNull(candidate, "导出适配器不能为空");
            if (registered.putIfAbsent(candidate.type(), candidate) != null) {
                throw new IllegalStateException("导出类型存在重复适配器：" + candidate.type());
            }
        }
        adapters = Map.copyOf(registered);
    }

    public ExportDatasetAdapter require(ExportJobType type) {
        ExportDatasetAdapter adapter = adapters.get(type);
        if (adapter == null) {
            throw new IllegalArgumentException("不支持的导出类型：" + type);
        }
        return adapter;
    }
}

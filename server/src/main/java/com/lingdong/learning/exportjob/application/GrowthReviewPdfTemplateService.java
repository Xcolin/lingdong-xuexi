package com.lingdong.learning.exportjob.application;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.growthpoint.infrastructure.pdf.GrowthReviewPdfRenderer.Template;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/** 统一模板的复盘 PDF 配置适配，不支持脚本、HTML、远程资源和任意布局指令。 */
@Service
public class GrowthReviewPdfTemplateService {
    private static final Set<String> FIELDS = Set.of("schemaVersion", "reportType", "templates");
    private final ImportExportTemplateMapper templates;
    private final ManagedAttachmentContentService content;
    private final ObjectMapper json;

    public GrowthReviewPdfTemplateService(ImportExportTemplateMapper templates,
                                         ManagedAttachmentContentService content, ObjectMapper json) {
        this.templates = templates;
        this.content = content;
        // 独立严格解析器，避免修改应用共享 ObjectMapper 的宽容策略。
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public void requireForCreation(Long templateId, String templateName, String version, Template selected) {
        if (templateId == null || selected == null) throw invalid();
        var template = templates.findById(templateId);
        if (template == null || template.status() != ImportExportTemplateStatus.ENABLED
                || template.templateType() != TemplateType.EXPORT || !"REPORT".equals(template.moduleCode())
                || !Objects.equals(template.templateName(), templateName) || !Objects.equals(template.version(), version)) {
            throw new IllegalArgumentException("复盘导出模板不存在、已停用或版本不一致");
        }
        if (!readModes(template.fileId()).contains(selected)) {
            throw new IllegalArgumentException("所选复盘模式未在该模板版本中启用");
        }
    }

    /** 只暴露受限配置的公开选项，不返回附件标识、存储键或配置全文。 */
    public List<Option> findOptions() {
        var result = new ArrayList<Option>();
        for (var template : templates.findAvailableReportExports()) {
            try {
                var modes = readModes(template.fileId()).stream().sorted().toList();
                result.add(new Option(template.id().toString(), template.templateName(), template.version(), modes));
            } catch (IllegalArgumentException exception) {
                // 同属 REPORT 的其他模板和失效附件不是复盘可选项；系统级存储故障仍向上传递。
            }
        }
        return List.copyOf(result);
    }

    public record Option(String id, String templateName, String version, List<Template> modes) { }

    private Set<Template> readModes(Long fileId) {
        var file = content.read(fileId);
        var bytes = file.content();
        if (bytes == null || bytes.length == 0 || bytes.length > 65536) throw invalid();
        try {
            var root = json.readTree(bytes);
            if (root == null || !root.isObject() || root.size() != FIELDS.size()) throw invalid();
            var names = root.fieldNames();
            while (names.hasNext()) if (!FIELDS.contains(names.next())) throw invalid();
            if (!root.path("schemaVersion").isIntegralNumber() || !root.path("schemaVersion").canConvertToInt()
                    || root.path("schemaVersion").intValue() != 1
                    || !"GROWTH_REVIEW".equals(root.path("reportType").textValue())) throw invalid();
            var modes = root.path("templates");
            if (!modes.isArray() || modes.isEmpty() || modes.size() > Template.values().length) throw invalid();
            var allowed = new HashSet<Template>();
            for (var mode : modes) {
                if (!mode.isTextual() || !("SIMPLE".equals(mode.textValue()) || "DETAILED".equals(mode.textValue()))
                        || !allowed.add(Template.valueOf(mode.textValue()))) throw invalid();
            }
            return Set.copyOf(allowed);
        } catch (IOException exception) {
            throw new IllegalArgumentException("复盘PDF模板不是有效的受限JSON配置", exception);
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("复盘PDF模板配置不合法，仅支持版本1及简洁/详细模式，文件不得超过64KB");
    }
}

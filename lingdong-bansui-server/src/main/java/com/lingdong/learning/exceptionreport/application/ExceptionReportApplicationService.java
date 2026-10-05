package com.lingdong.learning.exceptionreport.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScope;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.exceptionreport.domain.ExceptionReport;
import com.lingdong.learning.exceptionreport.domain.ExceptionReportStatus;
import com.lingdong.learning.exceptionreport.infrastructure.persistence.ExceptionReportActionRow;
import com.lingdong.learning.exceptionreport.infrastructure.persistence.ExceptionReportMapper;
import com.lingdong.learning.exceptionreport.infrastructure.persistence.ExceptionReportQuery;
import com.lingdong.learning.exceptionreport.infrastructure.persistence.ExceptionReportRow;
import com.lingdong.learning.exceptionreport.infrastructure.persistence.ExceptionReportStudentOptionRow;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.organization.domain.OrganizationEffectiveStatus;
import com.lingdong.learning.organization.domain.OrganizationStatus;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.auth.domain.AuthClientType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 编排异常报备的对象范围、单向状态、不可变历史和本地消息事件。 */
@Service
public class ExceptionReportApplicationService {
    private final PermissionDecisionService operationPermissions;
    private static final String FEATURE_CODE = "STUDENT_EXCEPTION_REPORT";
    private final ExceptionReportMapper mapper;
    private final OrganizationDataScopeService dataScopeService;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public ExceptionReportApplicationService(ExceptionReportMapper mapper,
            OrganizationDataScopeService dataScopeService, FeatureAccessService featureAccessService,
            IdGenerator idGenerator, Clock clock, PermissionDecisionService operationPermissions
    ) {
        this.operationPermissions = operationPermissions;
        this.mapper = mapper;
        this.dataScopeService = dataScopeService;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public ExceptionReportView create(AuthenticatedUser user, CreateExceptionReportCommand command) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireRole(user, "TEACHER");
        if (command == null || command.classOrganizationId() == null || command.studentId() == null
                || command.exceptionType() == null) throw new IllegalArgumentException("报备对象不能为空");
        String content = requireText(command.content(), 1, 1000, "异常描述");
        String key = requireText(command.idempotencyKey(), 8, 64, "幂等键");
        ExceptionReport existing = mapper.findByReporterAndIdempotencyKey(user.userId(), key);
        if (existing != null) {
            if (!sameRequest(existing, command, content)) throw new IllegalStateException("幂等键已用于其他报备");
            return requireVisible(user, existing.id());
        }
        if (!mapper.existsActiveTeacherStudent(user.userId(), command.classOrganizationId(), command.studentId())) {
            throw new ResourceNotFoundException("学生不存在或不可访问");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        ExceptionReport report = new ExceptionReport(idGenerator.nextId(), command.studentId(),
                command.classOrganizationId(), user.userId(), command.exceptionType(), content,
                ExceptionReportStatus.SUBMITTED, key, null, null, 0, now);
        requireWrite(mapper.insert(report));
        requireWrite(mapper.insertAction(idGenerator.nextId(), report.id(), "SUBMIT", user.userId(),
                null, "SUBMITTED", content, now));
        requireWrite(mapper.insertLocalEvent(idGenerator.nextId(), "EXCEPTION_REPORT_SUBMITTED", report.id(),
                "ORGANIZATION", report.classOrganizationId(), "有新的学生异常报备待处理", now));
        return requireVisible(user, report.id());
    }

    @Transactional
    public ExceptionReportView handle(AuthenticatedUser user, Long id, HandleExceptionReportCommand command) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireOperation(user, "EXCEPTION_REPORT_HANDLE");
        if (id == null || command == null || command.versionNo() < 0) throw new IllegalArgumentException("处理参数不合法");
        String note = requireText(command.handlingNote(), 1, 1000, "处理说明");
        ExceptionReportRow visible = findVisibleRow(user, id);
        if (visible.status() != ExceptionReportStatus.SUBMITTED) throw new IllegalStateException("报备已处理");
        LocalDateTime now = LocalDateTime.now(clock);
        requireWrite(mapper.handle(id, user.userId(), now, command.versionNo()));
        requireWrite(mapper.insertAction(idGenerator.nextId(), id, "HANDLE", user.userId(),
                "SUBMITTED", "HANDLED", note, now));
        requireWrite(mapper.insertLocalEvent(idGenerator.nextId(), "EXCEPTION_REPORT_HANDLED", id,
                "USER", visible.reporterUserId(), "学生异常报备已处理", now));
        return requireVisible(user, id);
    }

    @Transactional(readOnly = true)
    public ExceptionReportPage findPage(AuthenticatedUser user, Long classId, Long studentId,
            com.lingdong.learning.exceptionreport.domain.ExceptionReportType type,
            ExceptionReportStatus status, int page, int pageSize) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("分页参数不合法");
        ExceptionReportQuery query = query(user, classId, studentId, type, status, pageSize,
                Math.multiplyExact(page - 1, pageSize));
        return new ExceptionReportPage(mapper.findPage(query).stream().map(this::toView).toList(),
                page, pageSize, mapper.count(query));
    }

    @Transactional(readOnly = true)
    public ExceptionReportDetails findDetails(AuthenticatedUser user, Long id) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        ExceptionReportRow row = findVisibleRow(user, id);
        return new ExceptionReportDetails(toView(row), mapper.findActions(id).stream().map(this::toAction).toList());
    }

    @Transactional(readOnly = true)
    public List<ExceptionReportStudentOption> findStudentOptions(AuthenticatedUser user, Long classId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireRole(user, "TEACHER");
        if (classId == null) throw new IllegalArgumentException("班级标识不能为空");
        return mapper.findTeacherStudentOptions(user.userId(), classId).stream()
                .map(this::toStudentOption).toList();
    }

    @Transactional(readOnly = true)
    public List<ExceptionReportClassOption> findClassOptions(AuthenticatedUser user) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireOperation(user, "EXCEPTION_REPORT_READ");
        if (user.roleCodes().contains("TEACHER") && !user.roleCodes().contains("ORG_ADMIN")) {
            return mapper.findTeacherClassOptions(user.userId());
        }
        if (user.clientType() != AuthClientType.WEB && !user.roleCodes().contains("ORG_ADMIN")) throw denied();
        return dataScopeService.findAccessibleOrganizations(user.userId()).stream()
                .filter(organization -> "CLASS".equals(organization.typeCode()))
                .filter(organization -> organization.status() == OrganizationStatus.ENABLED)
                .filter(organization -> organization.effectiveStatus() == OrganizationEffectiveStatus.ENABLED)
                .map(organization -> new ExceptionReportClassOption(organization.id(), organization.name()))
                .toList();
    }

    private ExceptionReportView requireVisible(AuthenticatedUser user, Long id) { return toView(findVisibleRow(user, id)); }
    private ExceptionReportRow findVisibleRow(AuthenticatedUser user, Long id) {
        if (id == null) throw new IllegalArgumentException("报备标识不能为空");
        ExceptionReportRow row = mapper.findVisibleById(query(user, null, null, null, null, 1, 0), id);
        if (row == null) throw new ResourceNotFoundException("报备不存在或不可访问");
        return row;
    }
    private ExceptionReportQuery query(AuthenticatedUser user, Long classId, Long studentId,
            com.lingdong.learning.exceptionreport.domain.ExceptionReportType type,
            ExceptionReportStatus status, int limit, int offset) {
        requireOperation(user, "EXCEPTION_REPORT_READ");
        boolean teacher = user.roleCodes().contains("TEACHER") && !user.roleCodes().contains("ORG_ADMIN");
        if (teacher) return new ExceptionReportQuery(user.userId(), true, false, List.of(), classId, studentId, type, status, limit, offset);
        if (user.clientType() != AuthClientType.WEB && !user.roleCodes().contains("ORG_ADMIN")) throw denied();
        OrganizationDataScope scope = dataScopeService.resolve(user.userId());
        if (!scope.allOrganizations() && scope.rootPaths().isEmpty()) throw denied();
        return new ExceptionReportQuery(user.userId(), false, scope.allOrganizations(), scope.rootPaths(), classId, studentId, type, status, limit, offset);
    }
    private void requireOperation(AuthenticatedUser user, String code) {
        if (user == null || user.clientType() == null || !operationPermissions.isAllowed(user.userId(), PermissionClient.valueOf(user.clientType().name()), code)) throw denied();
    }
    private void requireRole(AuthenticatedUser user, String role) {
        requireOperation(user, "EXCEPTION_REPORT_CREATE");
        if (user.clientType() != AuthClientType.WEB && !user.roleCodes().contains(role)) throw denied();
    }
    private SystemOperationAccessDeniedException denied() { return new SystemOperationAccessDeniedException("当前身份不能访问异常报备"); }
    private String requireText(String value, int min, int max, String name) {
        String text = value == null ? "" : value.trim();
        if (text.length() < min || text.length() > max) throw new IllegalArgumentException(name + "不合法");
        return text;
    }
    private boolean sameRequest(ExceptionReport report, CreateExceptionReportCommand command, String content) {
        return report.classOrganizationId().equals(command.classOrganizationId())
                && report.studentId().equals(command.studentId()) && report.exceptionType() == command.exceptionType()
                && report.content().equals(content);
    }
    private void requireWrite(int count) { if (count != 1) throw new IllegalStateException("异常报备状态已变化"); }
    private ExceptionReportView toView(ExceptionReportRow r) { return new ExceptionReportView(r.id(), r.studentId(), r.studentName(), mask(r.studentAccount()), r.classOrganizationId(), r.className(), r.reporterUserId(), r.reporterName(), r.exceptionType(), r.content(), r.status(), r.handledBy(), r.handlerName(), r.reportedAt(), r.handledAt(), r.versionNo()); }
    private ExceptionReportActionView toAction(ExceptionReportActionRow r) { return new ExceptionReportActionView(r.id(), r.actionType(), r.operatorUserId(), r.operatorName(), r.beforeStatus(), r.afterStatus(), r.actionNote(), r.createdAt()); }
    private ExceptionReportStudentOption toStudentOption(ExceptionReportStudentOptionRow r) { return new ExceptionReportStudentOption(r.studentId(), r.studentName(), mask(r.studentAccount())); }
    private String mask(String account) { return account == null || account.length() < 4 ? "****" : account.substring(0, 2) + "****" + account.substring(account.length() - 2); }
}

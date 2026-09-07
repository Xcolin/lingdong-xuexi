package com.lingdong.learning.attendance.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.attendance.application.AttendanceEntry;
import com.lingdong.learning.attendance.application.AttendanceService;
import com.lingdong.learning.attendance.domain.AttendanceStatus;
import com.lingdong.learning.attendance.infrastructure.persistence.AttendanceActionRow;
import com.lingdong.learning.common.security.RequirePermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Web 与独立小程序共用的人工考勤接口，应用服务再次复核动态权限。 */
@RestController
@RequestMapping("/api/v1/attendance-records")
public class AttendanceController {
    private final AttendanceService service;
    public AttendanceController(AttendanceService service) { this.service = service; }

    public record PageResponse(List<AttendanceResponse> items, int page, int pageSize, long total) { }
    public record ClassResponse(String classOrganizationId, String className) { }
    public record RosterResponse(String studentId, String studentName, AttendanceResponse record) { }
    public record BatchRequest(Long classOrganizationId, LocalDate attendanceDate, List<AttendanceEntry> items) { }
    public record DetailsResponse(AttendanceResponse record, List<ActionResponse> actions) { }
    public record ActionResponse(String id, String actionType, String operatorUserId, String operatorName,
            AttendanceStatus beforeStatus, AttendanceStatus afterStatus,
            LocalTime beforeCheckinTime, LocalTime afterCheckinTime,
            LocalTime beforeCheckoutTime, LocalTime afterCheckoutTime, LocalDateTime createdAt) {
        static ActionResponse from(AttendanceActionRow r) {
            return new ActionResponse(r.id().toString(), r.actionType(), r.operatorUserId().toString(),
                    r.operatorName(), r.beforeStatus(), r.afterStatus(), r.beforeCheckinTime(), r.afterCheckinTime(),
                    r.beforeCheckoutTime(), r.afterCheckoutTime(), r.createdAt());
        }
    }

    @GetMapping
    @RequirePermission("ATTENDANCE_READ")
    public PageResponse page(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long classOrganizationId,
            @RequestParam(required = false) Long studentId, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AttendanceStatus status,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        var result = service.page(user, classOrganizationId, studentId, keyword, status, dateFrom, dateTo, page, pageSize);
        return new PageResponse(result.items().stream().map(AttendanceResponse::from).toList(), page, pageSize, result.total());
    }

    @GetMapping("/class-options")
    @RequirePermission(value = "ATTENDANCE_READ", anyOf = "ATTENDANCE_RECORD")
    public List<ClassResponse> classes(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean operational) {
        return service.classes(user, operational).stream()
                .map(c -> new ClassResponse(c.classOrganizationId().toString(), c.className())).toList();
    }

    @GetMapping("/roster")
    @RequirePermission("ATTENDANCE_RECORD")
    public List<RosterResponse> roster(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long classOrganizationId, @RequestParam LocalDate attendanceDate) {
        return service.roster(user, classOrganizationId, attendanceDate).stream()
                .map(r -> new RosterResponse(r.studentId().toString(), AttendanceResponse.mask(r.studentName()),
                        r.record() == null ? null : AttendanceResponse.from(r.record()))).toList();
    }

    @PostMapping("/batch")
    @RequirePermission("ATTENDANCE_RECORD")
    public List<AttendanceResponse> batch(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody BatchRequest request) {
        return service.batch(user, request.classOrganizationId(), request.attendanceDate(), request.items())
                .stream().map(AttendanceResponse::from).toList();
    }

    @GetMapping("/{id}")
    @RequirePermission("ATTENDANCE_READ")
    public DetailsResponse details(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        var result = service.details(user, id);
        return new DetailsResponse(AttendanceResponse.from(result.record()),
                result.actions().stream().map(ActionResponse::from).toList());
    }
}

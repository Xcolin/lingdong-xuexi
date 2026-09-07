package com.lingdong.learning.attendance.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.attendance.domain.AttendanceRecord;
import com.lingdong.learning.attendance.domain.AttendanceStatus;
import com.lingdong.learning.attendance.infrastructure.persistence.*;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationStatus;
import com.lingdong.learning.organization.domain.OrganizationEffectiveStatus;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/** 人工考勤事务编排：同班串行写入、全批次回滚、乐观更正与只追加历史。 */
@Service
public class AttendanceService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final AttendanceMapper mapper;
    private final OrganizationMapper organizations;
    private final AttendanceAccessService access;
    private final IdGenerator ids;
    private final Clock clock;

    public AttendanceService(AttendanceMapper mapper, OrganizationMapper organizations,
            AttendanceAccessService access, IdGenerator ids, Clock clock) {
        this.mapper = mapper; this.organizations = organizations;
        this.access = access; this.ids = ids; this.clock = clock;
    }

    public record Page(List<AttendanceRow> items, int page, int pageSize, long total) { }
    public record RosterEntry(Long studentId, String studentName, AttendanceRow record) { }
    public record Details(AttendanceRow record, List<AttendanceActionRow> actions) { }

    @Transactional(readOnly = true)
    public Page page(AuthenticatedUser user, Long classId, Long studentId, String keyword,
            AttendanceStatus status, LocalDate from, LocalDate to, int page, int size) {
        var scope = access.require(user, false);
        if (page < 1 || size < 1 || size > 100 || (long)(page - 1) * size > Integer.MAX_VALUE
                || (from == null) != (to == null)) throw new IllegalArgumentException("分页或日期范围不合法");
        if (from == null) { to = today(); from = to.minusDays(29); }
        if (from.isAfter(to)) throw new IllegalArgumentException("日期范围不合法");
        String name = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (name != null && name.length() > 64) throw new IllegalArgumentException("学生姓名过长");
        var query = new AttendanceQuery(scope, classId, studentId, name, status, from, to, size, (page - 1) * size);
        return new Page(mapper.findPage(query), page, size, mapper.count(query));
    }

    @Transactional(readOnly = true)
    public List<AttendanceClassRow> classes(AuthenticatedUser user, boolean operational) {
        return mapper.findClasses(access.require(user, operational), operational);
    }

    @Transactional(readOnly = true)
    public Details details(AuthenticatedUser user, Long id) {
        var scope = access.require(user, false);
        var record = visible(scope, id);
        return new Details(record, mapper.findActions(id));
    }

    @Transactional(readOnly = true)
    public List<RosterEntry> roster(AuthenticatedUser user, Long classId, LocalDate date) {
        var scope = access.require(user, true);
        validateDate(date);
        requireClass(scope, classId, false);
        // 查询该班当日记录一次，避免按学生逐次读取。
        var students = mapper.findRoster(classId, date);
        var query = new AttendanceQuery(scope, classId, null, null, null, date, date, Integer.MAX_VALUE, 0);
        Map<Long, AttendanceRow> records = new HashMap<>();
        mapper.findPage(query).forEach(row -> records.put(row.studentId(), row));
        return students.stream().map(s -> new RosterEntry(s.studentId(), s.studentName(), records.get(s.studentId()))).toList();
    }

    @Transactional
    public List<AttendanceRow> batch(AuthenticatedUser user, Long classId, LocalDate date, List<AttendanceEntry> items) {
        var scope = access.require(user, true);
        AttendanceRules.validate(date, today(), items);
        requireClass(scope, classId, true);
        List<AttendanceRow> result = new ArrayList<>();
        // 统一按学生标识加锁，降低并发调班与批次交叉导致死锁的概率。
        var ordered = items.stream().sorted(Comparator.comparing(AttendanceEntry::studentId)).toList();
        try {
            for (var item : ordered) {
                if (mapper.lockEligibleStudent(classId, item.studentId(), date) == null) throw notFound();
                var before = mapper.findExisting(classId, item.studentId(), date);
                if (before != null && sameContent(before, item)) {
                    result.add(visible(scope, before.id()));
                    continue;
                }
                if (before == null && item.versionNo() != null
                        || before != null && !Objects.equals(item.versionNo(), before.versionNo())) throw conflict();
                LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
                var after = new AttendanceRecord(before == null ? ids.nextId() : before.id(), item.studentId(),
                        classId, date, item.status(), item.checkinTime(), item.checkoutTime(), "MANUAL", user.userId(),
                        before == null ? 0 : Math.addExact(before.versionNo(), 1), before == null ? now : before.createdAt(), now);
                int updated = before == null ? mapper.insert(after) : mapper.correct(after, before.versionNo());
                if (updated != 1 || mapper.insertAction(ids.nextId(), before, after) != 1) throw conflict();
                result.add(visible(scope, after.id()));
            }
        } catch (DuplicateKeyException exception) {
            throw conflict();
        }
        return List.copyOf(result);
    }

    private void requireClass(AttendanceScope scope, Long classId, boolean lock) {
        if (classId == null || !mapper.canAccessClass(scope, classId)) throw notFound();
        Organization organization = lock ? organizations.findByIdForUpdate(classId) : organizations.findById(classId);
        if (organization == null || !"CLASS".equals(organization.typeCode())
                || organization.status() != OrganizationStatus.ENABLED
                || organization.effectiveStatus() != OrganizationEffectiveStatus.ENABLED) throw notFound();
        // 加锁后重新确认关系，避免组织移动或教师解除授权时沿用旧范围。
        if (lock && !mapper.canAccessClass(scope, classId)) throw notFound();
    }

    private AttendanceRow visible(AttendanceScope scope, Long id) {
        if (id == null) throw notFound();
        var row = mapper.findVisible(scope, id);
        if (row == null) throw notFound();
        return row;
    }

    private boolean sameContent(AttendanceRecord before, AttendanceEntry after) {
        return before.status() == after.status() && Objects.equals(before.checkinTime(), after.checkinTime())
                && Objects.equals(before.checkoutTime(), after.checkoutTime());
    }
    private LocalDate today() { return LocalDate.now(clock.withZone(BUSINESS_ZONE)); }
    private void validateDate(LocalDate date) {
        if (date == null || date.isAfter(today())) throw new IllegalArgumentException("考勤日期不合法");
    }
    private ResourceNotFoundException notFound() { return new ResourceNotFoundException("考勤对象不存在或不可访问"); }
    private IllegalStateException conflict() { return new IllegalStateException("考勤记录已被更新，请刷新后重试"); }
}

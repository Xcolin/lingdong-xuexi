package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.growthpoint.infrastructure.persistence.AnonymousRankMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.AnonymousRankPreferenceMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** 本人查看偏好与受控排名查询；偏好不参与参榜成员计算。 */
@Service
public class AnonymousRankQueryService {
    private final AnonymousRankAccess access;
    private final AnonymousRankPreferenceMapper preferences;
    private final AnonymousRankMapper ranks;
    private final UserMapper users;
    private final IdGenerator ids;
    public AnonymousRankQueryService(AnonymousRankAccess access, AnonymousRankPreferenceMapper preferences,
            AnonymousRankMapper ranks, UserMapper users, IdGenerator ids) {
        this.access=access; this.preferences=preferences; this.ranks=ranks; this.users=users; this.ids=ids;
    }
    public record Preference(boolean enabled, long version) { }
    public record WithdrawalOption(String studentId, String classId, long version) { }
    /** 本人撤回清单不带历史姓名、班级名或排名，关系撤销后仍可退出。 */
    public List<WithdrawalOption> withdrawals(AuthenticatedUser user) {
        access.requireParent(user);
        return preferences.findEnabledByParent(user.userId()).stream()
                .map(row -> new WithdrawalOption(row.studentId().toString(),row.classId().toString(),row.version())).toList();
    }
    public List<AnonymousRankAccess.StudentOption> students(AuthenticatedUser user) {
        return access.students(user);
    }
    public List<AnonymousRankMapper.ClassOption> classes(AuthenticatedUser user, Long studentId) {
        access.requireStudent(user,studentId);
        return ranks.classes(studentId);
    }
    public Preference preference(AuthenticatedUser user, Long studentId, Long classId) {
        access.requireOwner(user,studentId,classId);
        return view(preferences.find(user.userId(),studentId,classId));
    }
    @Transactional
    public Preference set(AuthenticatedUser user, Long studentId, Long classId, boolean enabled, long version) {
        access.requireSession(user);
        if (version < 0) throw new IllegalArgumentException("版本不能为负数");
        // 先获取用户行锁，再核验数据库身份，避免锁等待前建立 MySQL 旧快照。
        users.findByIdForUpdate(user.userId());
        access.requireOwner(user,studentId,classId);
        if (enabled) access.requireRead(user,studentId,classId);
        // 偏好使用当前读，不能因并发开启后的旧快照而假报撤回成功。
        var current=preferences.findForUpdate(user.userId(),studentId,classId);
        var before=view(current);
        if (before.enabled()==enabled) return before;
        if (before.version()!=version) throw new IllegalStateException("查看偏好已变化，请刷新后重试");
        var row=new AnonymousRankPreferenceMapper.Row(current==null?ids.nextId():current.id(),user.userId(),studentId,classId,enabled,Math.addExact(version,1));
        int changed=current==null?preferences.insert(row):preferences.update(row,version);
        if (changed!=1) throw new IllegalStateException("查看偏好已变化，请刷新后重试");
        return view(row);
    }
    public List<AnonymousRankMapper.Row> ranking(AuthenticatedUser user, Long studentId, Long classId) {
        access.requireRead(user,studentId,classId);
        if (!view(preferences.find(user.userId(),studentId,classId)).enabled())
            throw new SystemOperationAccessDeniedException("尚未主动开启班级排行查看");
        return ranks.list(classId);
    }
    private Preference view(AnonymousRankPreferenceMapper.Row row) {
        return row==null?new Preference(false,0):new Preference(row.enabled(),row.version());
    }
}

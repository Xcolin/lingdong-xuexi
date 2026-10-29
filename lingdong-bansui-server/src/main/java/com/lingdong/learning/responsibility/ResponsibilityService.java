package com.lingdong.learning.responsibility;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.application.StudentManagementAccessService;
import jakarta.validation.constraints.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;

@Service
public class ResponsibilityService {
    private final ResponsibilityStore store;
    private final StudentManagementAccessService access;
    private final PermissionDecisionService permissions;
    private static final Set<String> REASONS=Set.of("WORK_ADJUSTMENT","RESIGNATION_HANDOVER","TEMPORARY_TAKEOVER","OTHER");
    public ResponsibilityService(ResponsibilityStore store,StudentManagementAccessService access,PermissionDecisionService permissions){this.store=store;this.access=access;this.permissions=permissions;}
    public record Current(ResponsibilityStore.Person responsibleUser,String source,String availability,String className,int versionNo){}
    public record TransferRequest(@NotNull @Positive Long institutionOrganizationId,@NotNull @Positive Long targetUserId,
                                  @NotBlank @Size(max=32) String reasonType,@Size(max=200) String reasonText,@NotNull @Min(0) Integer expectedVersion){}
    private boolean allowed(AuthenticatedUser user,String permission){return user!=null&&user.clientType()!=null&&permissions.isAllowed(user.userId(),PermissionClient.valueOf(user.clientType().name()),permission);}
    private void requireRead(AuthenticatedUser user,long student,long org){
        boolean institution=allowed(user,"STUDENT_RESPONSIBILITY_READ")&&access.organizationAllowed(user,org)&&store.activeAdministrator(user.userId(),org);
        boolean guardian=allowed(user,"PARENT_RELATIONSHIP_READ")&&store.guardian(user.userId(),student);
        if(!institution&&!guardian)throw new SystemOperationAccessDeniedException("无权查看该机构学员负责人");
        requireEnrollment(student,org);
    }
    private void requireTransfer(AuthenticatedUser user,long student,long org){
        if(!allowed(user,"STUDENT_RESPONSIBILITY_TRANSFER")||!access.organizationAllowed(user,org)||!store.activeAdministrator(user.userId(),org))throw new SystemOperationAccessDeniedException("无权转交该机构学员负责人");
        requireEnrollment(student,org);
    }
    private void requireEnrollment(long student,long org){if(!store.enrolled(student,org))throw new ResourceNotFoundException("学员在本机构无有效在读关系");}
    @Transactional(readOnly=true) public Current current(AuthenticatedUser user,long student,long org){requireRead(user,student,org);return resolve(student,org,store.row(student,org));}
    private Current resolve(long student,long org,ResponsibilityStore.Row row){
        int version=row==null?0:row.version();
        if(row!=null&&row.userId()!=null)return new Current(store.person(row.userId()),"ASSIGNED",store.activeAdministrator(row.userId(),org)?"AVAILABLE":"UNAVAILABLE",null,version);
        var fallback=store.fallback(student,org);
        return fallback==null?new Current(null,"NONE","UNASSIGNED",null,version):new Current(fallback.person(),"HEAD_TEACHER","AVAILABLE",fallback.className(),version);
    }
    @Transactional(readOnly=true) public List<ResponsibilityStore.Person> options(AuthenticatedUser user,long student,long org){
        requireTransfer(user,student,org);var current=resolve(student,org,store.row(student,org));
        return store.candidates(org).stream().filter(p->current.responsibleUser()==null||!p.id().equals(current.responsibleUser().id())).toList();
    }
    @Transactional public Current transfer(AuthenticatedUser user,long student,TransferRequest request){
        if(request==null||request.institutionOrganizationId()==null||request.targetUserId()==null||request.expectedVersion()==null||request.expectedVersion()<0||request.institutionOrganizationId()<=0||request.targetUserId()<=0)throw new IllegalArgumentException("转交参数不完整");
        long org=request.institutionOrganizationId();requireTransfer(user,student,org);
        String reason=request.reasonType();String text=request.reasonText()==null?null:request.reasonText().trim();
        if(!REASONS.contains(reason==null?"":reason)||text!=null&&text.length()>200||"OTHER".equals(reason)&&(text==null||text.isBlank()))throw new IllegalArgumentException("请选择有效原因，其他原因须填写200字以内说明");
        var row=store.row(student,org);int before=row==null?0:row.version();
        if(request.expectedVersion()!=before)throw conflict();
        var current=resolve(student,org,row);long target=request.targetUserId();
        if(current.responsibleUser()!=null&&current.responsibleUser().id().equals(Long.toString(target)))throw new IllegalArgumentException("请选择其他负责人");
        if(store.candidates(org).stream().noneMatch(p->p.id().equals(Long.toString(target))))throw new IllegalArgumentException("目标必须是本机构有效管理员");
        try{
            if(row==null)store.insert(student,org,target,user.userId());
            else if(!store.update(student,org,target,user.userId(),before))throw conflict();
        }catch(DuplicateKeyException ex){throw conflict();}
        Long from=current.responsibleUser()==null?null:Long.valueOf(current.responsibleUser().id());
        store.history(student,org,from,target,reason,text,user.userId(),before,before+1);
        return new Current(store.person(target),"ASSIGNED","AVAILABLE",null,before+1);
    }
    private ResponsibilityConflictException conflict(){return new ResponsibilityConflictException();}
    public record HistoryPage(List<ResponsibilityStore.Transfer> items,long total,int page,int pageSize){}
    @Transactional(readOnly=true) public HistoryPage history(AuthenticatedUser user,long student,long org,int page,int pageSize){
        requireRead(user,student,org);
        if(page<1||pageSize<1||pageSize>100)throw new IllegalArgumentException("页码须大于0，每页数量须为1至100");
        return new HistoryPage(store.historyPage(student,org,pageSize,((long)page-1)*pageSize),store.historyCount(student,org),page,pageSize);
    }
}

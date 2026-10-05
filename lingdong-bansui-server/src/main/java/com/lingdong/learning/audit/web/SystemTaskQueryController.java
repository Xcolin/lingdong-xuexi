package com.lingdong.learning.audit.web;

import com.lingdong.learning.audit.application.*;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.user.application.UserDisplayNameResolver;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 系统任务只读入口，响应显式序列化标识且不包含领域私密载荷。 */
@RestController @RequestMapping("/api/v1/system-tasks")
public class SystemTaskQueryController {
    private final SystemTaskQueryService service;
    private final SystemTaskPayloadReader payloads;
    private final UserDisplayNameResolver userNames;
    public SystemTaskQueryController(SystemTaskQueryService service, SystemTaskPayloadReader payloads, UserDisplayNameResolver userNames){this.service=service;this.payloads=payloads;this.userNames=userNames;}
    @GetMapping @RequirePermission("SYSTEM_TASK_READ")
    public Page list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required=false)SystemTaskStatus status,
            @RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){
        var result=service.findPage(user,status,page,pageSize);
        List<Long> ids=new ArrayList<>();
        for(SystemTask task:result.items()){ids.add(task.submittedBy());ids.add(task.reviewedBy());}
        Map<Long,String> names=userNames.resolveAll(ids);
        return new Page(result.items().stream().map(task->Response.from(task,id->userNames.nameOf(names,id))).toList(),result.page(),result.pageSize(),result.total());
    }
    @GetMapping("/{id}") @RequirePermission("SYSTEM_TASK_READ")
    public Detail detail(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id){
        var task=service.findDetails(user,id);
        return new Detail(Response.from(task,userNames::resolve),payloads.read(task));
    }
    public record Detail(@com.fasterxml.jackson.annotation.JsonUnwrapped Response task, SystemTaskPayloadReader.Payload payload) { }
    public record Page(List<Response> items,int page,int pageSize,long total) { }
    public record Response(String id,String code,SystemTaskType type,String title,String description,
            ImpactScope impactScope,SystemTaskStatus status,String submittedBy,LocalDateTime submittedAt,
            String reviewedBy,LocalDateTime reviewedAt,String reviewComment,LocalDateTime createdAt,LocalDateTime updatedAt){
        static Response from(SystemTask task,Function<Long,String> nameOf){return new Response(task.id().toString(),task.code(),task.type(),task.title(),task.description(),
                task.impactScope(),task.status(),nameOf.apply(task.submittedBy()),task.submittedAt(),
                nameOf.apply(task.reviewedBy()),task.reviewedAt(),task.reviewComment(),task.createdAt(),task.updatedAt());}
    }
}

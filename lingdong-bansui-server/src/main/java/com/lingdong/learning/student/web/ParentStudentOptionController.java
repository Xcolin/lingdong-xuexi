package com.lingdong.learning.student.web;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.student.application.ParentStudentOptionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/parent/student-options")
public class ParentStudentOptionController {
    private final ParentStudentOptionService service;
    public ParentStudentOptionController(ParentStudentOptionService service) { this.service=service; }
    @GetMapping
    public List<ParentStudentOptionService.Option> find(@AuthenticationPrincipal AuthenticatedUser actor) {
        return service.find(actor);
    }
}

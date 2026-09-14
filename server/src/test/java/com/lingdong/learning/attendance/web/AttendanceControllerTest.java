package com.lingdong.learning.attendance.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lingdong.learning.attendance.application.AttendanceService;
import com.lingdong.learning.attendance.domain.AttendanceStatus;
import com.lingdong.learning.attendance.infrastructure.persistence.AttendanceRow;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证精度敏感 ID、中文脱敏和双端共用 JSON 请求契约。 */
class AttendanceControllerTest {
    @Test void preservesSnowflakeIdsAsStringsAndMasksNames() throws Exception {
        var now=LocalDateTime.of(2026,9,7,12,0);
        var row=new AttendanceRow(1874244142494646688L,1874244142494646689L,"张小明",1874244142494646690L,
                "一年级",LocalDate.of(2026,9,7),AttendanceStatus.NORMAL,LocalTime.of(8,0),null,"MANUAL",
                1874244142494646691L,"教师",0,now,now);
        var json=new ObjectMapper().registerModule(new JavaTimeModule()).valueToTree(AttendanceResponse.from(row));
        assertThat(json.get("id").isTextual()).isTrue();
        assertThat(json.get("studentId").asText()).isEqualTo("1874244142494646689");
        assertThat(json.get("studentName").asText()).isEqualTo("张*");
        assertThat(json.get("versionNo").isNumber()).isTrue();
    }

    @Test void acceptsStringIdsAndMinuteTimeInBatchContract() throws Exception {
        var request=new ObjectMapper().registerModule(new JavaTimeModule()).readValue("""
                {"classOrganizationId":"1874244142494646690","attendanceDate":"2026-09-07",
                 "items":[{"studentId":"1874244142494646689","status":"NORMAL","checkinTime":"08:00:00","checkoutTime":null,"versionNo":null}]}
                """,AttendanceController.BatchRequest.class);
        assertThat(request.items().get(0).studentId()).isEqualTo(1874244142494646689L);
        assertThat(request.items().get(0).checkinTime()).isEqualTo(LocalTime.of(8,0));
    }

    @Test void everyEndpointDeclaresPermissionAndRosterKeepsIdPrecision() {
        for(var method:AttendanceController.class.getDeclaredMethods()) {
            if(method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class)!=null
                    ||method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class)!=null) {
                assertThat(method.getAnnotation(RequirePermission.class)).as(method.getName()).isNotNull();
            }
        }
        var service=mock(AttendanceService.class);
        when(service.roster(null,1874244142494646690L,LocalDate.of(2026,9,7)))
                .thenReturn(List.of(new AttendanceService.RosterEntry(1874244142494646689L,"李小明",null)));
        var response=new AttendanceController(service).roster(null,1874244142494646690L,LocalDate.of(2026,9,7)).get(0);
        assertThat(response.studentId()).isEqualTo("1874244142494646689");
        // 点名操作必须能识别学生，完整姓名仅由受写权限保护的名单接口返回。
        assertThat(response.studentName()).isEqualTo("李小明");
        assertThat(response.record()).isNull();
    }
}

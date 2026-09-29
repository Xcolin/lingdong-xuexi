package com.lingdong.learning.dictionary.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DictionaryManagementControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void managesTypesAndItemsWithStringSnowflakeIdentifiers() throws Exception {
        String token = administratorToken("dictionary_api_admin");

        MvcResult typeResult = mockMvc.perform(post("/api/v1/dictionaries/types")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"LEARNING_SCENE","name":"学习场景","sortOrder":10}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.code").value("LEARNING_SCENE"))
                .andReturn();
        String typeId = body(typeResult).path("id").asText();

        MvcResult itemResult = mockMvc.perform(post("/api/v1/dictionaries/types/{typeId}/items", typeId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"HOME","name":"家庭","sortOrder":10,"defaultItem":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.typeId").value(typeId))
                .andExpect(jsonPath("$.defaultItem").value(true))
                .andReturn();
        String itemId = body(itemResult).path("id").asText();

        mockMvc.perform(get("/api/v1/dictionaries/types")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].name".formatted(typeId)).value("学习场景"));

        mockMvc.perform(put("/api/v1/dictionaries/types/{typeId}", typeId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"学习使用场景","sortOrder":20,"status":"ENABLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LEARNING_SCENE"))
                .andExpect(jsonPath("$.name").value("学习使用场景"));

        mockMvc.perform(put("/api/v1/dictionaries/items/{itemId}", itemId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"家庭场景","sortOrder":20,"status":"DISABLED","defaultItem":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(get("/api/v1/dictionaries/types/{typeId}/items", typeId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(itemId))
                .andExpect(jsonPath("$[0].status").value("DISABLED"));
    }

    @Test
    void rejectsUsersWithoutDynamicDictionaryPermissionAndDisabledFeature() throws Exception {
        String ordinaryToken = ordinaryToken("dictionary_api_ordinary");
        mockMvc.perform(get("/api/v1/dictionaries/types")
                        .header("Authorization", bearer(ordinaryToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        String administratorToken = administratorToken("dictionary_api_disabled");
        jdbcTemplate.update("""
                update sys_feature_toggle
                set status = 'DISABLED'
                where feature_code = 'DICTIONARY_MANAGEMENT' and scope_key = 'GLOBAL'
                """);
        mockMvc.perform(get("/api/v1/dictionaries/types")
                        .header("Authorization", bearer(administratorToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
    }

    @Test
    void publishesDictionaryPathsInAuthenticatedOpenApiJson() throws Exception {
        String token = administratorToken("dictionary_api_openapi");
        mockMvc.perform(get("/api/v1/openapi")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/dictionaries/types']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/dictionaries/types/{typeId}/items']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/dictionaries/items/{itemId}']").exists());
    }

    private String administratorToken(String username) throws Exception {
        User user = createUser(username, "字典管理测试管理员");
        Role role = roleMapper.findByCode("SYS_ADMIN");
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return setPasswordAndLogin(user);
    }

    private String ordinaryToken(String username) throws Exception {
        User passwordAdministrator = createUser(username + "_password_admin", "密码设置管理员");
        Role administratorRole = roleMapper.findByCode("SYS_ADMIN");
        userAccessApplicationService.assignRole(
                new AssignRoleToUserCommand(passwordAdministrator.id(), administratorRole.id(), null));
        User ordinaryUser = createUser(username, "字典管理普通用户");
        authenticationApplicationService.setPlatformUserPassword(
                new SetPlatformUserPasswordCommand(passwordAdministrator.id(), ordinaryUser.id(), "Password123"));
        return login(ordinaryUser);
    }

    private User createUser(String username, String displayName) {
        return userAccessApplicationService.createUser(
                new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
    }

    private String setPasswordAndLogin(User user) throws Exception {
        authenticationApplicationService.setPlatformUserPassword(
                new SetPlatformUserPasswordCommand(user.id(), user.id(), "Password123"));
        return login(user);
    }

    private String login(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123","deviceId":"%s-device","deviceName":"字典管理测试浏览器"}
                                """.formatted(user.username(), user.username())))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).path("accessToken").asText();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}

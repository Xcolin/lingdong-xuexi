package com.lingdong.learning.growthpoint.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.growthpoint.application.GrowthRewardExchangeCleanupService;
import com.lingdong.learning.feature.domain.FeatureStatus;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleMapper;
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
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RewardExchangeControllerTest {
    private static final String DATABASE_NAME = "reward_exchange_"
            + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void isolateDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:" + DATABASE_NAME
                + ";MODE=MySQL;DB_CLOSE_DELAY=0;DATABASE_TO_LOWER=TRUE");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SqlSessionTemplate sqlSessionTemplate;
    @Autowired private GrowthRewardExchangeCleanupService cleanupService;
    @Autowired private FeatureToggleMapper featureToggleMapper;

    @Test
    void primaryParentManagesStudentRewardAndStudentOnlySeesOnlineReward() throws Exception {
        Fixture fixture = createFixture();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(2).withNano(0);

        MvcResult createResult = mockMvc.perform(post("/api/v1/rewards/students/{studentId}",
                        fixture.student().id())
                        .header("Authorization", bearer(fixture.parentToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rewardBody("周末公园半日游", 30, "完成约定后一起去公园", expiresAt, "ONLINE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.studentId").value(fixture.student().id().toString()))
                .andExpect(jsonPath("$.rewardName").value("周末公园半日游"))
                .andExpect(jsonPath("$.requiredPoints").value(30))
                .andExpect(jsonPath("$.status").value("ONLINE"))
                .andReturn();
        Long rewardId = responseId(createResult);
        assertThat(rewardId.toString()).hasSize(19);

        mockMvc.perform(get("/api/v1/rewards/students/{studentId}?page=1&pageSize=20", fixture.student().id())
                        .header("Authorization", bearer(fixture.parentToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(rewardId.toString()))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/v1/rewards/students/{studentId}?page=1&pageSize=20", fixture.student().id())
                        .header("Authorization", bearer(fixture.otherParentToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/rewards/me?page=1&pageSize=20")
                        .header("Authorization", bearer(fixture.studentToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(rewardId.toString()))
                .andExpect(jsonPath("$.items[0].rewardName").value("周末公园半日游"))
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(patch("/api/v1/rewards/{rewardId}", rewardId)
                        .header("Authorization", bearer(fixture.parentToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rewardBody("周末图书馆", 25, null, expiresAt.plusDays(1), "OFFLINE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rewardName").value("周末图书馆"))
                .andExpect(jsonPath("$.requiredPoints").value(25))
                .andExpect(jsonPath("$.status").value("OFFLINE"));
        mockMvc.perform(get("/api/v1/rewards/me?page=1&pageSize=20")
                        .header("Authorization", bearer(fixture.studentToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(delete("/api/v1/rewards/{rewardId}", rewardId)
                        .header("Authorization", bearer(fixture.parentToken())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/rewards/students/{studentId}?page=1&pageSize=20", fixture.student().id())
                        .header("Authorization", bearer(fixture.parentToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void rejectsInvalidRewardFields() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(post("/api/v1/rewards/students/{studentId}", fixture.student().id())
                        .header("Authorization", bearer(fixture.parentToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rewardBody(" ", 0, "x".repeat(201), LocalDateTime.now().minusDays(1), "ONLINE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studentAppliesWithoutDeductionAndParentApprovesThenVerifies() throws Exception {
        Fixture fixture = createFixture();
        setPointBalance(fixture.student().id(), 50, 50);
        Long rewardId = createReward(fixture, "周末电影", 30);

        mockMvc.perform(get("/api/v1/rewards/me/summary")
                        .header("Authorization", bearer(fixture.studentToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(fixture.student().id().toString()))
                .andExpect(jsonPath("$.availablePoints").value(50))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        MvcResult applyResult = mockMvc.perform(post("/api/v1/reward-exchanges")
                        .header("Authorization", bearer(fixture.studentToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rewardId\":\"%s\"}".formatted(rewardId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.rewardId").value(rewardId.toString()))
                .andExpect(jsonPath("$.studentId").value(fixture.student().id().toString()))
                .andExpect(jsonPath("$.rewardName").value("周末电影"))
                .andExpect(jsonPath("$.requiredPoints").value(30))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.requestedAt").isNotEmpty())
                .andExpect(jsonPath("$.approvalDeadline").isNotEmpty())
                .andReturn();
        Long exchangeId = responseId(applyResult);

        assertPointBalance(fixture.student().id(), 50, 50);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM growth_point_ledger WHERE source_exchange_id = ?",
                Long.class, exchangeId)).isZero();
        mockMvc.perform(post("/api/v1/reward-exchanges")
                        .header("Authorization", bearer(fixture.studentToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rewardId\":\"%s\"}".formatted(rewardId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        mockMvc.perform(get("/api/v1/reward-exchanges/me?page=1&pageSize=20")
                        .header("Authorization", bearer(fixture.studentToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(exchangeId.toString()))
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/v1/reward-exchanges/students/{studentId}?page=1&pageSize=20", fixture.student().id())
                        .header("Authorization", bearer(fixture.parentToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(exchangeId.toString()))
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/v1/reward-exchanges/students/{studentId}?page=1&pageSize=20", fixture.student().id())
                        .header("Authorization", bearer(fixture.otherParentToken())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/reward-exchanges/{exchangeId}/approve", exchangeId)
                        .header("Authorization", bearer(fixture.parentToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());
        assertPointBalance(fixture.student().id(), 50, 20);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM growth_point_ledger WHERE source_exchange_id = ? "
                        + "AND change_type = 'REDEMPTION' AND amount = 0 AND available_delta = -30",
                Long.class, exchangeId)).isEqualTo(1L);
        mockMvc.perform(post("/api/v1/reward-exchanges/{exchangeId}/approve", exchangeId)
                        .header("Authorization", bearer(fixture.parentToken())))
                .andExpect(status().isConflict());
        assertPointBalance(fixture.student().id(), 50, 20);

        mockMvc.perform(post("/api/v1/reward-exchanges/{exchangeId}/verify", exchangeId)
                        .header("Authorization", bearer(fixture.parentToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedAt").isNotEmpty());
        mockMvc.perform(post("/api/v1/reward-exchanges/{exchangeId}/verify", exchangeId)
                        .header("Authorization", bearer(fixture.parentToken())))
                .andExpect(status().isConflict());
        assertPointBalance(fixture.student().id(), 50, 20);
    }

    @Test
    void insufficientBalanceAndRejectedExchangeNeverDeductPoints() throws Exception {
        Fixture fixture = createFixture();
        setPointBalance(fixture.student().id(), 20, 20);
        Long rewardId = createReward(fixture, "科学馆参观", 30);

        mockMvc.perform(post("/api/v1/reward-exchanges")
                        .header("Authorization", bearer(fixture.studentToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rewardId\":\"%s\"}".formatted(rewardId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM growth_reward_exchange WHERE reward_id = ?",
                Long.class, rewardId)).isZero();

        setPointBalance(fixture.student().id(), 50, 50);
        MvcResult applyResult = mockMvc.perform(post("/api/v1/reward-exchanges")
                        .header("Authorization", bearer(fixture.studentToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rewardId\":\"%s\"}".formatted(rewardId)))
                .andExpect(status().isCreated())
                .andReturn();
        Long exchangeId = responseId(applyResult);
        setPointBalance(fixture.student().id(), 50, 10);
        mockMvc.perform(post("/api/v1/reward-exchanges/{exchangeId}/approve", exchangeId)
                        .header("Authorization", bearer(fixture.parentToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        assertPointBalance(fixture.student().id(), 50, 10);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM growth_reward_exchange WHERE id = ?",
                String.class, exchangeId)).isEqualTo("PENDING_APPROVAL");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM growth_point_ledger WHERE source_exchange_id = ?",
                Long.class, exchangeId)).isZero();

        mockMvc.perform(post("/api/v1/reward-exchanges/{exchangeId}/reject", exchangeId)
                        .header("Authorization", bearer(fixture.parentToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectReason\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/reward-exchanges/{exchangeId}/reject", exchangeId)
                        .header("Authorization", bearer(fixture.parentToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectReason\":\"本周约定任务尚未完成\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("本周约定任务尚未完成"));
        assertPointBalance(fixture.student().id(), 50, 10);
    }

    @Test
    void cleanupAutoRejectsOverdueAndExpiresRewardsWithoutTouchingPendingVerification() throws Exception {
        Fixture fixture = createFixture();
        setPointBalance(fixture.student().id(), 100, 100);

        Long overdueRewardId = createReward(fixture, "超时奖励", 10);
        Long overdueExchangeId = applyExchange(fixture, overdueRewardId);
        jdbcTemplate.update(
                "UPDATE growth_reward_exchange SET requested_at = ?, approval_deadline = ? WHERE id = ?",
                LocalDateTime.now().minusHours(73), LocalDateTime.now().minusMinutes(1),
                overdueExchangeId);

        Long expiredRewardId = createReward(fixture, "到期奖励", 20);
        Long expiredExchangeId = applyExchange(fixture, expiredRewardId);
        jdbcTemplate.update("UPDATE growth_reward SET expires_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), expiredRewardId);

        Long verificationRewardId = createReward(fixture, "待核销奖励", 30);
        Long verificationExchangeId = applyExchange(fixture, verificationRewardId);
        mockMvc.perform(post("/api/v1/reward-exchanges/{exchangeId}/approve", verificationExchangeId)
                        .header("Authorization", bearer(fixture.parentToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
        jdbcTemplate.update("UPDATE growth_reward SET expires_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), verificationRewardId);
        sqlSessionTemplate.clearCache();

        assertThat(cleanupService.processAll()).isEqualTo(4);
        assertThat(cleanupService.processAll()).isZero();

        assertExchangeStatus(overdueExchangeId, "AUTO_REJECTED");
        assertExchangeStatus(expiredExchangeId, "EXPIRED");
        assertExchangeStatus(verificationExchangeId, "PENDING_VERIFICATION");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM growth_reward WHERE id = ?", String.class, expiredRewardId))
                .isEqualTo("OFFLINE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM growth_reward WHERE id = ?", String.class, verificationRewardId))
                .isEqualTo("OFFLINE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM growth_point_ledger WHERE source_exchange_id IN (?, ?)",
                Long.class, overdueExchangeId, expiredExchangeId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM growth_point_ledger WHERE source_exchange_id = ?",
                Long.class, verificationExchangeId)).isEqualTo(1L);
        assertPointBalance(fixture.student().id(), 100, 70);

        mockMvc.perform(post("/api/v1/reward-exchanges/{exchangeId}/approve", overdueExchangeId)
                        .header("Authorization", bearer(fixture.parentToken())))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/reward-exchanges/{exchangeId}/approve", expiredExchangeId)
                        .header("Authorization", bearer(fixture.parentToken())))
                .andExpect(status().isConflict());
    }

    @Test
    void teacherAndUnrelatedActorsCannotReachRewardOperations() throws Exception {
        Fixture fixture = createFixture();
        setPointBalance(fixture.student().id(), 50, 50);
        Long rewardId = createReward(fixture, "家庭专属奖励", 20);
        Long exchangeId = applyExchange(fixture, rewardId);

        mockMvc.perform(get("/api/v1/rewards/students/{studentId}?page=1&pageSize=20",
                        fixture.student().id())
                        .header("Authorization", bearer(fixture.teacherToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/v1/reward-exchanges/{exchangeId}/approve", exchangeId)
                        .header("Authorization", bearer(fixture.teacherToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/v1/reward-exchanges/{exchangeId}/approve", exchangeId)
                        .header("Authorization", bearer(fixture.otherParentToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/rewards/students/{studentId}?page=0&pageSize=101",
                        fixture.student().id())
                        .header("Authorization", bearer(fixture.parentToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void disabledFeatureBlocksWebAndMiniappDirectRequests() throws Exception {
        Fixture fixture = createFixture();
        setPointBalance(fixture.student().id(), 50, 50);
        Long rewardId = createReward(fixture, "停用验证奖励", 20);

        featureToggleMapper.updateGlobalStatus("REWARD_EXCHANGE", FeatureStatus.DISABLED);
        try {
            mockMvc.perform(get("/api/v1/rewards/students/{studentId}?page=1&pageSize=20",
                            fixture.student().id())
                            .header("Authorization", bearer(fixture.parentToken())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
            mockMvc.perform(get("/api/v1/rewards/me?page=1&pageSize=20")
                            .header("Authorization", bearer(fixture.studentToken())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
            mockMvc.perform(get("/api/v1/rewards/me/summary")
                            .header("Authorization", bearer(fixture.studentToken())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
            mockMvc.perform(post("/api/v1/reward-exchanges")
                            .header("Authorization", bearer(fixture.studentToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rewardId\":\"%s\"}".formatted(rewardId)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
        } finally {
            featureToggleMapper.updateGlobalStatus("REWARD_EXCHANGE", FeatureStatus.ENABLED);
        }
    }

    @Test
    void exchangeKeepsSnapshotAndRejectsOfflineExpiredOrCrossStudentReward() throws Exception {
        Fixture fixture = createFixture();
        setPointBalance(fixture.student().id(), 100, 100);
        Long rewardId = createReward(fixture, "原始奖励名称", 30);
        Long exchangeId = applyExchange(fixture, rewardId);

        mockMvc.perform(patch("/api/v1/rewards/{rewardId}", rewardId)
                        .header("Authorization", bearer(fixture.parentToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rewardBody("修改后的奖励", 80, "修改后的说明",
                                LocalDateTime.now().plusDays(3).withNano(0), "ONLINE")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reward-exchanges/me?page=1&pageSize=20")
                        .header("Authorization", bearer(fixture.studentToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(exchangeId.toString()))
                .andExpect(jsonPath("$.items[0].rewardName").value("原始奖励名称"))
                .andExpect(jsonPath("$.items[0].requiredPoints").value(30));

        Long offlineRewardId = createReward(fixture, "已下架奖励", 10);
        mockMvc.perform(patch("/api/v1/rewards/{rewardId}", offlineRewardId)
                        .header("Authorization", bearer(fixture.parentToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rewardBody("已下架奖励", 10, null,
                                LocalDateTime.now().plusDays(2).withNano(0), "OFFLINE")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/reward-exchanges")
                        .header("Authorization", bearer(fixture.studentToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rewardId\":\"%s\"}".formatted(offlineRewardId)))
                .andExpect(status().isNotFound());

        Long expiredRewardId = createReward(fixture, "已过期奖励", 10);
        jdbcTemplate.update("UPDATE growth_reward SET expires_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), expiredRewardId);
        sqlSessionTemplate.clearCache();
        mockMvc.perform(post("/api/v1/reward-exchanges")
                        .header("Authorization", bearer(fixture.studentToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rewardId\":\"%s\"}".formatted(expiredRewardId)))
                .andExpect(status().isNotFound());

        IssuedStudent otherStudent = createStudent(fixture.parentToken(), "另一名奖励测试学生");
        String otherStudentToken = studentLoginToken(otherStudent);
        mockMvc.perform(post("/api/v1/reward-exchanges")
                        .header("Authorization", bearer(otherStudentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rewardId\":\"%s\"}".formatted(rewardId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void concurrentApprovalDeductsAvailablePointsExactlyOnce() throws Exception {
        Fixture fixture = createFixture();
        setPointBalance(fixture.student().id(), 100, 100);
        Long rewardId = createReward(fixture, "并发审批奖励", 30);
        Long exchangeId = applyExchange(fixture, rewardId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = List.of(
                    executor.submit(() -> concurrentApprove(
                            fixture.parentToken(), exchangeId, ready, start)),
                    executor.submit(() -> concurrentApprove(
                            fixture.parentToken(), exchangeId, ready, start))
            );
            ready.await();
            start.countDown();
            List<Integer> statuses = List.of(results.get(0).get(), results.get(1).get());
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }

        assertPointBalance(fixture.student().id(), 100, 70);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM growth_point_ledger WHERE source_exchange_id = ?",
                Long.class, exchangeId)).isEqualTo(1L);
    }

    private Fixture createFixture() throws Exception {
        User administrator = createUserWithRole("reward_sys_admin", "奖励测试系统管理员", "SYS_ADMIN");
        User parent = createUserWithRole("reward_parent", "奖励测试家长", "PARENT");
        User otherParent = createUserWithRole("reward_other_parent", "奖励测试无关家长", "PARENT");
        User teacher = createUserWithRole("reward_teacher", "奖励测试教师", "TEACHER");
        setPassword(administrator, administrator);
        setPassword(administrator, parent);
        setPassword(administrator, otherParent);
        setPassword(administrator, teacher);
        String parentToken = platformLoginToken("reward_parent");
        String otherParentToken = platformLoginToken("reward_other_parent");
        String teacherToken = platformLoginToken("reward_teacher");
        IssuedStudent student = createStudent(parentToken, "奖励测试学生");
        return new Fixture(
                student, parentToken, otherParentToken, teacherToken, studentLoginToken(student));
    }

    private Long createReward(Fixture fixture, String rewardName, long requiredPoints) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/rewards/students/{studentId}",
                        fixture.student().id())
                        .header("Authorization", bearer(fixture.parentToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rewardBody(rewardName, requiredPoints, null,
                                LocalDateTime.now().plusDays(2).withNano(0), "ONLINE")))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private Long applyExchange(Fixture fixture, Long rewardId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/reward-exchanges")
                        .header("Authorization", bearer(fixture.studentToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rewardId\":\"%s\"}".formatted(rewardId)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private void assertExchangeStatus(Long exchangeId, String expectedStatus) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM growth_reward_exchange WHERE id = ?",
                String.class, exchangeId)).isEqualTo(expectedStatus);
    }

    private void setPointBalance(Long studentId, long totalPoints, long availablePoints) {
        assertThat(jdbcTemplate.update(
                "UPDATE growth_point_account SET total_points = ?, available_points = ?, "
                        + "version_no = version_no + 1 WHERE student_id = ?",
                totalPoints, availablePoints, studentId)).isEqualTo(1);
        sqlSessionTemplate.clearCache();
    }

    private void assertPointBalance(Long studentId, long totalPoints, long availablePoints) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT total_points FROM growth_point_account WHERE student_id = ?",
                Long.class, studentId)).isEqualTo(totalPoints);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT available_points FROM growth_point_account WHERE student_id = ?",
                Long.class, studentId)).isEqualTo(availablePoints);
    }

    private String rewardBody(
            String rewardName,
            long requiredPoints,
            String description,
            LocalDateTime expiresAt,
            String rewardStatus
    ) throws Exception {
        ObjectNode body = objectMapper.createObjectNode()
                .put("rewardName", rewardName)
                .put("requiredPoints", requiredPoints)
                .put("expiresAt", expiresAt.toString())
                .put("status", rewardStatus);
        if (description != null) {
            body.put("description", description);
        }
        return objectMapper.writeValueAsString(body);
    }

    private IssuedStudent createStudent(String token, String studentName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentName\":\"%s\"}".formatted(studentName)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return new IssuedStudent(response.path("id").asLong(), response.path("studentAccount").asText(),
                response.path("initialLoginCode").asText());
    }

    private String studentLoginToken(IssuedStudent student) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/student-sessions/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentAccount":"%s","loginCode":"%s","deviceId":"reward-student-%s","deviceName":"奖励测试设备"}
                                """.formatted(student.account(), student.loginCode(), student.id())))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }

    private User createUserWithRole(String username, String displayName, String roleCode) {
        User user = userAccessApplicationService.createUser(
                new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }

    private void setPassword(User administrator, User target) {
        authenticationApplicationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                administrator.id(), target.id(), "Password123"));
    }

    private String platformLoginToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123","deviceId":"%s-device","deviceName":"奖励测试浏览器"}
                                """.formatted(username, username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }

    private Long responseId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private int concurrentApprove(
            String parentToken,
            Long exchangeId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(post("/api/v1/reward-exchanges/{exchangeId}/approve", exchangeId)
                        .header("Authorization", bearer(parentToken)))
                .andReturn().getResponse().getStatus();
    }

    private record IssuedStudent(Long id, String account, String loginCode) {
    }

    private record Fixture(
            IssuedStudent student,
            String parentToken,
            String otherParentToken,
            String teacherToken,
            String studentToken
    ) {
    }
}

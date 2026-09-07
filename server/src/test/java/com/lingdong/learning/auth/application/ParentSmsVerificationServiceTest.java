package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.infrastructure.config.ParentSmsProperties;
import com.lingdong.learning.auth.infrastructure.memory.InMemoryParentSmsVerificationStore;
import com.lingdong.learning.auth.infrastructure.security.ParentSmsCodeHasher;
import com.lingdong.learning.auth.domain.AuthClientType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParentSmsVerificationServiceTest {
    private static final String MOBILE = "13800138000";
    private static final String SOURCE_DIGEST = "source-digest";
    private static final String CODE = "384291";

    @Test
    void issuesCodeWithoutReturningPlaintextAndConsumesItOnce() {
        MutableClock clock = new MutableClock();
        RecordingSmsSender sender = new RecordingSmsSender();
        ParentSmsVerificationService service = service(clock, sender);

        IssuedParentSmsCode issued = service.issue(
                MOBILE, ParentSmsPurpose.REGISTER_OR_LOGIN, AuthClientType.WEB, SOURCE_DIGEST);

        assertThat(issued.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(5)));
        assertThat(issued.retryAfterSeconds()).isEqualTo(60);
        assertThat(issued.toString()).doesNotContain(CODE);
        assertThat(sender.deliveries()).containsExactly(
                new SmsDelivery(MOBILE, ParentSmsPurpose.REGISTER_OR_LOGIN));

        service.verifyAndConsume(MOBILE, ParentSmsPurpose.REGISTER_OR_LOGIN, AuthClientType.WEB, CODE);
        assertThatThrownBy(() -> service.verifyAndConsume(
                MOBILE, ParentSmsPurpose.REGISTER_OR_LOGIN, AuthClientType.WEB, CODE))
                .isInstanceOf(ParentSmsVerificationFailedException.class);
    }

    @Test
    void isolatesPurposeAndClientAndRejectsExpiredCode() {
        MutableClock clock = new MutableClock();
        ParentSmsVerificationService service = service(clock, new RecordingSmsSender());
        service.issue(MOBILE, ParentSmsPurpose.REGISTER_OR_LOGIN, AuthClientType.WEB, SOURCE_DIGEST);

        assertThatThrownBy(() -> service.verifyAndConsume(
                MOBILE, ParentSmsPurpose.RESET_PASSWORD, AuthClientType.WEB, CODE))
                .isInstanceOf(ParentSmsVerificationFailedException.class);
        assertThatThrownBy(() -> service.verifyAndConsume(
                MOBILE, ParentSmsPurpose.REGISTER_OR_LOGIN, AuthClientType.MINIAPP, CODE))
                .isInstanceOf(ParentSmsVerificationFailedException.class);

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        assertThatThrownBy(() -> service.verifyAndConsume(
                MOBILE, ParentSmsPurpose.REGISTER_OR_LOGIN, AuthClientType.WEB, CODE))
                .isInstanceOf(ParentSmsVerificationFailedException.class);
    }

    @Test
    void limitsSameMobilePurposeAndClientToThreeIssuesPerMinute() {
        MutableClock clock = new MutableClock();
        ParentSmsVerificationService service = service(clock, new RecordingSmsSender());
        for (int attempt = 0; attempt < 3; attempt++) {
            service.issue(MOBILE, ParentSmsPurpose.REGISTER_OR_LOGIN, AuthClientType.WEB, SOURCE_DIGEST);
        }

        assertThatThrownBy(() -> service.issue(
                MOBILE, ParentSmsPurpose.REGISTER_OR_LOGIN, AuthClientType.WEB, SOURCE_DIGEST))
                .isInstanceOf(RateLimitedException.class);

        clock.advance(Duration.ofSeconds(61));
        service.issue(MOBILE, ParentSmsPurpose.REGISTER_OR_LOGIN, AuthClientType.WEB, SOURCE_DIGEST);
    }

    @Test
    void rejectsInvalidMobileBeforeSending() {
        RecordingSmsSender sender = new RecordingSmsSender();
        ParentSmsVerificationService service = service(new MutableClock(), sender);

        assertThatThrownBy(() -> service.issue(
                "138 0013 8000", ParentSmsPurpose.REGISTER_OR_LOGIN, AuthClientType.WEB, SOURCE_DIGEST))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(sender.deliveries()).isEmpty();
    }

    @Test
    void rejectsMissingPurposeAndClientAsValidationErrors() {
        ParentSmsVerificationService service = service(new MutableClock(), new RecordingSmsSender());

        assertThatThrownBy(() -> service.issue(MOBILE, null, AuthClientType.WEB, SOURCE_DIGEST))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.issue(
                MOBILE, ParentSmsPurpose.REGISTER_OR_LOGIN, null, SOURCE_DIGEST))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.verifyAndConsume(MOBILE, null, AuthClientType.WEB, CODE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void permitsOnlyOneConcurrentConsumptionForSameMobileCode() throws Exception {
        ParentSmsVerificationService service = service(new MutableClock(), new RecordingSmsSender());
        service.issue(MOBILE, ParentSmsPurpose.REGISTER_OR_LOGIN, AuthClientType.MINIAPP, SOURCE_DIGEST);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> verifyConcurrently(service, ready, start));
            Future<Boolean> second = executor.submit(() -> verifyConcurrently(service, ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean verifyConcurrently(
            ParentSmsVerificationService service,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            service.verifyAndConsume(
                    MOBILE, ParentSmsPurpose.REGISTER_OR_LOGIN, AuthClientType.MINIAPP, CODE);
            return true;
        } catch (ParentSmsVerificationFailedException exception) {
            return false;
        }
    }

    private ParentSmsVerificationService service(Clock clock, ParentSmsSender sender) {
        ParentSmsProperties properties = new ParentSmsProperties();
        properties.setHmacSecret("parent-sms-test-secret-with-at-least-32-bytes");
        ParentSmsVerificationStore store = new InMemoryParentSmsVerificationStore(properties, clock);
        ParentSmsCodeHasher hasher = new ParentSmsCodeHasher(properties);
        return new ParentSmsVerificationService(
                () -> CODE, sender, store, hasher, properties, clock);
    }

    private record SmsDelivery(String mobile, ParentSmsPurpose purpose) {
    }

    private static final class RecordingSmsSender implements ParentSmsSender {
        private final List<SmsDelivery> deliveries = new ArrayList<>();

        @Override
        public void send(String mobile, ParentSmsPurpose purpose, String code) {
            assertThat(code).isEqualTo(CODE);
            deliveries.add(new SmsDelivery(mobile, purpose));
        }

        List<SmsDelivery> deliveries() {
            return List.copyOf(deliveries);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-08T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Shanghai");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

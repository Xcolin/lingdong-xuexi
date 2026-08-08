package com.lingdong.learning.auth.application;

import java.time.LocalDateTime;

/** 仅在签发时返回一次的学生登录二维码内容。 */
public record IssuedStudentQrTicket(Long ticketId, String qrContent, LocalDateTime expiresAt) {
}

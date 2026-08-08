package com.lingdong.learning.auth.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.auth.application.IssuedStudentQrTicket;

import java.time.LocalDateTime;

/** Web 端用于绘制学生登录二维码的短时响应。 */
public record StudentQrTicketResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long ticketId,
        String qrContent,
        LocalDateTime expiresAt
) {
    public static StudentQrTicketResponse from(IssuedStudentQrTicket ticket) {
        return new StudentQrTicketResponse(ticket.ticketId(), ticket.qrContent(), ticket.expiresAt());
    }
}

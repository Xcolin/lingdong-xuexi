package com.lingdong.learning.iam.web;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.user.application.UserAccessApplicationService.ManagedUserCreationResult;

/** Initial student code is returned only by the authorized creation or repair operation. */
public record ManagedUserCreationResponse(@JsonUnwrapped UserResponse user,
        @JsonSerialize(using = ToStringSerializer.class) Long studentId, String initialLoginCode) {
    public static ManagedUserCreationResponse from(ManagedUserCreationResult result) {
        return new ManagedUserCreationResponse(UserResponse.from(result.user()), result.studentId(), result.initialLoginCode());
    }
    @Override public String toString() { return "ManagedUserCreationResponse[initialLoginCode=REDACTED]"; }
}

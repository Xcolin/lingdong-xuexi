package com.lingdong.learning.audit.application;

/** Input used by a system administrator to create one high-risk system task draft. */
public record CreateSystemTaskCommand(Long submitterId, SystemTaskType type, String title,
                                      String description, ImpactScope impactScope) { }

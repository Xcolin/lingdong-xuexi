package com.lingdong.learning.permission.domain;
/** Immutable permission-directory entry. */
public record Permission(Long id, String code, String name, PermissionResourceType resourceType,
                         PermissionClient client, Long parentId, PermissionStatus status, String description) { }

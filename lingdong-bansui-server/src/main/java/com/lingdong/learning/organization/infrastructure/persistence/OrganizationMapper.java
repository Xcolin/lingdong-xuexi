package com.lingdong.learning.organization.infrastructure.persistence;

import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationEffectiveStatus;
import com.lingdong.learning.organization.domain.OrganizationStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Persistence boundary for organization tree nodes.
 */
@Mapper
public interface OrganizationMapper {
    Organization findById(@Param("id") Long id);

    Organization findByIdForUpdate(@Param("id") Long id);

    Organization findByCode(@Param("code") String code);

    boolean existsByCode(@Param("code") String code);

    boolean existsByParentScopeAndName(
            @Param("parentScopeKey") String parentScopeKey,
            @Param("name") String name
    );

    boolean existsByParentScopeAndNameExcludingId(
            @Param("parentScopeKey") String parentScopeKey,
            @Param("name") String name,
            @Param("excludedId") Long excludedId
    );

    int insert(@Param("organization") Organization organization);

    int updateDetails(
            @Param("id") Long id,
            @Param("name") String name,
            @Param("sortOrder") Integer sortOrder,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("adminDivisionCode") String adminDivisionCode
    );

    List<Organization> findSubtreeByPathForUpdate(@Param("path") String path);

    int updateStatusAndEffectiveStatus(
            @Param("id") Long id,
            @Param("status") OrganizationStatus status,
            @Param("effectiveStatus") OrganizationEffectiveStatus effectiveStatus,
            @Param("expectedVersion") Integer expectedVersion
    );

    int updateEffectiveStatus(
            @Param("id") Long id,
            @Param("effectiveStatus") OrganizationEffectiveStatus effectiveStatus,
            @Param("expectedVersion") Integer expectedVersion
    );

    int disableDescendantEffectiveStatus(
            @Param("rootId") Long rootId,
            @Param("rootPath") String rootPath
    );

    int updateLocationAndPath(
            @Param("id") Long id,
            @Param("parentId") Long parentId,
            @Param("parentScopeKey") String parentScopeKey,
            @Param("path") String path,
            @Param("effectiveStatus") OrganizationEffectiveStatus effectiveStatus,
            @Param("expectedVersion") Integer expectedVersion
    );

    int updatePathAndEffectiveStatus(
            @Param("id") Long id,
            @Param("path") String path,
            @Param("effectiveStatus") OrganizationEffectiveStatus effectiveStatus,
            @Param("expectedVersion") Integer expectedVersion
    );

    /** 拖拽同级排序：仅更新顺序与乐观锁版本。 */
    int updateSortOrder(
            @Param("id") Long id,
            @Param("sortOrder") Integer sortOrder,
            @Param("expectedVersion") Integer expectedVersion
    );

    /** 锁定某父级下全部子节点（parentId 为空表示根层级），供同级排序串行化。 */
    List<Organization> findChildrenForUpdate(@Param("parentId") Long parentId);

    long countDeleteReferences(@Param("id") Long id);

    int deleteLeaf(
            @Param("id") Long id,
            @Param("expectedVersion") Integer expectedVersion
    );

    List<Organization> findOperationalSchoolsByOrganizationAdministrator(@Param("userId") Long userId);

    List<Organization> findClassesByOrganizationAdministrator(@Param("userId") Long userId);

    List<Organization> findByIds(@Param("ids") List<Long> ids);

    List<Organization> findByRootPaths(@Param("rootPaths") List<String> rootPaths);

    List<Organization> findAll();
}

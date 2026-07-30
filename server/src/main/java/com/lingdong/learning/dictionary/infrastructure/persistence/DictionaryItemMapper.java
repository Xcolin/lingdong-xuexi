package com.lingdong.learning.dictionary.infrastructure.persistence;

import com.lingdong.learning.dictionary.domain.DictionaryItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence boundary for dictionary items and their single-default invariant. */
@Mapper
public interface DictionaryItemMapper {
    DictionaryItem findById(@Param("id") Long id);

    DictionaryItem findByIdForUpdate(@Param("id") Long id);

    DictionaryItem findByTypeIdAndCode(@Param("typeId") Long typeId, @Param("code") String code);

    boolean existsByTypeIdAndCode(@Param("typeId") Long typeId, @Param("code") String code);

    java.util.List<DictionaryItem> findEnabledByTypeCode(@Param("typeCode") String typeCode);

    int clearDefaultByTypeId(@Param("typeId") Long typeId);

    int update(@Param("dictionaryItem") DictionaryItem dictionaryItem);

    int insert(@Param("dictionaryItem") DictionaryItem dictionaryItem);
}

package com.lingdong.learning.dictionary.infrastructure.persistence;

import com.lingdong.learning.dictionary.domain.DictionaryItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 数据字典项及单一默认项约束的持久化边界。 */
@Mapper
public interface DictionaryItemMapper {
    DictionaryItem findById(@Param("id") Long id);

    DictionaryItem findByIdForUpdate(@Param("id") Long id);

    DictionaryItem findByTypeIdAndCode(@Param("typeId") Long typeId, @Param("code") String code);

    boolean existsByTypeIdAndCode(@Param("typeId") Long typeId, @Param("code") String code);

    java.util.List<DictionaryItem> findEnabledByTypeCode(@Param("typeCode") String typeCode);

    java.util.List<DictionaryItem> findAllByTypeId(@Param("typeId") Long typeId);

    int clearDefaultByTypeId(@Param("typeId") Long typeId);

    int update(@Param("dictionaryItem") DictionaryItem dictionaryItem);

    int insert(@Param("dictionaryItem") DictionaryItem dictionaryItem);
}

package com.lingdong.learning.dictionary.infrastructure.persistence;

import com.lingdong.learning.dictionary.domain.DictionaryType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 数据字典类型的持久化边界。 */
@Mapper
public interface DictionaryTypeMapper {
    DictionaryType findById(@Param("id") Long id);

    DictionaryType findByIdForUpdate(@Param("id") Long id);

    DictionaryType findByCode(@Param("code") String code);

    boolean existsByCode(@Param("code") String code);

    List<String> findEnabledCodes();

    List<DictionaryType> findAll();

    int update(@Param("dictionaryType") DictionaryType dictionaryType);

    int insert(@Param("dictionaryType") DictionaryType dictionaryType);
}

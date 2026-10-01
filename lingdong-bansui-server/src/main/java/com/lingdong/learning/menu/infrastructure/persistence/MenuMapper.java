package com.lingdong.learning.menu.infrastructure.persistence;
import com.lingdong.learning.menu.domain.MenuNode;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface MenuMapper {
    List<MenuNode> findAll();
    List<MenuNode> lockAll();
    int insert(@Param("node") MenuNode node);
    int update(@Param("node") MenuNode node);
    int reorder(@Param("id") Long id,@Param("version") Long version,@Param("sortOrder") int sortOrder);
}

package com.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface DynamicSqlMapper extends BaseMapper<Object> {

    /**
     * 执行动态 SQL 查询
     * @param dynamicSql 动态生成的 SQL
     * @return 查询结果
     */
    @Select("${dynamicSql}")
    List<Map<String, Object>> executeDynamicSql(@Param("dynamicSql") String dynamicSql);
}

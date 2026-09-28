package com.lingdong.learning;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** 直接解析真实 Mapper，不启动应用或连接数据库。 */
class ImportRowMapperMysqlCompatibilityTest {
    @ParameterizedTest
    @CsvSource({
            "importjob/ImportJobRowResultMapper,ImportJobRowMap,findByJobId",
            "studentimport/StudentImportRowMapper,RowMap,findByExecutionIdAndStatus"
    })
    void quotesMysqlReservedColumnInInsertAndSelect(String mapper, String resultMap, String select) throws Exception {
        String resource = "mapper/" + mapper + ".xml";
        Configuration configuration = new Configuration();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        String namespace = configuration.getResultMapNames().stream()
                .filter(name -> name.endsWith("." + resultMap))
                .findFirst().orElseThrow();
        namespace = namespace.substring(0, namespace.lastIndexOf('.'));

        String insertSql = configuration.getMappedStatement(namespace + ".insertBatch")
                .getBoundSql(Map.of("rows", List.of(Map.of("id", 1L, "rowNumber", 2))))
                .getSql();
        assertQuotedRowNumber(insertSql);

        for (boolean filtered : List.of(false, true)) {
            Map<String, Object> parameters = new java.util.HashMap<>();
            parameters.put("jobId", 1L);
            parameters.put("executionId", 1L);
            parameters.put("invalidOnly", filtered);
            parameters.put("status", filtered ? "FAILED" : null);
            parameters.put("limit", 10);
            parameters.put("offset", 0);
            String selectSql = configuration.getMappedStatement(namespace + "." + select)
                    .getBoundSql(parameters).getSql();
            assertQuotedRowNumber(selectSql);
            assertThat(selectSql).containsPattern("ORDER BY\\s+`row_number`");
        }

        assertThat(configuration.getResultMap(namespace + "." + resultMap).getConstructorResultMappings())
                .extracting(ResultMapping::getColumn)
                .contains("row_number")
                .doesNotContain("`row_number`");
    }

    private static void assertQuotedRowNumber(String sql) {
        assertThat(sql).contains("`row_number`");
        assertThat(sql).doesNotContainPattern("(?i)(?<!`)\\brow_number\\b(?!`)");
    }
}

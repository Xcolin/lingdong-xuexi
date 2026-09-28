package com.lingdong.learning.exportjob.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** 导出学生标识必须使用字符串，避免雪花标识被客户端数字精度截断。 */
public class ExportStudentIdDeserializer extends JsonDeserializer<String> {
    @Override public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (String) context.handleUnexpectedToken(String.class, parser);
        }
        return parser.getText();
    }
}

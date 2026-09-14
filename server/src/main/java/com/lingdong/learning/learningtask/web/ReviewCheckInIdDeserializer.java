package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/** 打卡标识必须是 JSON 字符串，禁止数字在客户端发生精度丢失后进入审核。 */
public class ReviewCheckInIdDeserializer extends StdDeserializer<String> {
    public ReviewCheckInIdDeserializer() { super(String.class); }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            throw JsonMappingException.from(parser, "打卡标识必须为字符串");
        }
        return parser.getText();
    }
}

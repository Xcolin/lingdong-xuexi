package com.lingdong.learning.exportjob.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** 责任人标识必须来自 JSON 字符串，避免客户端数字精度丢失。 */
public class InterfaceOwnerIdDeserializer extends JsonDeserializer<String> {
    @Override public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (String) context.handleUnexpectedToken(String.class, parser);
        }
        return parser.getText();
    }
}

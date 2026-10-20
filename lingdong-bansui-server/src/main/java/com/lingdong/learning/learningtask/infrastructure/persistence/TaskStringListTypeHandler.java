package com.lingdong.learning.learningtask.infrastructure.persistence;
import org.apache.ibatis.type.*;
import java.sql.*;
import java.util.List;
/** Explicit JSON string-list adapter for task requirement items. */
public class TaskStringListTypeHandler extends BaseTypeHandler<List<String>> {
 private static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper();
 private String write(List<String> value)throws SQLException{try{return JSON.writeValueAsString(value);}catch(Exception e){throw new SQLException(e);}}
 private List<String> read(String value)throws SQLException{if(value==null||value.isBlank())return List.of();try{return JSON.readValue(value,new com.fasterxml.jackson.core.type.TypeReference<List<String>>(){});}catch(Exception e){throw new SQLException(e);}}
 @Override public void setNonNullParameter(PreparedStatement p,int i,List<String> value,JdbcType type)throws SQLException{p.setString(i,write(value));}
 @Override public List<String> getNullableResult(ResultSet r,String c)throws SQLException{return read(r.getString(c));}
 @Override public List<String> getNullableResult(ResultSet r,int c)throws SQLException{return read(r.getString(c));}
 @Override public List<String> getNullableResult(CallableStatement r,int c)throws SQLException{return read(r.getString(c));}
}

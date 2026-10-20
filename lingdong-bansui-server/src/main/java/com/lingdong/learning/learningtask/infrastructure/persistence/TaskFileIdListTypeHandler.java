package com.lingdong.learning.learningtask.infrastructure.persistence;
import org.apache.ibatis.type.*;
import java.sql.*;
import java.util.List;
/** Explicit JSON id-list adapter for task example attachment ids. */
public class TaskFileIdListTypeHandler extends BaseTypeHandler<List<Long>> {
 private static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper();
 private String write(List<Long> value)throws SQLException{try{return JSON.writeValueAsString(value);}catch(Exception e){throw new SQLException(e);}}
 private List<Long> read(String value)throws SQLException{if(value==null||value.isBlank())return List.of();try{return JSON.readValue(value,new com.fasterxml.jackson.core.type.TypeReference<List<Long>>(){});}catch(Exception e){throw new SQLException(e);}}
 @Override public void setNonNullParameter(PreparedStatement p,int i,List<Long> value,JdbcType type)throws SQLException{p.setString(i,write(value));}
 @Override public List<Long> getNullableResult(ResultSet r,String c)throws SQLException{return read(r.getString(c));}
 @Override public List<Long> getNullableResult(ResultSet r,int c)throws SQLException{return read(r.getString(c));}
 @Override public List<Long> getNullableResult(CallableStatement r,int c)throws SQLException{return read(r.getString(c));}
}

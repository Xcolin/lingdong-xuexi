package com.lingdong.learning.learningtask.infrastructure.persistence;
import org.apache.ibatis.type.*;
import java.sql.*;
import java.util.List;
/** Explicit JSON weekday adapter; null legacy rows remain daily-compatible. */
public class TaskWeekdaysTypeHandler extends BaseTypeHandler<List<Integer>> {
 private static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper();
 private String write(List<Integer> value)throws SQLException{try{return JSON.writeValueAsString(value);}catch(Exception e){throw new SQLException(e);}}
 private List<Integer> read(String value)throws SQLException{if(value==null||value.isBlank())return List.of();try{return JSON.readValue(value,new com.fasterxml.jackson.core.type.TypeReference<List<Integer>>(){});}catch(Exception e){throw new SQLException(e);}}
 @Override public void setNonNullParameter(PreparedStatement p,int i,List<Integer> value,JdbcType type)throws SQLException{p.setString(i,write(value));}
 @Override public List<Integer> getNullableResult(ResultSet r,String c)throws SQLException{return read(r.getString(c));}
 @Override public List<Integer> getNullableResult(ResultSet r,int c)throws SQLException{return read(r.getString(c));}
 @Override public List<Integer> getNullableResult(CallableStatement r,int c)throws SQLException{return read(r.getString(c));}
}

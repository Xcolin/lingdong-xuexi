package com.lingdong.learning.training;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Only the three training tables are writable here; primary school/class membership is immutable. */
@Repository
public class TrainingStore {
 private final NamedParameterJdbcTemplate jdbc;
 public TrainingStore(JdbcTemplate jdbc){this.jdbc=new NamedParameterJdbcTemplate(jdbc);}
 public List<Map<String,Object>> rows(String sql,Map<String,?> args){return jdbc.query(sql,args,(rs,n)->row(rs));}
 public Map<String,Object> one(String sql,Map<String,?> args){var rows=rows(sql,args);return rows.isEmpty()?null:rows.get(0);}
 public long count(String sql,Map<String,?> args){Long value=jdbc.queryForObject(sql,args,Long.class);return value==null?0:value;}
 public int update(String sql,Map<String,?> args){return jdbc.update(sql,args);}
 private Map<String,Object> row(ResultSet rs)throws SQLException{
  var value=new LinkedHashMap<String,Object>();var meta=rs.getMetaData();
  for(int i=1;i<=meta.getColumnCount();i++){
   String raw=meta.getColumnLabel(i).toLowerCase(Locale.ROOT);var key=new StringBuilder();boolean upper=false;
   for(char c:raw.toCharArray()){if(c=='_')upper=true;else{key.append(upper?Character.toUpperCase(c):c);upper=false;}}
   String name=key.toString();Object field=rs.getObject(i);
   if(field!=null&&(name.equals("id")||name.endsWith("Id")||name.endsWith("By")))field=field.toString();
   if(field instanceof Timestamp time)field=time.toLocalDateTime();value.put(name,field);
  }
  return value;
 }
}

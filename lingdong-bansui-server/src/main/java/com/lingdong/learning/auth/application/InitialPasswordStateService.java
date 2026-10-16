package com.lingdong.learning.auth.application;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service
public class InitialPasswordStateService {
 private final JdbcTemplate jdbc;
 public InitialPasswordStateService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public boolean required(Long id){return !jdbc.queryForList("select user_id from auth_initial_password_state where user_id=? and change_required=true",Long.class,id).isEmpty();}
 public void require(Long id){jdbc.update("delete from auth_initial_password_state where user_id=?",id);jdbc.update("insert into auth_initial_password_state(user_id,change_required,updated_at) values(?,true,current_timestamp)",id);}
 public void clear(Long id){jdbc.update("update auth_initial_password_state set change_required=false,updated_at=current_timestamp where user_id=?",id);}
}

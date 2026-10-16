package com.lingdong.learning.student.application.transfer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
class TransferMaterialReservationTest {
 @Test void sharedRowLockProtectsAssociationAgainstDraftRetirement() throws Exception {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:transfer_material_lock;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=4000","sa","");var j=new JdbcTemplate(ds);
  j.execute("CREATE TABLE sys_file(id BIGINT PRIMARY KEY,uploader_id BIGINT,module_code VARCHAR(64),file_category VARCHAR(64),status VARCHAR(20),updated_at TIMESTAMP)");
  j.execute("CREATE TABLE sys_file_relation(file_id BIGINT,status VARCHAR(20))");
  j.update("INSERT INTO sys_file VALUES(41,31,'STUDENT_TRANSFER','MATERIAL','AVAILABLE',CURRENT_TIMESTAMP),(42,31,'STUDENT_TRANSFER','MATERIAL','AVAILABLE',CURRENT_TIMESTAMP),(43,32,'STUDENT_TRANSFER','MATERIAL','AVAILABLE',CURRENT_TIMESTAMP)");
  var repo=new TransferApplicationRepository(j,new com.fasterxml.jackson.databind.ObjectMapper());var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  assertThrows(IllegalArgumentException.class,()->tx.execute(s->{repo.retireUnusedMaterial(31,43);return null;}));
  var locked=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  try {
   var association=pool.submit(()->tx.execute(s->{repo.lockUnusedMaterials(31,java.util.List.of(41L));locked.countDown();try{if(!release.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("fixture timeout");}catch(InterruptedException e){throw new IllegalStateException(e);}j.update("INSERT INTO sys_file_relation VALUES(41,'ACTIVE')");return null;}));
   assertTrue(locked.await(3,java.util.concurrent.TimeUnit.SECONDS));
   var retirement=pool.submit(()->tx.execute(s->{repo.retireUnusedMaterial(31,41);return null;}));
   release.countDown();association.get(4,java.util.concurrent.TimeUnit.SECONDS);
   assertThrows(java.util.concurrent.ExecutionException.class,()->retirement.get(4,java.util.concurrent.TimeUnit.SECONDS));
   assertEquals("AVAILABLE",j.queryForObject("SELECT status FROM sys_file WHERE id=41",String.class));
   tx.execute(s->{repo.retireUnusedMaterial(31,42);repo.retireUnusedMaterial(31,42);return null;});
   assertEquals("RETIRED",j.queryForObject("SELECT status FROM sys_file WHERE id=42",String.class));
   assertThrows(IllegalArgumentException.class,()->tx.execute(s->{repo.lockUnusedMaterials(31,java.util.List.of(42L));return null;}));
  }finally{release.countDown();pool.shutdownNow();}
 }
}

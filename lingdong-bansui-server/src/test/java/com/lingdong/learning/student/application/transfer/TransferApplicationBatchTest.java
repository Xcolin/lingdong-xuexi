package com.lingdong.learning.student.application.transfer;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.mockito.Mockito.*;import static org.junit.jupiter.api.Assertions.*;
class TransferApplicationBatchTest {
 @Test void individualTransactionFailureDoesNotAbortOtherDueApplications(){
  var repo=mock(TransferApplicationRepository.class);var executor=mock(TransferApplicationExecutor.class);var clock=Clock.fixed(Instant.parse("2026-10-14T00:00:00Z"),ZoneOffset.UTC);
  when(repo.dueIds(LocalDate.of(2026,10,14),100)).thenReturn(java.util.List.of(1L,2L,3L));
  when(executor.execute(1)).thenThrow(new IllegalStateException("fixture failure"));when(executor.execute(2)).thenReturn(TransferApplicationExecutor.Result.COMPLETED);when(executor.execute(3)).thenReturn(TransferApplicationExecutor.Result.BLOCKED);
  var batch=new TransferApplicationBatchService(repo,executor,clock);var result=batch.processDue();
  assertEquals(1,result.completed());assertEquals(1,result.blocked());assertEquals(1,result.failed());verify(executor).execute(3);
 }
}

package com.lingdong.learning.student.application.transfer;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalDate;

/** No outer transaction: each proposal commits or rolls back through the injected executor proxy. */
@Service
public class TransferApplicationBatchService {
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(TransferApplicationBatchService.class);
 private final TransferApplicationRepository repository;private final TransferApplicationExecutor executor;private final Clock clock;
 public TransferApplicationBatchService(TransferApplicationRepository repository,TransferApplicationExecutor executor,Clock clock){this.repository=repository;this.executor=executor;this.clock=clock;}
 public record Result(int completed,int blocked,int skipped,int failed){}
 public Result processDue(){
  int completed=0,blocked=0,skipped=0,failed=0;
  for(long id:repository.dueIds(LocalDate.now(clock),100)){
   try{switch(executor.execute(id)){case COMPLETED->completed++;case BLOCKED->blocked++;case SKIPPED->skipped++;}}
   catch(RuntimeException failure){failed++;LOG.warn("转学到期执行失败，将在后续批次重试，申请标识={}，异常类型={}",id,failure.getClass().getSimpleName());}
  }
  return new Result(completed,blocked,skipped,failed);
 }
}

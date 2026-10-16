package com.lingdong.learning.student.application.transfer;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class TransferApplicationTest {
    private final LocalDate day=LocalDate.of(2026,10,14);
    private TransferApplication pending(){return TransferApplication.submit(1,2,3,"目标学校","家庭搬迁",day,List.of(),day);}
    @Test void submissionIsOnlyImmutableProposal(){var p=pending();assertEquals(TransferApplication.Status.PENDING,p.status());assertNull(p.completedAt());assertEquals(2,p.sourceInstitutionId());}
    @Test void reviewScopeAndSelfReview(){assertThrows(IllegalArgumentException.class,()->pending().review(3,2,true));assertThrows(IllegalArgumentException.class,()->pending().review(4,5,true));}
    @Test void transitionsAndDueDate(){var p=pending();var a=p.review(4,2,true);assertThrows(IllegalStateException.class,()->a.complete(day.minusDays(1).atStartOfDay()));var c=a.complete(day.atStartOfDay());assertEquals(TransferApplication.Status.COMPLETED,c.status());assertThrows(IllegalStateException.class,()->c.complete(day.atStartOfDay()));assertThrows(IllegalStateException.class,()->a.withdraw(3));assertThrows(IllegalStateException.class,()->p.review(4,2,false).review(4,2,true));}
    @Test void withdrawOnlyApplicant(){assertThrows(IllegalArgumentException.class,()->pending().withdraw(4));assertThrows(IllegalStateException.class,()->pending().withdraw(3).review(4,2,true));}
    @Test void invalidInput(){assertThrows(IllegalArgumentException.class,()->TransferApplication.submit(1,2,3,"目标","原因",day.minusDays(1),List.of(),day));for(String reason:List.of(" ","字".repeat(201)))assertThrows(IllegalArgumentException.class,()->TransferApplication.submit(1,2,3,"目标",reason,day,List.of(),day));assertThrows(IllegalArgumentException.class,()->TransferApplication.submit(1,2,3,"目标","原因",day,List.of(1L,2L,3L,4L),day));assertThrows(IllegalArgumentException.class,()->TransferApplication.submit(1,2,3,"目标","原因",day,List.of(1L,1L),day));}
}

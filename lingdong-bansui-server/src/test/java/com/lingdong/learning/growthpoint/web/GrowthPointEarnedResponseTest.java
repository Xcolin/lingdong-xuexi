package com.lingdong.learning.growthpoint.web;
import com.lingdong.learning.growthpoint.application.GrowthPointAccountView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class GrowthPointEarnedResponseTest {
 @Test void earnedFieldsReachJsonAndStudentIdRetainsPrecision() throws Exception {
 var view=new GrowthPointAccountView(2109841585653940225L,"child",130,85,null,20,45);
 var json=new ObjectMapper().valueToTree(GrowthPointAccountResponse.from(view));
 assertThat(json.get("weekEarnedPoints").longValue()).isEqualTo(20);
 assertThat(json.get("monthEarnedPoints").longValue()).isEqualTo(45);
 assertThat(json.get("availablePoints").longValue()).isEqualTo(85);
 assertThat(json.get("studentId").isTextual()).isTrue();
 assertThat(json.get("studentId").textValue()).isEqualTo("2109841585653940225");
 }
}

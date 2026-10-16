package com.lingdong.learning.growthpoint.web;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.lingdong.learning.growthpoint.application.AddGrowthReviewSupplementCommand;
import com.lingdong.learning.growthpoint.domain.GrowthReviewSupplementType;
import java.util.List;
public record SaveDailyReflectionRequest(@NotBlank @Size(max=200) String insight,@NotBlank @Size(max=200) String difficulty,@NotBlank @Size(max=200) String nextPlan) {
 public List<AddGrowthReviewSupplementCommand> toCommands(){return List.of(new AddGrowthReviewSupplementCommand(GrowthReviewSupplementType.INSIGHT,insight),new AddGrowthReviewSupplementCommand(GrowthReviewSupplementType.STRENGTH_WEAKNESS,difficulty),new AddGrowthReviewSupplementCommand(GrowthReviewSupplementType.NEXT_PLAN,nextPlan));}
}

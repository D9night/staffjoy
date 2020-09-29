package xyz.staffjoy.company.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 员工以及员工所属的团队信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerOfList {
    private String userId;
    @Builder.Default
    private List<TeamDto> teams = new ArrayList<TeamDto>();
}

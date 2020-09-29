package xyz.staffjoy.bot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

/**
 * 问候短信请求
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GreetingRequest {
    @NotBlank
    private String userId;
}

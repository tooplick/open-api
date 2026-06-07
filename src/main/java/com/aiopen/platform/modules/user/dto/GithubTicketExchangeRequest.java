package com.aiopen.platform.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GithubTicketExchangeRequest {

    @NotBlank(message = "ticket is required")
    private String ticket;
}

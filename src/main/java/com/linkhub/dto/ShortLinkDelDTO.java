package com.linkhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.URL;


@Data
public class ShortLinkDelDTO {
    @NotBlank
    @Pattern(
            regexp = "^[a-zA-Z0-9_-]{4,16}$"
    )
    private String shortCode;

}

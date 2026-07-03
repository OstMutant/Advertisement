package org.ost.platform.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignUpDto {

    public static final int NAME_MAX_LENGTH     = 100;
    public static final int EMAIL_MAX_LENGTH    = 254;
    public static final int PASSWORD_MIN_LENGTH = 6;
    public static final int PASSWORD_MAX_LENGTH = 255;

    @NotBlank
    @Size(min = 1, max = NAME_MAX_LENGTH)
    private String name;

    @NotBlank
    @Email
    @Size(max = EMAIL_MAX_LENGTH)
    private String email;

    @NotBlank
    @Size(min = PASSWORD_MIN_LENGTH, max = PASSWORD_MAX_LENGTH)
    private String password;
}

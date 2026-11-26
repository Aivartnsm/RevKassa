package ee.revkassa.controller.admin.membershiplevel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * A new level is active, goes last in the order and has no monthly fee until the admin sets one.
 */
@Data
public class MembershipLevelCreateRequestDto implements Serializable {

    public static final int MAX_NAME_LENGTH = 50;

    @NotBlank
    @Size(max = MAX_NAME_LENGTH)
    private String name;
}

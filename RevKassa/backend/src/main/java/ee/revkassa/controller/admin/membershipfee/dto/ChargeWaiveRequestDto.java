package ee.revkassa.controller.admin.membershipfee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * The note is required and the member sees it in their history ("Liikmemaksu tühistus · november 2026 · <note>").
 */
@Data
public class ChargeWaiveRequestDto implements Serializable {

    public static final int MAX_NOTE_LENGTH = 500;

    @NotBlank
    @Size(max = MAX_NOTE_LENGTH)
    private String note;
}

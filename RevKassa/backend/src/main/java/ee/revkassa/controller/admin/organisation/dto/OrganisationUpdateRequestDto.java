package ee.revkassa.controller.admin.organisation.dto;

import ee.revkassa.infrastructure.validation.ValidIban;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class OrganisationUpdateRequestDto implements Serializable {

    // Payee name exactly as the bank account holder is named, e.g. "MTÜ Näidisklubi"
    @NotBlank
    @Size(max = 100)
    private String name;

    // Spaces and lower case are allowed here; saved without spaces, in capitals
    @NotNull
    @ValidIban
    private String iban;

    // The version the admin loaded; an older one means someone else saved in between (409)
    @NotNull
    private Long version;
}

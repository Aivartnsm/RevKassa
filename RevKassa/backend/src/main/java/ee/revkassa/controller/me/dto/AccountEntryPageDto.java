package ee.revkassa.controller.me.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountEntryPageDto implements Serializable {
    // Newest first
    private List<AccountEntryDto> entries;
    private int page;
    // true: "Laadi veel" asks for page + 1
    private boolean hasMore;
}

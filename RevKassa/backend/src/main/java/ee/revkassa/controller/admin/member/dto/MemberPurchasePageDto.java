package ee.revkassa.controller.admin.member.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberPurchasePageDto implements Serializable {
    // Newest first
    private List<MemberPurchaseDto> purchases;
    private int page;
    // true: "Laadi veel" asks for page + 1
    private boolean hasMore;
}

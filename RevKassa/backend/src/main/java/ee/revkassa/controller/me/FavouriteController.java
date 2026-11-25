package ee.revkassa.controller.me;

import ee.revkassa.controller.me.dto.FavouritesDto;
import ee.revkassa.controller.me.dto.FavouritesUpdateRequestDto;
import ee.revkassa.infrastructure.error.ApiError;
import ee.revkassa.infrastructure.security.MemberPrincipal;
import ee.revkassa.service.FavouriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/favourites")
@RequiredArgsConstructor
public class FavouriteController {

    private final FavouriteService favouriteService;

    @GetMapping
    @Operation(summary = "Your favourite product ids in your order",
            description = "Any logged-in member (PIN or password), only their own list. May include hidden products: "
                    + "show only the ids that are in the product list.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK, productIds in order (empty when none)"),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Member deactivated (112)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public FavouritesDto getFavourites(@Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return favouriteService.getFavourites(principal.memberId());
    }

    @PutMapping
    @Operation(summary = "Replace your favourites with this ordered list",
            description = "Any logged-in member, only their own list. The order of productIds is the new order; an "
                    + "empty list clears it. Returns the saved list. Needs the X-XSRF-TOKEN header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Saved, productIds in the new order"),
            @ApiResponse(responseCode = "400", description = "productIds missing, a null or non-positive id, the same id "
                    + "twice or more than 100 ids (103); a product does not exist (130)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Session missing or expired (errorCode 115)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Member deactivated (112) or CSRF token missing (118)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public FavouritesDto replaceFavourites(@RequestBody @Valid FavouritesUpdateRequestDto favouritesUpdateRequest,
                                           @Parameter(hidden = true) @AuthenticationPrincipal MemberPrincipal principal) {
        return favouriteService.replaceFavourites(principal.memberId(), favouritesUpdateRequest);
    }
}

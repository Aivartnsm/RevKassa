package ee.revkassa.service;

import ee.revkassa.controller.me.dto.FavouritesDto;
import ee.revkassa.controller.me.dto.FavouritesUpdateRequestDto;
import ee.revkassa.infrastructure.error.Error;
import ee.revkassa.infrastructure.exception.BadRequestException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.persistence.memberfavourite.MemberFavourite;
import ee.revkassa.persistence.memberfavourite.MemberFavouriteRepository;
import ee.revkassa.persistence.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A member's starred products in their own order. Saving replaces the whole list, so positions are always 0..n-1.
 * Hidden products stay in the list (the buying view leaves them out) and come back if the product is shown again.
 */
@Service
@RequiredArgsConstructor
public class FavouriteService {

    private final MemberFavouriteRepository memberFavouriteRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final Clock clock;

    public FavouritesDto getFavourites(Long memberId) {
        return new FavouritesDto(memberFavouriteRepository.findProductIds(memberId));
    }

    @Transactional
    public FavouritesDto replaceFavourites(Long memberId, FavouritesUpdateRequestDto request) {
        List<Long> productIds = request.getProductIds();
        Member member = getValidMemberForUpdateBy(memberId);
        validateProductsExist(productIds);
        memberFavouriteRepository.deleteByMemberId(memberId);
        memberFavouriteRepository.saveAll(createFavourites(member, productIds));
        return getFavourites(memberId);
    }

    // Two saves from two devices run one after another instead of colliding on the same positions
    private Member getValidMemberForUpdateBy(Long memberId) {
        return memberRepository.findForUpdate(memberId)
                .orElseThrow(() -> new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND));
    }

    private void validateProductsExist(List<Long> productIds) {
        if (!productIds.isEmpty() && productRepository.countByIds(productIds) != productIds.size()) {
            throw new BadRequestException(Error.UNKNOWN_PRODUCT);
        }
    }

    private List<MemberFavourite> createFavourites(Member member, List<Long> productIds) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<MemberFavourite> favourites = new ArrayList<>();
        for (int position = 0; position < productIds.size(); position++) {
            favourites.add(createFavourite(member, productIds.get(position), position, now));
        }
        return favourites;
    }

    private MemberFavourite createFavourite(Member member, Long productId, int position, OffsetDateTime now) {
        MemberFavourite favourite = new MemberFavourite();
        favourite.setMember(member);
        favourite.setProduct(productRepository.getReferenceById(productId));
        favourite.setPosition(position);
        favourite.setCreatedAt(now);
        return favourite;
    }
}

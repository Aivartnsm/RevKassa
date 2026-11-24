package ee.revkassa.infrastructure.security;

import ee.revkassa.persistence.member.Member;
import ee.revkassa.persistence.member.MemberRepository;
import ee.revkassa.testdata.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
@Import(TestData.class)
class PinServiceTest {

    // docs/database/3_import.sql: Kaspar Kask, PIN 111111, made with the dev key from test config
    private static final String KASPAR_PIN_HMAC = "022a92e11a89a2f073dbbe4578920272870cb113a551e1457781545bc05030b7";

    @Autowired
    private PinService pinService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TestData testData;

    @MockitoBean
    private RandomGenerator randomGenerator;

    @Test
    void hash_matchesHashInImportScript() {
        String hash = pinService.hash("111111");

        assertThat(hash).isEqualTo(KASPAR_PIN_HMAC);
    }

    @Test
    void hash_samePinGivesSameHashAndDifferentPinDifferentHash() {
        assertThat(pinService.hash("482915")).isEqualTo(pinService.hash("482915"));
        assertThat(pinService.hash("482915")).isNotEqualTo(pinService.hash("482916"));
    }

    @Test
    void generateUniquePin_padsSmallNumberWithLeadingZeros() {
        when(randomGenerator.nextInt(anyInt())).thenReturn(42);

        String pin = pinService.generateUniquePin();

        assertThat(pin).isEqualTo("000042");
    }

    @Test
    void generateUniquePin_triesAgainWhenPinIsTaken() {
        saveMemberWithPin("135790");
        when(randomGenerator.nextInt(anyInt())).thenReturn(135790, 246801);

        String pin = pinService.generateUniquePin();

        assertThat(pin).isEqualTo("246801");
    }

    @Test
    void generateUniquePin_asksForNumbersCoveringAllSixDigitPins() {
        when(randomGenerator.nextInt(1_000_000)).thenReturn(999_999);

        String pin = pinService.generateUniquePin();

        assertThat(pin).isEqualTo("999999");
    }

    @Test
    void findByPin_findsMemberWithThatPin() {
        Member saved = saveMemberWithPin("135790");

        Optional<Member> found = pinService.findByPin("135790");

        assertThat(found).get().extracting(Member::getId).isEqualTo(saved.getId());
    }

    @Test
    void findByPin_returnsEmptyForUnknownPin() {
        Optional<Member> found = pinService.findByPin("864209");

        assertThat(found).isEmpty();
    }

    @Test
    void constructor_refusesBlankSecret() {
        assertThatThrownBy(() -> new PinService(memberRepository, mock(RandomGenerator.class), " "))
                .isInstanceOf(IllegalStateException.class);
    }

    private Member saveMemberWithPin(String pin) {
        return testData.member("pin-test-" + pin + "@example.org", pinService.hash(pin));
    }
}

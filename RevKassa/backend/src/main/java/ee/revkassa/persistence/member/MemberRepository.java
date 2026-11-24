package ee.revkassa.persistence.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    @Query("select m from Member m where m.pinHmac = :pinHmac")
    Optional<Member> findByPinHmac(String pinHmac);

    @Query("select (count(m) > 0) from Member m where m.pinHmac = :pinHmac")
    boolean existsByPinHmac(String pinHmac);

}

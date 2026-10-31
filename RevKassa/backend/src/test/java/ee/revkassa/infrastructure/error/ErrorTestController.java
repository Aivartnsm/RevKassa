package ee.revkassa.infrastructure.error;

import ee.revkassa.infrastructure.exception.DataNotFoundException;
import ee.revkassa.infrastructure.exception.ForbiddenException;
import ee.revkassa.infrastructure.exception.PrimaryKeyNotFoundException;
import jakarta.validation.Valid;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
public class ErrorTestController {

    @GetMapping("/test/primary-key-not-found")
    public void primaryKeyTest() {
        throw new PrimaryKeyNotFoundException(Error.ENTRY_NOT_FOUND);
    }

    @GetMapping("/test/data-not-found")
    public void dataNotFoundTest() {
        throw new DataNotFoundException(Error.DATA_NOT_FOUND);
    }

    @GetMapping("/test/forbidden")
    public void forbiddenExceptionTest() {
        throw new ForbiddenException(Error.INVALID_REQUEST);
    }

    @GetMapping("/test/runtime-exception")
    public void internalServerErrorTest() {
        throw new RuntimeException("Here is some SQL query exception");
    }

    @GetMapping("/test/optimistic-locking")
    public void objectOptimisticLockExceptionTest() {
        throw new ObjectOptimisticLockingFailureException("Data has changed, reload page", new RuntimeException("Here is some SQL query exception"));
    }

    @PostMapping("/test/dto-test")
    public void requestDtoErrorTest(@Valid @RequestBody ErrorTestRequestDto errorTestRequestDto) {

    }

    @GetMapping("/test/clock-and-money-format-test")
    public ClockAndMoneyFormatDto clockAndMoneyFormatTest() {
        ClockAndMoneyFormatDto clockAndMoneyFormat = new ClockAndMoneyFormatDto();
        clockAndMoneyFormat.setDateTime(OffsetDateTime.parse("2026-10-28T18:42:00+02:00"));
        clockAndMoneyFormat.setMoney(-1250);
        return clockAndMoneyFormat;
    }

}

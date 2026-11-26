package ee.revkassa.infrastructure.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Error {

    ENTRY_NOT_FOUND("Record not found", 101),
    DATA_NOT_FOUND("Data not found", 102),
    INVALID_DATA_IN_FIELDS("Validate fields", 103),
    INVALID_REQUEST("Invalid request. Try again", 104),
    DATA_HAS_CHANGED_RELOAD_REQUIRED("Oops, someone has changed the data, try reloading the page", 105),
    WRONG_PIN("Wrong PIN.", 111),
    ACCOUNT_NOT_ACTIVE("Account is not active.", 112),
    TOO_MANY_WRONG_PINS("Too many wrong PINs. Try again later.", 113),
    WRONG_EMAIL_OR_PASSWORD("Wrong email or password.", 114),
    SESSION_MISSING("Session expired or missing.", 115),
    DEVICE_NOT_TRUSTED("PIN login is only allowed on a registered device.", 116),
    TOO_MANY_FAILED_LOGINS("Too many failed logins. Try again later.", 117),
    ACCESS_DENIED("Access denied.", 118),
    DEVICE_ALREADY_REVOKED("Device is already revoked.", 119),
    DEVICE_ALREADY_TRUSTED("This device is already registered.", 120),
    PASSWORD_LINK_INVALID("Password link is invalid or expired.", 121),
    TOO_MANY_PASSWORD_RESETS("Too many password reset requests. Try again later.", 122),
    EMAIL_NOT_SENT("Email could not be sent. Try again later.", 123),
    WRONG_CURRENT_PASSWORD("Current password is wrong.", 124),
    DUPLICATE_PRODUCT_IN_STOCKTAKE("A product is counted more than once.", 125),
    PRODUCT_NOT_AVAILABLE("A product is no longer for sale.", 126),
    PRICES_CHANGED("Prices have changed. Check the new total and confirm again.", 127),
    PURCHASE_ALREADY_REVERSED("This purchase has already been undone.", 128),
    UNDO_WINDOW_PASSED("A purchase can only be undone within 10 minutes.", 129),
    UNKNOWN_PRODUCT("A product does not exist.", 130),
    PAYMENT_DETAILS_NOT_SET("Payment details have not been set up yet.", 131),
    PAYMENT_ALREADY_DECIDED("This payment has already been confirmed or rejected.", 132),
    EMAIL_TAKEN("This email is already used by another member.", 133),
    LAST_ADMIN("At least one active admin must remain.", 134),
    LEVEL_NAME_TAKEN("A membership level with this name already exists.", 135),
    LEVEL_IN_USE("This membership level is in use and cannot be deleted. Deactivate it instead.", 136),
    LAST_ACTIVE_LEVEL("At least one membership level must stay active.", 137),
    LEVEL_NOT_ACTIVE("This membership level is not active.", 138),
    FEE_MONTH_PASSED("Fees can only be set from the current month on.", 139),
    FEE_MONTH_ALREADY_CHARGED("This month's fee has already been charged for this level.", 140),
    FEE_ALREADY_WAIVED("This fee has already been waived.", 141),
    DATE_RANGE_INVALID("The start date must not be after the end date.", 142),
    WRONG_CURRENT_PIN("Current PIN is wrong.", 143),
    PIN_NOT_AVAILABLE("This PIN cannot be used. Choose another one.", 144),
    NEW_PIN_SAME_AS_CURRENT("The new PIN must differ from the current one.", 145),
    MEMBER_NOT_IN_DEBT("This member has no debt to invoice.", 146),
    INVOICE_ALREADY_SENT("This invoice has already been sent.", 147),
    UNEXPECTED_ERROR("Something went wrong, try again", 500);

    private final String message;
    private final Integer errorCode;
}

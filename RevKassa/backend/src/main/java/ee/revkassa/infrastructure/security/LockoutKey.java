package ee.revkassa.infrastructure.security;

/**
 * Whose wrong attempts are counted together: PIN attempts per trusted device, password logins per client IP,
 * wrong current passwords (password change) and PIN change attempts per member. Different types never share a counter.
 */
public record LockoutKey(LockoutTypeEnum type, String id) {

    public static LockoutKey forDevice(Long deviceId) {
        return new LockoutKey(LockoutTypeEnum.PIN_LOGIN, String.valueOf(deviceId));
    }

    public static LockoutKey forIp(String clientIp) {
        return new LockoutKey(LockoutTypeEnum.PASSWORD_LOGIN, clientIp);
    }

    public static LockoutKey forPasswordChange(Long memberId) {
        return new LockoutKey(LockoutTypeEnum.PASSWORD_CHANGE, String.valueOf(memberId));
    }

    public static LockoutKey forPinChange(Long memberId) {
        return new LockoutKey(LockoutTypeEnum.PIN_CHANGE, String.valueOf(memberId));
    }
}

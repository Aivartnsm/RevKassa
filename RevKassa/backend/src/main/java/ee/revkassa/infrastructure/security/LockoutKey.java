package ee.revkassa.infrastructure.security;

/**
 * Whose wrong attempts are counted together: PIN attempts per trusted device, password attempts per client IP.
 * The two never share a counter.
 */
public record LockoutKey(LoginMethodEnum loginMethod, String id) {

    public static LockoutKey forDevice(Long deviceId) {
        return new LockoutKey(LoginMethodEnum.PIN, String.valueOf(deviceId));
    }

    public static LockoutKey forIp(String clientIp) {
        return new LockoutKey(LoginMethodEnum.PASSWORD, clientIp);
    }
}

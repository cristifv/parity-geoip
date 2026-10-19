package parity.geoip.geo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Parses IP address literals. Never resolves a host name, and rejects the old IPv4 forms
 * that {@link InetAddress} still accepts ({@code 127.1}, octets with leading zeros).
 */
public final class IpAddresses {

    private static final Pattern IPV4 =
            Pattern.compile("((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");

    /** Hex groups and colons, optionally ending in a dotted IPv4 address. No zone id. */
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:]*:[0-9A-Fa-f:.]*");

    private IpAddresses() {
    }

    /** The address, or IllegalArgumentException if the text isn't an IPv4 or IPv6 literal. */
    public static InetAddress parse(String text) {
        if (text == null || !(IPV4.matcher(text).matches() || IPV6.matcher(text).matches())) {
            throw new IllegalArgumentException("not an IP address: " + text);
        }
        try {
            // A literal: InetAddress only checks it, no lookup.
            return InetAddress.getByName(text);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("not an IP address: " + text);
        }
    }
}

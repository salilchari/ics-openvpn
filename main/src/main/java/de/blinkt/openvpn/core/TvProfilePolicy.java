package de.blinkt.openvpn.core;

import de.blinkt.openvpn.VpnProfile;

/** GPL-2.0; see doc/LICENSE.txt. Profile preparation, NOT an OS kill switch. */
public final class TvProfilePolicy {
    private TvProfilePolicy() {}

    public static VpnProfile createCopy(VpnProfile source) {
        // Unparsed options can override routing. Require review rather than remove them.
        if (source.mUseCustomConfig && hasText(source.mCustomConfigOptions))
            throw new IllegalArgumentException("Review custom profile options before preparing a TV copy.");
        if (source.mConnections != null) {
            for (Connection connection : source.mConnections) {
                if (connection != null && connection.mUseCustomConfig
                        && hasText(connection.mCustomConfiguration))
                    throw new IllegalArgumentException("Review custom connection options before preparing a TV copy.");
            }
        }
        VpnProfile copy = source.copy(source.mName + " (TV)");
        copy.mUseDefaultRoute = true;
        copy.mUseDefaultRoutev6 = true;
        copy.mRoutenopull = false;
        copy.mAllowLocalLAN = false;
        copy.mAllowAppVpnBypass = false;
        copy.mAllowedAppsVpn.clear();
        copy.mAllowedAppsVpnAreDisallowed = true;
        copy.mExcludedRoutes = "";
        copy.mExcludedRoutesv6 = "";
        copy.mBlockUnusedAddressFamilies = true;
        copy.mPersistTun = true;
        copy.mConnectRetryMax = "-1";
        copy.mConnectRetry = "2";
        copy.mConnectRetryMaxTime = "60";
        // Keep the user's authentication, transport, TLS and DNS configuration.
        // DNS and server-pushed routes still require live leak tests.
        return copy;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

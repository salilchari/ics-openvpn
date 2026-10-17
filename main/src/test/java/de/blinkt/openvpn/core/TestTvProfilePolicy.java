package de.blinkt.openvpn.core;

import android.os.Build;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import de.blinkt.openvpn.VpnProfile;
import static org.junit.Assert.*;

@Config(sdk = Build.VERSION_CODES.O_MR1)
@RunWith(RobolectricTestRunner.class)
public class TestTvProfilePolicy {
    @Test public void copyPreservesOriginalAndGeneratesFullRoutes() {
        VpnProfile original = new VpnProfile("Home");
        original.mUseDefaultRoute = false;
        original.mAllowLocalLAN = true;
        original.mAllowedAppsVpn.add("example.streaming.app");
        VpnProfile copy = TvProfilePolicy.createCopy(original);
        assertNotEquals(original.getUUIDString(), copy.getUUIDString());
        assertFalse(original.mUseDefaultRoute);
        assertTrue(original.mAllowLocalLAN);
        assertTrue(original.mAllowedAppsVpn.contains("example.streaming.app"));
        assertTrue(copy.mAllowedAppsVpn.isEmpty());
        String config = copy.getConfigFile(RuntimeEnvironment.application, false);
        assertTrue(config.contains("route 0.0.0.0 0.0.0.0 vpn_gateway\n"));
        assertTrue(config.contains("route-ipv6 ::/0\n"));
        assertTrue(config.contains("persist-tun\n"));
        assertTrue(config.contains("connect-retry 2 60\n"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesCustomRoutingOverride() {
        VpnProfile profile = new VpnProfile("Home");
        profile.mUseCustomConfig = true;
        profile.mCustomConfigOptions = "route 0.0.0.0 0.0.0.0 net_gateway";
        TvProfilePolicy.createCopy(profile);
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesCustomConnectionOverride() {
        VpnProfile profile = new VpnProfile("Home");
        profile.mConnections[0].mUseCustomConfig = true;
        profile.mConnections[0].mCustomConfiguration = "route-nopull";
        TvProfilePolicy.createCopy(profile);
    }
}

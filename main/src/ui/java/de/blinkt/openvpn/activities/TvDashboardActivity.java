package de.blinkt.openvpn.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;

import de.blinkt.openvpn.LaunchVPN;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.Preferences;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.TvProfilePolicy;
import de.blinkt.openvpn.core.VpnStatus;

/** GPL-2.0; see doc/LICENSE.txt. Remote-friendly development dashboard. */
public class TvDashboardActivity extends Activity implements VpnStatus.StateListener {
    private LinearLayout content;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(48), dp(24), dp(48), dp(24));
        content.setBackgroundColor(Color.rgb(16, 24, 36));
        scroll.addView(content);
        setContentView(scroll);
        text("Home VPN · TV development build", 28);
        status = text("Connection status unavailable", 22);
        text("Internet blocking is NOT verified. A connected tunnel does not prove leak protection. "
                + "Use system Always-on VPN and Block connections without VPN where supported.", 18);
        button("Connect a saved profile", this::chooseConnection);
        button("Prepare a TV profile copy", this::prepareCopy);
        button("Import / edit profiles", () -> startActivity(new Intent(this, MainActivity.class)));
        button("System VPN protection settings", () -> {
            try { startActivity(new Intent(Settings.ACTION_VPN_SETTINGS)); }
            catch (ActivityNotFoundException | SecurityException unavailable) {
                message("VPN settings unavailable", "This device does not expose these settings to this app. "
                        + "App-level reconnect protection cannot guarantee blocking after a crash or reboot.");
            }
        });
        button("Reconnect at startup", () -> chooseProfile(profile -> {
            Preferences.getDefaultSharedPreferences(this).edit()
                    .putString("alwaysOnVpn", profile.getUUIDString())
                    .putBoolean("restartvpnonboot", true).apply();
            message("Startup profile saved", "Startup reconnect requested. This does not enable system "
                    + "VPN lockdown and cannot block traffic before the service starts.");
        }));
        button("Connection logs", () -> startActivity(new Intent(this, LogWindow.class)));
        button("Disconnect…", () -> startActivity(new Intent(this, DisconnectVPN.class)));
        content.getChildAt(3).requestFocus();
    }

    private void chooseConnection() {
        chooseProfile(profile -> {
            Intent intent = new Intent(this, LaunchVPN.class);
            intent.setAction(Intent.ACTION_MAIN);
            intent.putExtra(LaunchVPN.EXTRA_KEY, profile.getUUIDString());
            intent.putExtra(OpenVPNService.EXTRA_START_REASON, "TV dashboard");
            startActivity(intent);
        });
    }

    private void prepareCopy() {
        chooseProfile(source -> new AlertDialog.Builder(this)
                .setTitle("Prepare a separate TV copy?")
                .setMessage("Request full IPv4/IPv6 routing, remove app and LAN bypasses, retain the tunnel "
                        + "during reconnects, and retry connections. Original profile stays unchanged. "
                        + "Server routes and DNS still need testing; this is not system lockdown.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create copy", (dialog, which) -> {
                    try {
                        VpnProfile copy = TvProfilePolicy.createCopy(source);
                        ProfileManager manager = ProfileManager.getInstance(this);
                        ProfileManager.saveProfile(this, copy);
                        manager.addProfile(copy);
                        manager.saveProfileList(this);
                        message("TV copy created", "Select " + copy.mName + " when connecting.");
                    } catch (IllegalArgumentException error) {
                        message("Profile needs review", error.getMessage());
                    } catch (RuntimeException error) {
                        message("Could not save profile", "The TV copy could not be saved. Check storage and connection logs.");
                    }
                }).show());
    }

    private interface ProfileAction { void run(VpnProfile profile); }
    private void chooseProfile(ProfileAction action) {
        ProfileManager manager = ProfileManager.getInstance(this);
        manager.refreshVPNList(this);
        ArrayList<VpnProfile> profiles = new ArrayList<>(manager.getProfiles());
        profiles.sort(Comparator.comparing(p -> p.mName, String.CASE_INSENSITIVE_ORDER));
        if (profiles.isEmpty()) {
            message("Import a profile first", "Open Import / edit profiles and import your router's .ovpn file.");
            return;
        }
        String[] names = new String[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) names[i] = profiles.get(i).mName;
        new AlertDialog.Builder(this).setTitle("Select VPN profile")
                .setItems(names, (dialog, which) -> action.run(profiles.get(which))).show();
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        view.setPadding(0, dp(8), 0, dp(8));
        content.addView(view);
        return view;
    }

    private void button(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(20);
        button.setAllCaps(false);
        button.setMinHeight(dp(56));
        button.setFocusable(true);
        button.setOnClickListener(view -> action.run());
        button.setOnFocusChangeListener((view, focused) -> {
            view.setScaleX(focused ? 1.02f : 1f);
            view.setScaleY(focused ? 1.02f : 1f);
        });
        content.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void message(String title, String body) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("OK", null).show();
    }
    @Override protected void onResume() { super.onResume(); VpnStatus.addStateListener(this); }
    @Override protected void onPause() { VpnStatus.removeStateListener(this); super.onPause(); }
    @Override public void setConnectedVPN(String uuid) { }
    @Override public void updateState(String state, String log, int resource,
                                     ConnectionStatus level, Intent intent) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            status.setText(level == ConnectionStatus.LEVEL_CONNECTED
                    ? "VPN tunnel connected · protection not verified"
                    : "VPN: " + level.name().replace("LEVEL_", "").replace('_', ' ')
                    + " · internet may be available");
        });
    }
}

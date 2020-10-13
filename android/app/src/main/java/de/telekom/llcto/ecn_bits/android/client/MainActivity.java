package de.telekom.llcto.ecn_bits.android.client;

/*-
 * Copyright © 2020
 *      mirabilos <t.glaser@tarent.de>
 * Licensor: Deutsche Telekom
 *
 * Provided that these terms and disclaimer and all copyright notices
 * are retained or reproduced in an accompanying document, permission
 * is granted to deal in this work without restriction, including un‐
 * limited rights to use, publicly perform, distribute, sell, modify,
 * merge, give away, or sublicence.
 *
 * This work is provided “AS IS” and WITHOUT WARRANTY of any kind, to
 * the utmost extent permitted by applicable law, neither express nor
 * implied; without malicious intent or gross negligence. In no event
 * may a licensor, author or contributor be held liable for indirect,
 * direct, other damage, loss, or other issues arising in any way out
 * of dealing in the work, even if advised of the possibility of such
 * damage or existence of a defect, except proven that it results out
 * of said person’s immediate fault when using the work as intended.
 */

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;
import de.telekom.llcto.ecn_bits.android.lib.Bits;
import de.telekom.llcto.ecn_bits.android.lib.ECNBitsLibraryException;
import de.telekom.llcto.ecn_bits.android.lib.ECNStatistics;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.evolvis.tartools.rfc822.FQDN;
import org.evolvis.tartools.rfc822.IPAddress;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.ECNBitsDatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.BiFunction;

@SuppressWarnings({ "FieldCanBeLocal", /* UnIntelliJ bug */ "RedundantSuppression" })
public class MainActivity extends AppCompatActivity {
    private static final String OUTPUT_LINES = "outputLines";
    private static final String OUTPUT_POS = "outputPos";

    private final ArrayList<String> outputLines = new ArrayList<>();

    private String contentSeparator = "\n";

    private EditText hostnameText;
    private EditText portText;
    private Spinner bitsDropdown;
    private Button sendButton;
    private RecyclerView outputListView;
    private OutputListAdapter outputListAdapter;
    private LinearLayoutManager outputListLayoutMgr;
    private InputMethodManager imm;
    private boolean showKbd = false;
    private ECNBitsDatagramSocket sock;
    private Thread netThread = null;
    private volatile boolean exiting = false;

    /**
     * Adapts a {@link Bits} enum for use with an {@link ArrayAdapter}: the
     * latter is supremely inflexible and insists on using {@link #toString()}
     * to determine the dropdown value, which isn’t overridable…
     */
    @RequiredArgsConstructor
    @Getter
    private static class BitsAdapter {
        static final BitsAdapter[] values;

        static {
            val bits = Bits.values();
            values = new BitsAdapter[bits.length];
            for (int i = 0; i < bits.length; ++i) {
                values[i] = new BitsAdapter(bits[i]);
            }
        }

        private final Bits bit;

        @Override
        public String toString() {
            return bit.getShortname();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        netThread = null;
        exiting = false;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //  deepcode ignore ApiMigration~getSystemService: false positive (IME not inflater)
        imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);

        final Point appSize = new Point();
        getWindowManager().getDefaultDisplay().getSize(appSize);
        contentSeparator = appSize.x > appSize.y ? "│" : "\n";

        outputLines.clear();
        outputListView = findViewById(R.id.outputList);
        outputListView.setHasFixedSize(true);
        outputListLayoutMgr = new LinearLayoutManager(this);
        outputListView.setLayoutManager(outputListLayoutMgr);
        outputListAdapter = new OutputListAdapter(outputLines);
        outputListView.setAdapter(outputListAdapter);
        outputListView.addItemDecoration(new DividerItemDecoration(this,
          DividerItemDecoration.VERTICAL));
        resetOutputLine("(received packets will show up here)");
        showKbd = savedInstanceState == null;

        hostnameText = findViewById(R.id.hostnameText);
        portText = findViewById(R.id.portText);
        bitsDropdown = findViewById(R.id.bitsDropdown);
        val bitsAdapter = new ArrayAdapter<>(this,
          android.R.layout.simple_spinner_item, BitsAdapter.values);
        bitsDropdown.setAdapter(bitsAdapter);
        sendButton = findViewById(R.id.sendButton);
        sendButton.setSaveEnabled(false);
        uiEnabled(true);
    }

    private void uiEnabled(final boolean status) {
        hostnameText.setEnabled(status);
        portText.setEnabled(status);
        bitsDropdown.setEnabled(status);
        sendButton.setEnabled(status);
    }

    @Override
    protected void onDestroy() {
        if (!exiting) {
            exiting = true;
            if (netThread != null) {
                sock.close();
                netThread.interrupt();
                netThread = null;
            }
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(OUTPUT_LINES, outputLines);
        outState.putInt(OUTPUT_POS, outputListLayoutMgr.findFirstVisibleItemPosition());
    }

    @Override
    protected void onRestoreInstanceState(final Bundle state) {
        super.onRestoreInstanceState(state);
        final ArrayList<String> newdata = state.getStringArrayList(OUTPUT_LINES);
        if (newdata != null) {
            outputLines.clear();
            outputLines.addAll(newdata);
            outputListAdapter.notifyDataSetChanged();
        }
        outputListView.scrollToPosition(state.getInt(OUTPUT_POS, outputLines.size()));
        showKbd = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (showKbd) {
            showKbd = false;
        } else {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
    }

    private void resetOutputLine(final String first) {
        outputLines.clear();
        outputLines.add(first);
        outputListAdapter.notifyDataSetChanged();
    }

    private String addOutputLine(final String line) {
        final int newItem = outputLines.size();
        outputLines.add(line);
        outputListAdapter.notifyItemInserted(newItem);
        outputListView.smoothScrollToPosition(newItem);
        return line;
    }

    private boolean getFieldValueFocusOnError = true;

    private <O, T> T getFieldValue(final boolean first, final TextView v,
      final BiFunction<String, O, Map.Entry<String, T>> extractor,
      final O extractorArgument) {
        if (first) {
            getFieldValueFocusOnError = true;
        }
        final Map.Entry<String, T> ev = extractor.apply(v.getText().toString(),
          extractorArgument);
        if (ev.getKey() == null) {
            return ev.getValue();
        }
        v.setError(addOutputLine(ev.getKey()));
        if (getFieldValueFocusOnError) {
            v.requestFocus();
            getFieldValueFocusOnError = false;
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Map.Entry<String, String> stringExtractor(final String s, final String what) {
        if ("".equals(s)) {
            return new AbstractMap.SimpleEntry<>("empty " + what, null);
        }
        return new AbstractMap.SimpleEntry<>(null, s);
    }

    private static class IPorFQDN {
        final boolean resolved;
        final String s;
        final InetAddress[] a = new InetAddress[1];

        IPorFQDN(final String i) {
            resolved = false;
            s = i;
        }

        IPorFQDN(final String i, final InetAddress ip) {
            resolved = true;
            s = i;
            a[0] = ip;
        }
    }

    private Map.Entry<String, IPorFQDN> fqdnOrIPExtractor(final String s, final String what) {
        if ("".equals(s)) {
            return new AbstractMap.SimpleEntry<>("empty " + what, null);
        }
        if (FQDN.isDomain(s)) {
            return new AbstractMap.SimpleEntry<>(null, new IPorFQDN(s));
        }
        final InetAddress i6 = IPAddress.v6(s);
        if (i6 != null) {
            return new AbstractMap.SimpleEntry<>(null, new IPorFQDN(s, i6));
        }
        final InetAddress i4 = IPAddress.v4(s);
        if (i4 != null) {
            return new AbstractMap.SimpleEntry<>(null, new IPorFQDN(s, i4));
        }
        return new AbstractMap.SimpleEntry<>("not an IP or FQDN: " + what, null);
    }

    @AllArgsConstructor
    private static class UIntExtractorBounds {
        final int lower;
        final int upper;
        final String what;
    }

    private Map.Entry<String, Integer> uintExtractor(final String s, final UIntExtractorBounds b) {
        if ("".equals(s)) {
            return new AbstractMap.SimpleEntry<>("empty " + b.what, null);
        }
        final int res;
        try {
            res = Integer.parseUnsignedInt(s);
        } catch (NumberFormatException e) {
            return new AbstractMap.SimpleEntry<>(String.format("bad %s: %s", b.what, e), null);
        }
        if (res < b.lower) {
            return new AbstractMap.SimpleEntry<>(String.format("%s too %s, not in [%d, %d]",
              b.what, "small", b.lower, b.upper), null);
        } else if (res > b.upper) {
            return new AbstractMap.SimpleEntry<>(String.format("%s too %s, not in [%d, %d]",
              b.what, "large", b.lower, b.upper), null);
        }
        return new AbstractMap.SimpleEntry<>(null, res);
    }

    public void clkSend(final View v) {
        final IPorFQDN hostname = getFieldValue(true, hostnameText, this::fqdnOrIPExtractor, "hostname");
        final Integer port = getFieldValue(false, portText, this::uintExtractor,
          new UIntExtractorBounds(1, 65535, "port"));
        //if (Stream.of(hostname, port).anyMatch(Objects::isNull)) fails in UnIntelliJ
        if (hostname == null || port == null) {
            return;
        }
        final Bits outBits = getDropdownSelectedValueOr(bitsDropdown,
          BitsAdapter.values, BitsAdapter.values[0]).getBit();

        try {
            sock = new ECNBitsDatagramSocket();
        } catch (ECNBitsLibraryException e) {
            addOutputLine("could not initialise ECN-Bits library: " + e.getMessage());
            return;
        } catch (SocketException e) {
            addOutputLine("could not create socket: " + e);
            return;
        }

        uiEnabled(false);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        addOutputLine(String.format("connect to [%s]:%d with %s", hostname.resolved ?
          hostname.a[0].getHostAddress() : hostname.s, port, outBits.getShortname()));
        val hdr = String.format("sent(%s)/received packets:", outBits.getShortname());
        netThread = new Thread(() -> {
            sock.startMeasurement();
            try {
                boolean oneSuccess = false;
                sock.setSoTimeout(1000);
                // Note: ↓ only works outside of the emulator or via loopback
                sock.setTrafficClass(outBits.getBits());
                final byte[] buf = new byte[512];
                final InetAddress[] dstArr = hostname.resolved ? hostname.a :
                  InetAddress.getAllByName(hostname.s);
                runOnUiThread(() -> resetOutputLine(hdr));
                for (final InetAddress dst : dstArr) {
                    if (exiting || Thread.interrupted()) {
                        return;
                    }
                    runOnUiThread(() -> addOutputLine(String.format(" → [%s]:%d",
                      dst.getHostAddress(), port)));
                    buf[0] = 'h';
                    buf[1] = 'i';
                    buf[2] = '!';
                    final DatagramPacket psend = new DatagramPacket(buf, 3, dst, port);
                    try {
                        sock.send(psend);
                    } catch (IOException e) {
                        if (exiting || Thread.interrupted()) {
                            return;
                        }
                        runOnUiThread(() -> addOutputLine("!! send: " + e));
                        continue;
                    }
                    final DatagramPacket precv = new DatagramPacket(buf, buf.length);
                    while (true) {
                        try {
                            sock.receive(precv);
                        } catch (SocketTimeoutException e) {
                            break;
                        } catch (IOException e) {
                            if (exiting || Thread.interrupted()) {
                                return;
                            }
                            runOnUiThread(() -> addOutputLine("!! recv: " + e));
                            break;
                        }
                        final String stamp = ZonedDateTime.now(ZoneOffset.UTC)
                          .truncatedTo(ChronoUnit.MILLIS)
                          .format(DateTimeFormatter.ISO_INSTANT);
                        final Byte trafficClass = sock.retrieveLastTrafficClass();
                        oneSuccess = true;
                        final String userData = new String(buf, StandardCharsets.UTF_8);
                        final String logLine = String.format("• %s %s%s%s",
                          stamp, Bits.print(trafficClass), contentSeparator,
                          userData.trim());
                        runOnUiThread(() -> addOutputLine(logLine));
                    }
                }
                if (oneSuccess) {
                    runOnUiThread(() -> addOutputLine(" ‣ Success!"));
                } else {
                    runOnUiThread(() -> addOutputLine("!! failed !!"));
                }
            } catch (UnknownHostException e) {
                runOnUiThread(() -> addOutputLine("!! resolve: " + e));
            } catch (SocketException e) {
                runOnUiThread(() -> addOutputLine("!! setsockopt: " + e));
            } finally {
                try {
                    final ECNStatistics stats = sock.getMeasurement(false);
                    runOnUiThread(() -> addOutputLine(stats == null ?
                      "!! no congestion measurement" :
                      String.format("ℹ %.2f%% of %d packets received over %d ms were congested",
                        stats.getCongestionFactor() * 100.0, stats.getReceivedPackets(),
                        stats.getLengthOfMeasuringPeriod() / 1000000L)));
                } catch (ArithmeticException e) {
                    runOnUiThread(() -> addOutputLine("!! ECNStatistics: " + e));
                }
                if (!sock.isClosed()) {
                    sock.close();
                }
                runOnUiThread(() -> uiEnabled(true));
            }
        });
        netThread.start();
    }

    public static <T> T getDropdownSelectedValueOr(final Spinner dropdown,
      final T[] values, final T defaultValue /* can be null */) {
        final int pos = dropdown.getSelectedItemPosition();
        if (pos == Spinner.INVALID_POSITION) {
            // doesn’t happen in my experience, but… cf. https://stackoverflow.com/q/10105628/2171120
            return defaultValue;
        }
        return values[pos];
    }

    public void clkLicence(final View v) {
        // TODO: this is nowhere near sufficient for production
        startActivity(new Intent(this, OssLicensesMenuActivity.class));
    }
}

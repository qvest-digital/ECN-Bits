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
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import lombok.AllArgsConstructor;
import org.evolvis.tartools.rfc822.FQDN;
import org.evolvis.tartools.rfc822.IPAddress;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
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

@SuppressWarnings("FieldCanBeLocal")
public class MainActivity extends AppCompatActivity {
    private static final String OUTPUT_LINES = "outputLines";
    private static final String OUTPUT_POS = "outputPos";

    private final ArrayList<String> outputLines = new ArrayList<>();

    private EditText hostnameText;
    private EditText portText;
    private Button sendButton;
    private Button startStopButton;
    private RecyclerView outputListView;
    private OutputListAdapter outputListAdapter;
    private LinearLayoutManager outputListLayoutMgr;
    private InputMethodManager imm;
    private boolean showKbd = false;
    private DatagramSocket sock;
    private Thread netThread = null;
    private volatile boolean exiting = false;
    private boolean channelStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        netThread = null;
        exiting = false;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);

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
        sendButton = findViewById(R.id.sendButton);
        sendButton.setSaveEnabled(false);
        sendButton.setEnabled(true);
        startStopButton = findViewById(R.id.channelButton);
        channelStarted = false;
        startStopButton.setText(R.string.startLabel);
        startStopButton.setSaveEnabled(false);
        startStopButton.setEnabled(true);
    }

    @Override
    protected void onDestroy() {
        exiting = true;
        if (netThread != null) {
            sock.close();
            netThread.interrupt();
            netThread = null;
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

    //private Map.Entry<String, String> stringExtractor(final String s, final String what) {
    //    if ("".equals(s)) {
    //        return new AbstractMap.SimpleEntry<>("empty " + what, null);
    //    }
    //    return new AbstractMap.SimpleEntry<>(null, s);
    //}

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
    private static class Bounds {
        final int lower;
        final int upper;
        final String what;
    }

    private Map.Entry<String, Integer> uintExtractor(final String s, final Bounds b) {
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
          new Bounds(1, 65535, "port"));
        //if (Stream.of(hostname, port).anyMatch(Objects::isNull)) fails in UnIntelliJ
        if (hostname == null || port == null) {
            return;
        }

        try {
            sock = new DatagramSocket();
        } catch (SocketException e) {
            addOutputLine("could not create socket: " + e);
            return;
        }

        sendButton.setEnabled(false);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        addOutputLine(String.format("connect to [%s]:%d", hostname.resolved ?
          hostname.a[0].getHostAddress() : hostname.s, port));
        netThread = new Thread(() -> {
            try {
                boolean oneSuccess = false;
                sock.setSoTimeout(1000);
                final byte[] buf = new byte[512];
                final InetAddress[] dstArr = hostname.resolved ? hostname.a :
                  InetAddress.getAllByName(hostname.s);
                runOnUiThread(() -> resetOutputLine("sent/received packets:"));
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
                        oneSuccess = true;
                        final String userData = new String(buf, StandardCharsets.UTF_8);
                        final String logLine = String.format("%s %s <%s>",
                          stamp, "[ECN?]", userData.trim());
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
                runOnUiThread(() -> addOutputLine("!! SO_TMOUT: " + e));
            } finally {
                if (!sock.isClosed()) {
                    sock.close();
                }
                runOnUiThread(() -> sendButton.setEnabled(true));
            }
        });
        netThread.start();
    }

    public void clkStartStop(final View v) {
        // TODO: https://developer.android.com/reference/java/nio/channels/DatagramChannel
        if (channelStarted) {
            addOutputLine("stopping channel: not yet implemented, sorry");
            channelStarted = false;
            startStopButton.setText(R.string.startLabel);
        } else {
            channelStarted = true;
            startStopButton.setText(R.string.stopLabel);
            addOutputLine("starting channel: not yet implemented, sorry");
        }
    }
}

package de.telekom.llcto.ecn_bits.android.client;

/*-
 * Copyright © 2020
 *	mirabilos <t.glaser@tarent.de>
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
import android.widget.EditText;
import android.widget.TextView;
import lombok.AllArgsConstructor;
import org.evolvis.tartools.rfc822.FQDN;

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
    private RecyclerView outputListView;
    private OutputListAdapter outputListAdapter;
    private LinearLayoutManager outputListLayoutMgr;
    private InputMethodManager imm;
    private boolean showKbd = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);

        outputLines.clear();
        outputLines.add("(received packets will show up here)");
        outputListView = findViewById(R.id.outputList);
        outputListView.setHasFixedSize(true);
        outputListLayoutMgr = new LinearLayoutManager(this);
        outputListView.setLayoutManager(outputListLayoutMgr);
        outputListAdapter = new OutputListAdapter(outputLines);
        outputListView.setAdapter(outputListAdapter);
        outputListView.addItemDecoration(new DividerItemDecoration(this,
          DividerItemDecoration.VERTICAL));
        showKbd = savedInstanceState == null;

        hostnameText = findViewById(R.id.hostnameText);
        portText = findViewById(R.id.portText);
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

    private Map.Entry<String, String> fqdnExtractor(final String s, final String what) {
        if ("".equals(s)) {
            return new AbstractMap.SimpleEntry<>("empty " + what, null);
        }
        if (!FQDN.isDomain(s)) {
            return new AbstractMap.SimpleEntry<>("not an FQDN: " + what, null);
        }
        return new AbstractMap.SimpleEntry<>(null, s);
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

    private boolean anyNull(final Object... args) {
        for (final Object o : args) {
            if (o == null) {
                return true;
            }
        }
        return false;
    }

    public void clkSend(final View v) {
        final String hostname = getFieldValue(true, hostnameText, this::fqdnExtractor, "hostname");
        final Integer port = getFieldValue(false, portText, this::uintExtractor,
          new Bounds(1, 65535, "port"));
        if (anyNull(hostname, port)) {
            return;
        }

        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        addOutputLine(String.format("connect to [%s]:%d", hostname, port));
    }
}

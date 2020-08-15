package de.telekom.llcto.ecn_bits.android.client;

import android.app.Activity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;

@SuppressWarnings("FieldCanBeLocal")
public class MainActivity extends AppCompatActivity {
    private static final String OUTPUT_LINES = "outputLines";
    private static final String OUTPUT_POS = "outputPos";

    private final ArrayList<String> outputLines = new ArrayList<>();

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

    public void clkSend(final View v) {
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

        final int newItem = outputLines.size();
        outputLines.add("click!");
        outputListAdapter.notifyItemInserted(newItem);
        outputListView.smoothScrollToPosition(newItem);
    }
}

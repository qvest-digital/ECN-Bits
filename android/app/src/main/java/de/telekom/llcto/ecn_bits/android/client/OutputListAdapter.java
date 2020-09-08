package de.telekom.llcto.ecn_bits.android.client;

import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class OutputListAdapter extends RecyclerView.Adapter<OutputListAdapter.ViewHolder> {
    private final List<String> dataset;

    public OutputListAdapter(final List<String> data) {
        dataset = data;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView view;

        public ViewHolder(final TextView v) {
            super(v);
            view = v;
        }

        public TextView getView() {
            return view;
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        final TextView v = new TextView(parent.getContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT));
        //noinspection UnnecessaryLocalVariable
        final ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    @Override
    public int getItemCount() {
        return dataset.size();
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        holder.getView().setText(dataset.get(position));
    }
}

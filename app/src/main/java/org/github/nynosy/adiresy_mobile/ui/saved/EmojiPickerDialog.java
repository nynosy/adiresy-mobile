package org.github.nynosy.adiresy_mobile.ui.saved;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.github.nynosy.adiresy_mobile.R;

public class EmojiPickerDialog {

    public interface OnEmojiSelectedListener {
        void onEmojiSelected(String emoji);
    }

    private static final String[] EMOJIS = {
        "📍", "📌", "🏠", "🏥", "🏫", "🏪",
        "🏢", "🏭", "🏨", "🏦", "🏛", "🏟",
        "⭐", "❤️", "🔑", "🎯", "🌴", "🌊",
        "🚗", "🚑", "🚒", "🎓", "☕", "🍽",
        "🎭", "🎪", "🏁", "⚽", "🌿", "✨",
        "🔴", "🔵", "🟡", "🟠", "🟣", "🟤"
    };

    public static void show(Context context, String currentEmoji,
                            OnEmojiSelectedListener listener) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.dialog_emoji_picker, null);
        GridView grid = view.findViewById(R.id.grid_emoji);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                context, R.layout.item_emoji, EMOJIS) {
            @NonNull
            @Override
            public View getView(int pos, View convertView, @NonNull ViewGroup parent) {
                TextView tv = convertView instanceof TextView
                        ? (TextView) convertView
                        : (TextView) LayoutInflater.from(context)
                                .inflate(R.layout.item_emoji, parent, false);
                tv.setText(EMOJIS[pos]);
                tv.setBackgroundResource(
                        EMOJIS[pos].equals(currentEmoji)
                                ? R.color.emoji_selected_bg
                                : android.R.color.transparent);
                return tv;
            }
        };
        grid.setAdapter(adapter);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.emoji_picker_title)
                .setView(view)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        grid.setOnItemClickListener((parent, v, position, id) -> {
            listener.onEmojiSelected(EMOJIS[position]);
            dialog.dismiss();
        });

        dialog.show();
    }
}

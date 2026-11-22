package dev.odin.controls;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import org.json.*;

/** Owns the performance editor. Loading or dragging a profile only changes its draft. */
final class PerformancePanel {
    private final MainActivity host;
    private final SharedPreferences prefs;
    private final ExecutorService io;
    private Status status;
    private PerformanceProfile draft;
    private String selection, suggestedName = "", error = "";
    private boolean pending, applying,quickRequested;
    private int text, muted, surface;
    private TextView active;
    private Button apply;
    private final ArrayList<FrequencyBar> bars = new ArrayList<>();

    PerformancePanel(MainActivity host, SharedPreferences prefs, ExecutorService io) {
        this.host = host;
        this.prefs = prefs;
        this.io = io;
        selection = prefs.getString("performanceSelection", "");
        try {
            draft = decode(new JSONObject(prefs.getString("performanceDraft", "{}")));
        } catch (Exception ignored) {
        }
    }
    static JSONObject encode(PerformanceProfile profile) throws JSONException {
        return new JSONObject()
            .put("mode", profile.mode)
            .put("minimum", new JSONArray(profile.minimum))
            .put("maximum", new JSONArray(profile.maximum));
    }
    static PerformanceProfile decode(JSONObject json) throws JSONException {
        JSONArray min = json.getJSONArray("minimum"), max = json.getJSONArray("maximum");
        if (min.length() != 3 || max.length() != 3)
            throw new JSONException("Invalid performance limits");
        long[] low = new long[3], high = new long[3];
        for (int i = 0; i < 3; i++) {
            low[i] = min.getLong(i);
            high[i] = max.getLong(i);
        }
        return new PerformanceProfile(json.getInt("mode"), low, high);
    }
    private void saveDraft() {
        if (draft == null)
            return;
        try {
            prefs.edit()
                .putString("performanceSelection", selection)
                .putString("performanceDraft", encode(draft).toString())
                .apply();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
    private ArrayList<String> names() {
        ArrayList<String> result = new ArrayList<>(Arrays.asList(PerformanceProfile.STOCK_NAMES)),
                          custom = new ArrayList<>();
        for (String key : prefs.getAll().keySet())
            if (key.startsWith("performance:"))
                custom.add(key.substring(12));
        Collections.sort(custom, String.CASE_INSENSITIVE_ORDER);
        result.addAll(custom);
        return result;
    }
    private PerformanceProfile original(String name) throws JSONException {
        for (int i = 0; i < 3; i++)
            if (name.equals(PerformanceProfile.STOCK_NAMES[i]))
                return status.stock[i].copy();
        PerformanceProfile profile = decode(new JSONObject(prefs.getString("performance:" + name, "{}")));
        profile.validate(status.frequencies);
        return profile;
    }
    private boolean changed() {
        try {
            return !draft.same(original(selection));
        } catch (Exception e) {
            return true;
        }
    }
    private void changedDraft() {
        saveDraft();
        if (apply != null)
            apply.setEnabled(!applying && !changed());
    }
    private void outline(Button button) {
        GradientDrawable background = host.box(surface, 12);
        background.setStroke(host.dp(1), muted);
        button.setBackground(background);
    }
    void show(int text, int muted, int surface, int accent) {
        this.text = text;
        this.muted = muted;
        this.surface = surface;
        bars.clear();
        active = null;
        apply = null;
        if (!Build.MODEL.equals("Odin3")) {
            host.card("Performance", "CPU and GPU tuning requires compatible Odin 3 CPU and GPU controls.");
            return;
        }
        if (status == null) {
            LinearLayout card =
                host.card("Performance", error.isEmpty() ? "Reading CPU and GPU settings…" : error);
            if (!error.isEmpty())
                host.button(card, "Retry", () -> {
                    error = "";
                    refresh();
                }, false);
            else
                refresh();
            return;
        }
        LinearLayout profile = host.card("Performance profile", "");
        Button picker = host.control(selection + "  ▾", () -> {
            ArrayList<String> names = names();
            host.dialogBuilder()
                .setTitle("Performance profile")
                .setSingleChoiceItems(names.toArray(new String[0]), names.indexOf(selection),
                    (dialog, index) -> {
                        if (applying)
                            return;
                        try {
                            draft = original(names.get(index));
                            selection = names.get(index);
                            suggestedName = "";
                            saveDraft();
                            dialog.dismiss();
                            host.draw();
                        } catch (Exception e) {
                            host.message(e.getMessage());
                        }
                    })
                .setNegativeButton("Cancel", null)
                .show();
        }, false);
        picker.setContentDescription("Performance profile");
        outline(picker);
        profile.addView(picker, new LinearLayout.LayoutParams(-1, host.dp(48)));
        picker.setEnabled(!applying);
        active = host.text(activeText(), 13, muted);
        profile.addView(active);
        profile.addView(host.text(
            "Adjust the bars to make a draft. Save under a custom name to apply changes to a stock profile. System boosts may raise the applied CPU minimum above your requested minimum.",
            12, muted));
        for (int i = 0; i < 3; i++) {
            LinearLayout card = host.card(PerformanceProfile.COMPONENTS[i], "");
            FrequencyBar low = new FrequencyBar(i, true), high = new FrequencyBar(i, false);
            bars.add(low);
            bars.add(high);
            card.addView(low);
            card.addView(high);
            TextView live = host.text(liveText(i), 12, muted);
            card.addView(live);
            low.live = live;
        }
        underclockCard();
        LinearLayout save = host.card("Save profile", "");
        EditText name = new EditText(host);
        name.setSingleLine();
        name.setTextColor(text);
        name.setHintTextColor(muted);
        name.setHint("Performance profile name");
        name.setContentDescription("Performance profile name");
        name.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(40)});
        name.setText(!suggestedName.isEmpty()            ? suggestedName
                : PerformanceProfile.reserved(selection) ? ""
                                                         : selection);
        save.addView(name);
        host.button(save, "Save & apply custom profile", () -> {
            String key = name.getText().toString().trim();
            if (key.isEmpty()) {
                name.setError("Enter a profile name");
                return;
            }
            if (PerformanceProfile.reserved(key)) {
                name.setError("Stock profiles are protected. Choose a custom name.");
                return;
            }
            if (prefs.contains("performance:" + key) && !key.equals(selection)) {
                host.dialogBuilder()
                    .setTitle("Replace " + key + "?")
                    .setMessage("Replace this custom performance profile with the current draft?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Replace", (d, w) -> applyProfile(key, draft.copy(), true))
                    .show();
            } else
                applyProfile(key, draft.copy(), true);
        }, true);
        save.addView(host.text("Saves CPU and GPU minimums, maximums and the base system performance mode. "
                               + "Saving also applies the profile. A safety guard monitors CPU/GPU temperatures and recovers conservatively at 78°C, sensor failure or loss of monitoring. Recovery keeps protected underclock limits until temperatures are valid and below 60°C.",
            12, muted));
        if (!PerformanceProfile.reserved(selection))
            host.button(save, "Delete profile",
                ()
                    -> host.dialogBuilder()
                        .setTitle("Delete " + selection + "?")
                        .setMessage(
                            "The currently applied limits will stay active until you apply another profile.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete",
                            (d, w) -> {
                                prefs.edit().remove("performance:" + selection).apply();
                                selection = PerformanceProfile.STOCK_NAMES[draft.mode];
                                saveDraft();
                                host.draw();
                            })
                        .show(),
                false);
        apply = host.control("Apply " + selection, () -> applyProfile(selection, draft.copy(), false), true);
        apply.setEnabled(!applying && !changed());
        host.performanceAction(apply);
    }
    private void underclockCard() {
        LinearLayout card = host.card("Underclock presets", "");
        Spinner target = new Spinner(host);
        target.setAdapter(host.adapter(
            new String[] {"CPU + GPU", "CPU only", "GPU only", "CPU cores 0–5", "CPU cores 6–7"}));
        target.setContentDescription("Underclock components");
        GradientDrawable background = host.box(surface, 12);
        background.setStroke(host.dp(1), muted);
        target.setBackground(background);
        target.setPadding(host.dp(12), 0, host.dp(12), 0);
        card.addView(target, new LinearLayout.LayoutParams(-1, host.dp(48)));
        LinearLayout row = new LinearLayout(host);
        for (double reduction : new double[] {.10, .175, .25}) {
            String percent = reduction == .175 ? "17.5%" : reduction == .10 ? "10%" : "25%";
            Button button = host.control(percent, () -> {
                if (applying)
                    return;
                draft = draft.underclock(status.frequencies, reduction, target.getSelectedItemPosition());
                suggestedName = target.getSelectedItem() + " −" + percent;
                saveDraft();
                host.draw();
            }, false);
            outline(button);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, host.dp(44), 1);
            size.setMargins(0, host.dp(8), host.dp(6), 0);
            row.addView(button, size);
        }
        card.addView(row);
        card.addView(
            host.text("Lowers the selected maximums by at least the chosen percentage from the hardware "
                      + "maximum, rounding down to a supported step. Selected minimums use the lowest "
                      + "supported frequency. Load a preset, then save it as a custom profile.",
                12, muted));
    }
    private String activeText() {
        String name = "System settings";
        try {
            String saved = prefs.getString("performanceActiveConfig", null);
            if (saved != null && status.current.effectiveMatches(decode(new JSONObject(saved))))
                name = prefs.getString("performanceActiveName", name);
            else
                for (int i = 0; i < 3; i++)
                    if (status.current.effectiveMatches(status.stock[i]))
                        name = PerformanceProfile.STOCK_NAMES[i];
        } catch (Exception ignored) {
        }
        if (status.guard.equals("recovery")) name = "Safe recovery (" + status.reason.replace('-', ' ') + ")";
        String safety = status.guard.equals("armed") ? " · Safety guard active" : "";
        return "Active: " + name + " · System mode: " + PerformanceProfile.STOCK_NAMES[status.current.mode] + safety;
    }
    private String frequency(int component, long value) {
        return String.format(Locale.ROOT, component == 2 ? "%.0f MHz" : "%.3f GHz", value / 1e6);
    }
    private String liveText(int component) {
        return "Live: " + frequency(component, status.live[component])
            + " · Applied: " + frequency(component, status.current.minimum[component]) + " – "
            + frequency(component, status.current.maximum[component]);
    }
    void quickProfiles(){
        if(!android.os.Build.MODEL.equals("Odin3")){host.message("CPU profiles are supported on Odin 3.");return;}
        if(status==null){quickRequested=true;refresh();return;}quickRequested=false;ArrayList<String> available=names();
        host.dialogBuilder().setTitle("Raven CPU profiles").setItems(available.toArray(new String[0]),(dialog,n)->{try{String name=available.get(n);PerformanceProfile profile=original(name);applyProfile(name,profile,false);}catch(Exception e){host.message(e.getMessage());}}).setNegativeButton("Cancel",null).show();
    }
    void refresh() {
        if (pending || applying || !Build.MODEL.equals("Odin3"))
            return;
        pending = true;
        io.execute(() -> {
            Status update = null;
            String failure = null;
            try {
                update = new Status(Root.run(host, "performance-status"));
            } catch (Exception e) {
                failure = e.getMessage();
            }
            Status result = update;
            String failed = failure;
            host.runOnUiThread(() -> {
                pending = false;
                if (host.isFinishing() || host.isDestroyed())
                    return;
                if (failed != null) {
                    if(quickRequested){quickRequested=false;host.message(failed);}
                    error = failed;
                    if (active != null)
                        active.setText("Read failed: " + failed);
                    else if (host.performanceVisible())
                        host.draw();
                    return;
                }
                boolean first = status == null;
                status = result;
                error = "";
                if(quickRequested)quickProfiles();
                if (first) {
                    try {
                        if (draft == null || !names().contains(selection))
                            throw new IllegalArgumentException();
                        draft.validate(status.frequencies);
                    } catch (Exception e) {
                        selection = PerformanceProfile.STOCK_NAMES[status.current.mode];
                        draft = status.stock[status.current.mode].copy();
                    }
                    if (host.performanceVisible())
                        host.draw();
                } else {
                    if (active != null)
                        active.setText(activeText());
                    for (FrequencyBar bar : bars) {
                        bar.slider.invalidate();
                        if (bar.live != null)
                            bar.live.setText(liveText(bar.component));
                    }
                }
            });
        });
    }
    private void applyProfile(String name, PerformanceProfile selected, boolean save) {
        if (applying)
            return;
        if (!prefs.getBoolean("setupCompleteV2", false)) {
            host.guidedSetup();
            return;
        }
        try {
            selected.validate(status.frequencies);
        } catch (Exception e) {
            host.message(e.getMessage());
            return;
        }
        final String json;
        try {
            json = encode(selected).toString();
        } catch (JSONException e) {
            host.message(e.getMessage());
            return;
        }
        String command;
        if (!save && PerformanceProfile.reserved(name))
            command = "performance-stock " + selected.mode;
        else {
            command = "performance-apply " + selected.mode;
            for (int i = 0; i < 3; i++) command += " " + selected.minimum[i] + " " + selected.maximum[i];
        }
        applying = true;
        host.performanceBusy(true);
        host.draw();
        String request = command;
        io.execute(() -> {
            String failure = null;
            try {
                GameProfileSession.globalChoice(host,"cpu");
                PerformanceOwnership.begin(host);
                if (request.startsWith("performance-apply ")) PerformanceService.apply(host, request.substring("performance-apply ".length()));
                else { Root.run(host, request); host.getSystemService(android.app.NotificationManager.class).cancel(3);ControlNotifications.clear(host,ControlNotifications.CPU_ALERT); }
            } catch (Exception e) {
                failure = e.getMessage();
            }
            String failed = failure;
            host.runOnUiThread(() -> {
                applying = false;
                host.performanceBusy(false);
                if (host.isFinishing() || host.isDestroyed())
                    return;
                if (failed == null) {
                    SharedPreferences.Editor editor = prefs.edit()
                                                          .putString("performanceActiveName", name)
                                                          .putString("performanceActiveConfig", json);
                    if (save)
                        editor.putString("performance:" + name, json);
                    editor.apply();
                    CpuTile.refresh(host);
                    selection = name;
                    draft = selected;
                    suggestedName = "";
                    saveDraft();
                } else
                    host.message(failed);
                host.draw();
                refresh();
            });
        });
    }
    private final class FrequencyBar extends LinearLayout {
        final int component;
        final boolean minimum;
        final SeekBar slider;
        final TextView caption;
        TextView live;
        FrequencyBar(int component, boolean minimum) {
            super(host);
            this.component = component;
            this.minimum = minimum;
            setOrientation(VERTICAL);
            caption = host.text("", 14, text);
            addView(caption);
            slider = new SeekBar(host) {
                final Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    long[] steps = status.frequencies[component];
                    double value = status.live[component];
                    int index = 0;
                    while (index < steps.length - 1 && steps[index + 1] <= value) index++;
                    double position = index;
                    if (index < steps.length - 1)
                        position += (value - steps[index]) / (steps[index + 1] - steps[index]);
                    float fraction = (float) Math.max(0, Math.min(1, position / (steps.length - 1)));
                    if (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL)
                        fraction = 1 - fraction;
                    float x =
                        getPaddingLeft() + fraction * (getWidth() - getPaddingLeft() - getPaddingRight());
                    marker.setColor(text);
                    marker.setStrokeWidth(host.dp(2));
                    canvas.drawLine(
                        x, getHeight() / 2f - host.dp(8), x, getHeight() / 2f + host.dp(8), marker);
                }
            };
            host.tintSlider(slider);
            GradientDrawable tick = new GradientDrawable();
            tick.setColor(muted);
            tick.setShape(GradientDrawable.OVAL);
            tick.setSize(host.dp(5), host.dp(5));
            slider.setTickMark(tick);
            slider.setMax(status.frequencies[component].length - 1);
            slider.setProgress(Arrays.binarySearch(status.frequencies[component],
                minimum ? draft.minimum[component] : draft.maximum[component]));
            slider.setEnabled(!applying);
            slider.setContentDescription(
                PerformanceProfile.COMPONENTS[component] + (minimum ? " minimum" : " maximum"));
            addView(slider, new LinearLayout.LayoutParams(-1, host.dp(40)));
            updateCaption();
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar view, int progress, boolean user) {
                    if (!user)
                        return;
                    long value = status.frequencies[component][progress];
                    if (minimum) {
                        draft.minimum[component] = value;
                        if (draft.maximum[component] < value)
                            draft.maximum[component] = value;
                    } else {
                        draft.maximum[component] = value;
                        if (draft.minimum[component] > value)
                            draft.minimum[component] = value;
                    }
                    for (FrequencyBar bar : bars)
                        if (bar.component == component) {
                            bar.slider.setProgress(Arrays.binarySearch(status.frequencies[component],
                                bar.minimum ? draft.minimum[component] : draft.maximum[component]));
                            bar.updateCaption();
                        }
                    changedDraft();
                }
                public void onStartTrackingTouch(SeekBar view) {}
                public void onStopTrackingTouch(SeekBar view) {}
            });
        }
        void updateCaption() {
            caption.setText((minimum ? "Minimum: " : "Maximum: ")
                + frequency(component, minimum ? draft.minimum[component] : draft.maximum[component]));
        }
    }
    static final class Status {
        final long[][] frequencies = new long[3][];
        final long[] live = new long[3];
        final PerformanceProfile[] stock = new PerformanceProfile[3];
        final PerformanceProfile current;
        final String guard, reason;
        Status(String raw) throws Exception {
            Properties p = new Properties();
            p.load(new java.io.StringReader(raw));
            guard = p.getProperty("guard", "stopped");
            reason = p.getProperty("guardReason", "");
            long[] min = new long[3], max = new long[3];
            for (int i = 0; i < 3; i++) {
                String[] values = p.getProperty("frequencies." + i, "").trim().split(" +");
                frequencies[i] = new long[values.length];
                for (int j = 0; j < values.length; j++) frequencies[i][j] = Long.parseLong(values[j]);
                Arrays.sort(frequencies[i]);
                if (frequencies[i].length < 2 || frequencies[i][0] <= 0)
                    throw new IllegalArgumentException("Frequency table unavailable");
                min[i] = Long.parseLong(p.getProperty("minimum." + i));
                max[i] = Long.parseLong(p.getProperty("maximum." + i));
                live[i] = Long.parseLong(p.getProperty("current." + i));
            }
            current = new PerformanceProfile(Integer.parseInt(p.getProperty("mode")), min, max);
            for (int m = 0; m < 3; m++) {
                String[] values = p.getProperty("stock." + m, "").split(" ");
                if (values.length != 6)
                    throw new IllegalArgumentException("Stock performance limits unavailable");
                long[] low = new long[3], high = new long[3];
                for (int i = 0; i < 3; i++) {
                    low[i] = Long.parseLong(values[i * 2]);
                    high[i] = Long.parseLong(values[i * 2 + 1]);
                }
                stock[m] = new PerformanceProfile(m, low, high);
                stock[m].validate(frequencies);
            }
        }
    }
}

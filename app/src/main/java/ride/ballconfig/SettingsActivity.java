package ride.ballconfig;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.RingtonePreference;
import android.support.v7.app.ActionBar;
import android.text.InputType;
import android.text.TextUtils;
import android.view.MenuItem;

import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;

import proto.Protocol;

public class SettingsActivity extends AppCompatPreferenceActivity {
    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else if (preference instanceof RingtonePreference) {
                // For ringtone preferences, look up the correct display value
                // using RingtoneManager.
                if (TextUtils.isEmpty(stringValue)) {
                    // Empty values correspond to 'silent' (no ringtone).
               //     preference.setSummary(R.string.pref_ringtone_silent);

                } else {
                    Ringtone ringtone = RingtoneManager.getRingtone(
                            preference.getContext(), Uri.parse(stringValue));

                    if (ringtone == null) {
                        // Clear the summary if there was a lookup error.
                        preference.setSummary(null);
                    } else {
                        // Set the summary to reflect the new ringtone display
                        // name.
                        String name = ringtone.getTitle(preference.getContext());
                        preference.setSummary(name);
                    }
                }

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    public static void setDefaults(Message.Builder bldr) {
        for (Descriptors.FieldDescriptor field: bldr.getDescriptorForType().getFields()) {
            if (field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
                setDefaults(bldr.getFieldBuilder(field));
            }
            else {
                bldr.setField(field, bldr.getField(field));
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();

        setDefaults(configBuilder);
        if (getIntent() != null) {
            byte[] cfg = getIntent().getByteArrayExtra("config");
            if (cfg != null) {
                try {
                    configBuilder.mergeFrom(cfg);
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onIsMultiPane() {
        return (this.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    Protocol.Config.Builder configBuilder = Protocol.Config.newBuilder();

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<PreferenceActivity.Header> target) {
        List<Descriptors.FieldDescriptor> fields = configBuilder.getDescriptorForType().getFields();
        for (Descriptors.FieldDescriptor field : fields) {
            if (field.getType() != Descriptors.FieldDescriptor.Type.MESSAGE) {
                continue;
            }

            PreferenceActivity.Header header = new PreferenceActivity.Header();
            header.title = field.getName();
       //     header.summary = "Change even more settings";
            header.fragment = ProtoPreferenceFragment.class.getName();

            Bundle args = new Bundle();
            args.putByteArray("data", ((GeneratedMessageV3) configBuilder.getField(field)).toByteArray());
            args.putString("type", field.getMessageType().getFullName());
            args.putString("fieldName", field.getName());
            header.fragmentArguments = args;

            target.add(header);
        }
    }

    @Override
    public void finish() {
        Intent intent = new Intent()
                .putExtra("config", configBuilder.build().toByteArray());
        setResult(RESULT_OK, intent);
        super.finish();
    }

    protected boolean isValidFragment(String fragmentName) {
        return true;
    }


    static Descriptors.Descriptor findType(String name) {
        Descriptors.FileDescriptor fd = Protocol.Config.getDescriptor().getFile();
        if (name.contains(".")) {
            String parent = name.substring(0, name.indexOf('.'));
            Descriptors.Descriptor type = fd.findMessageTypeByName(parent);
            if (type == null)
                return  null;

            do {
                name = name.substring(name.indexOf('.') + 1);
                type = type.findNestedTypeByName(name);
                if (type == null)
                    return  null;
            }
            while (name.contains("."));
            return type;
        }
        else {
            return fd.findMessageTypeByName(name);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class ProtoPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            //addPreferencesFromResource(R.xml.pref_general);
            setHasOptionsMenu(true);

            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getActivity());
            setPreferenceScreen(screen);
            Descriptors.Descriptor type = findType(getArguments().getString("type"));

            SettingsActivity activity =  (SettingsActivity)getActivity();
            final Message.Builder m =
                    activity.configBuilder.getFieldBuilder(
                            activity.configBuilder.getDescriptorForType()
                                    .findFieldByName(getArguments().getString("fieldName")));

//            DynamicMessage.Builder mTmp = DynamicMessage.getDefaultInstance(type).toBuilder();
//            try {
//                mTmp = DynamicMessage.parseFrom(type, getArguments().getByteArray("data")).toBuilder();
//            } catch (InvalidProtocolBufferException e) {
//                e.printStackTrace();
//            }

        //    final DynamicMessage.Builder m = mTmp;
            NumberFormat formatter = new DecimalFormat();
            formatter.setMaximumFractionDigits(6);
            for (final Descriptors.FieldDescriptor child : type.getFields()) {
                EditTextPreference preference = new EditTextPreference(screen.getContext());
                preference.setTitle(child.getName());
                preference.setSummary(formatter.format(m.getField(child)));
                preference.setDialogTitle("Enter " + child.getName() + " value");
                preference.getEditText().setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                preference.setOnPreferenceChangeListener((p, newValue) -> {

                    switch (child.getJavaType()) {
                        case DOUBLE: m.setField(child , Double.valueOf((String)newValue)); break;
                        case INT: m.setField(child , Integer.valueOf((String)newValue)); break;
                        case LONG: m.setField(child , Long.valueOf((String)newValue)); break;
                        case FLOAT: m.setField(child , Float.valueOf((String)newValue)); break;
                    }
                    p.setSummary((String)newValue);
                    return true;
                });
                preference.setOnPreferenceClickListener(p -> {
                    String value = formatter.format(m.getField(child));
                    ((EditTextPreference)p).getEditText().setText(value);
                    return true;
                });
                screen.addPreference(preference);
            }
        }


        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }
}
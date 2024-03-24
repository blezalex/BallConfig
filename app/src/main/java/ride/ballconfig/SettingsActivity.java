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
import android.preference.PreferenceScreen;
import android.preference.RingtonePreference;
import androidx.appcompat.app.ActionBar;
import android.text.InputType;
import android.text.TextUtils;
import android.view.MenuItem;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;

import static com.google.protobuf.Descriptors.FieldDescriptor.JavaType;

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

    Descriptors.Descriptor configDescriptor;
    DynamicMessage.Builder configBuilder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (getIntent() == null) {
            return;
        }

        byte[] cfg = getIntent().getByteArrayExtra("config");
        byte[] compressedConfigDescriptor = getIntent().getByteArrayExtra("configDescriptor");

        try {
            configDescriptor = DescriptorUtils.parseConfigDescriptor(compressedConfigDescriptor);
            configBuilder = DynamicMessage.newBuilder(configDescriptor);
            configBuilder.mergeFrom(cfg);

        } catch (Exception e) {
            e.printStackTrace();
        }

        super.onCreate(savedInstanceState);
        setupActionBar();

        DescriptorUtils.setFieldsToTheirDefaultValues(configBuilder);
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
            args.putByteArray("data", ((Message) configBuilder.getField(field)).toByteArray());
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


    static Descriptors.Descriptor findType(Descriptors.Descriptor descriptor, String name) {
        Descriptors.FileDescriptor fd = descriptor.getFile();
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

    static String formatValue(MessageOrBuilder m, Descriptors.FieldDescriptor child) {
        NumberFormat formatter = new DecimalFormat();
        formatter.setMaximumFractionDigits(6);
        formatter.setGroupingUsed(false);
        Object field = m.getField(child);
        if (child.getJavaType() == JavaType.BOOLEAN) {
            return field.toString();
        }
        return formatter.format(field);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class ProtoPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            SettingsActivity activity = (SettingsActivity)getActivity();
            setHasOptionsMenu(true);

            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getActivity());
            setPreferenceScreen(screen);
            Descriptors.Descriptor type = findType(activity.configBuilder.getDescriptorForType(), getArguments().getString("type"));

            String fieldName = getArguments().getString("fieldName");
            Descriptors.FieldDescriptor field = activity.configBuilder.getDescriptorForType()
                    .findFieldByName(fieldName);

            final Message.Builder m = ((DynamicMessage)activity.configBuilder.getField(field)).toBuilder();

            for (final Descriptors.FieldDescriptor child : type.getFields()) {
                EditTextPreference preference = new EditTextPreference(screen.getContext());
                preference.setTitle(child.getName());
                preference.setSummary( formatValue(m, child));
                preference.setDialogTitle("Enter " + child.getName() + " value");

                Descriptors.FieldDescriptor.JavaType fieldType = child.getJavaType();
                if (fieldType != JavaType.BOOLEAN) {
                    preference.getEditText().setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                }
                preference.setOnPreferenceChangeListener((p, newValue) -> {
                    switch (fieldType) {
                        case DOUBLE: m.setField(child , Double.valueOf((String)newValue)); break;
                        case INT: m.setField(child , Integer.valueOf((String)newValue)); break;
                        case LONG: m.setField(child , Long.valueOf((String)newValue)); break;
                        case FLOAT: m.setField(child , Float.valueOf((String)newValue)); break;
                        case BOOLEAN: m.setField(child , Boolean.valueOf((String)newValue)); break;
                    }
                    p.setSummary((String)newValue);
                    activity.configBuilder.setField(field, m.build());
                    return true;
                });
                preference.setOnPreferenceClickListener(p -> {
                    String value = formatValue(m , child);
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
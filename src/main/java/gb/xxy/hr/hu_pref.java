package gb.xxy.hr;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.view.View;

public class hu_pref extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

        //getFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit();


		final ListPreference itemList = (ListPreference)findPreference("lightsens");
		final EditTextPreference itemList2 = (EditTextPreference)findPreference("luxval");
		final EditTextPreference itemList3 = (EditTextPreference)findPreference("luxsensibility");
		if (itemList.findIndexOfValue(itemList.getValue())!=0) {
			itemList2.setEnabled(false);
			itemList3.setEnabled(false);
		}
		itemList.setOnPreferenceChangeListener(new	Preference.OnPreferenceChangeListener() {
			 @Override
		  public boolean onPreferenceChange(Preference preference, Object newValue) {
			final String val = newValue.toString();
			int index = itemList.findIndexOfValue(val);
			if(index==0) {
				itemList2.setEnabled(true);
				itemList3.setEnabled(true);
			}
			else {
				itemList2.setEnabled(false);
				itemList3.setEnabled(false);
			}
			return true;
		  }
		});
    }

    public void exitpref(View v)
	{
		this.finish();
	}
    public static class MyPreferenceFragment extends PreferenceFragment
    {
        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
        }
    }

}
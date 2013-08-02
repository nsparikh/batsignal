package com.neenaparikh.locationsender;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.neenaparikh.locationsender.util.Constants;


public class MainActivity extends Activity {
	private SharedPreferences sharedPrefs;
	private GoogleAccountCredential credential;

	private ProgressDialog pDialog;
	private Button retryButton;

	// TODO change layout / appearance to look like more of a launch screen
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Make sure Retry button isn't visible to start
		retryButton = (Button) findViewById(R.id.main_activity_retry_button);
		retryButton.setVisibility(View.GONE);

		// Check to see if user is authenticated / signed in
		sharedPrefs = getSharedPreferences(Constants.SHARED_PREFERENCES_NAME, 0);
		credential = GoogleAccountCredential.usingAudience(this, Constants.CREDENTIAL_AUDIENCE);
		setAccountName(sharedPrefs.getString(Constants.SHARED_PREFERENCES_ACCOUNT_NAME_KEY, null));

		if (credential.getSelectedAccountName() != null) {
			// Already signed in, begin app!
			onSignIn();	
		} else {
			// Not signed in, request an account by showing an account picker
			startActivityForResult(credential.newChooseAccountIntent(), Constants.REQUEST_ACCOUNT_PICKER);
		}
	}

	/**
	 * Called when the user has successfully authenticated / signed in.
	 */
	private void onSignIn() {
		// Show a progress dialog
		pDialog = new ProgressDialog(this);
		pDialog.setMessage("Registering...");
		pDialog.setCancelable(false);
		pDialog.show();

		// Register the device
		GCMIntentService.register(getApplicationContext());
	}

	/**
	 * Called when startActivityForResult() is called
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode) {
		case Constants.REQUEST_ACCOUNT_PICKER:
			if (data != null && data.getExtras() != null) {
				String accountName = data.getExtras().getString(AccountManager.KEY_ACCOUNT_NAME);
				if (accountName != null) {
					setAccountName(accountName);
					SharedPreferences.Editor editor = sharedPrefs.edit();
					editor.putString(Constants.SHARED_PREFERENCES_ACCOUNT_NAME_KEY, accountName);
					editor.commit();
					onSignIn();
				}
			}
			break;
		}
	}

	/**
	 * Sets the account name in the Shared Preferences and the Google Accounts Credential object
	 * @param accountName
	 */
	private void setAccountName(String accountName) {
		SharedPreferences.Editor editor = sharedPrefs.edit();
		editor.putString(Constants.SHARED_PREFERENCES_ACCOUNT_NAME_KEY, accountName);
		editor.commit();

		credential.setSelectedAccountName(accountName);
	}

	/**
	 * Called when a new intent is received to start this activity.
	 * 
	 * For this to work, android:launchMode="singleTop" must be set for this activity
	 * in AndroidManifest.xml.
	 */
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		pDialog.dismiss();

		// First determine whether the intent came from GCMIntentService 
		if (intent.getBooleanExtra(Constants.GCM_INTENT_SERVICE_KEY, false)) {

			if (intent.getBooleanExtra(Constants.REGISTER_INTENT_SUCCESS_KEY, false)) {
				// If registration was successful, start NearbyPlacesActivity
				Intent nearbyPlacesIntent = new Intent(this, NearbyPlacesActivity.class);
				startActivity(nearbyPlacesIntent);
				finish();
			} else {
				// Otherwise, prompt the user to retry
				Toast.makeText(MainActivity.this, "Registration unsuccessful. Retry?", Toast.LENGTH_LONG).show();
				retryButton.setVisibility(View.VISIBLE);
			}
		}
	}

	/**
	 * Called when the user clicks the "Retry" button. Just relaunches this activity.
	 * @author neenaparikh
	 *
	 */
	public void onClickRetry(View view) {
		onSignIn();
	}

}

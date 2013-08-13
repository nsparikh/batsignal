package com.neenaparikh.locationsender.comms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.neenaparikh.locationsender.GCMIntentService;
import com.neenaparikh.locationsender.NearbyPlacesActivity;
import com.neenaparikh.locationsender.messageEndpoint.MessageEndpoint;
import com.neenaparikh.locationsender.messageEndpoint.model.BooleanResult;
import com.neenaparikh.locationsender.model.Person;
import com.neenaparikh.locationsender.model.Place;
import com.neenaparikh.locationsender.util.Constants;

/**
 * An asynchronous task to send a message in a background thread.
 * Constructor takes in the Place that is the subject of the message
 * Task takes in a list of recipients as Person objects and returns a List of 
 * 	Person objects that represents those who the message couldn't be sent to
 * 	(because they have not registered with the app).
 * 
 * @author neenaparikh
 *
 */
public class SendMessageTask extends AsyncTask<Person, Void, Boolean> {
	private MessageEndpoint endpoint;
	private boolean isTextFallbackEnabled;
	private Activity activity;
	private Place place;
	private Set<String> successfulRecipientIds;
	private TelephonyManager telephonyManager;
    

	/**
	 * Default constructor. Takes in the Place object.
	 * @param activity The activity that called the task
	 * @param place The given Place object
	 */
	public SendMessageTask(Activity activity, Place place) {
		this.endpoint = GCMIntentService.getAuthMessageEndpoint(activity);
		this.isTextFallbackEnabled = activity.getSharedPreferences(Constants.SHARED_PREFERENCES_NAME, 0)
				.getBoolean(Constants.SHARED_PREFERENCES_TEXT_ENABLED_KEY, false);
		this.activity = activity;
		this.place = place;
		this.successfulRecipientIds = new HashSet<String>();
		this.telephonyManager = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
	}

	/**
	 * Sends the message. Takes in a list of Person objects.
	 */
	@Override
	protected Boolean doInBackground(Person... params) {
		boolean success = true;

		// Iterate through each person and send the message individually
		ArrayList<Person> textMessageRecipients = new ArrayList<Person>();
		for (Person recipient : params) {
			if (recipient.isRegistered()) {
				try {
					// Go through each of this recipient's registered devices and send the message
					for (String deviceId : recipient.getDeviceRegistrationIdList()) {
						BooleanResult result = endpoint.sendMessage(place.getName(), place.getLatitude(), place.getLongitude(), 
								place.getDuration(), deviceId).execute();
						if (result.getResult()) successfulRecipientIds.add(deviceId);
						else success = false;
					}
				} catch (IOException e) {
					Log.e(SendMessageTask.class.getName(), "IOException: " + e.getMessage());
					success = false;
				}
			} else if (isTextFallbackEnabled && recipient.hasPhones()) {
				textMessageRecipients.add(recipient);
			}
		}
		
		// Send text message to recipients who are unregistered, if any
		int numTextRecipients = textMessageRecipients.size();
		if (numTextRecipients > 0) {
			SendTextMessageTask textTask = new SendTextMessageTask(activity, place);
			textTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, textMessageRecipients.toArray(new Person[numTextRecipients]));
			try {
				textTask.get();
			} catch (InterruptedException e) {
				Log.e(SendMessageTask.class.getName(), "InterruptedException: " + e.getMessage());
			} catch (ExecutionException e) {
				Log.e(SendMessageTask.class.getName(), "ExecutionException: " + e.getMessage());
			}
		}
		

		return success;
	}

	/**
	 * Called after the task finishes.
	 */
	@Override
	protected void onPostExecute(Boolean result) {
		
		// Update lastContacted of all successful recipient devices in sharedprefs
		// Map each device ID to the last contact time
		SharedPreferences.Editor editor = activity.getSharedPreferences(Constants.SHARED_PREFERENCES_NAME, 0).edit();
		for (String deviceId : successfulRecipientIds) {
			editor.putLong(deviceId, System.currentTimeMillis());
		}
		editor.commit();
		
		// Notify the user if there is no SIM card
		if (telephonyManager.getSimState() == TelephonyManager.SIM_STATE_ABSENT)
			Toast.makeText(activity, "Need SIM card to send text messages", Toast.LENGTH_SHORT).show();

		// Send was successful
		if (result) {
		
			if (successfulRecipientIds.size() > 0)
				Toast.makeText(activity, "FindMe notification sent!", Toast.LENGTH_SHORT).show();
			
			// Launch back to NearbyPlacesActivity then close this activity
			Intent intent = new Intent(activity, NearbyPlacesActivity.class);
			activity.startActivity(intent);
			activity.finish();
		} else {
			// Send was unsuccessful
			Toast.makeText(activity, "Unable to send message. Please try again soon.", Toast.LENGTH_LONG).show();
		}
	}
}

/** ============================================================== */
package com.sendbird.android.sample.main;
/** ============================================================== */
import android.app.Application;

import com.sendbird.android.SendBird;
import com.sendbird.android.sample.utils.PreferenceUtils;

import static android.provider.UserDictionary.Words.APP_ID;

/** ============================================================== */
/*public class THLApp extends Application
{
	private static final String APP_ID = "9DA1B1F4-0BE6-4DA8-82C5-2E81DAB56F23"; // US-1 Demo
	public static final String VERSION = "3.0.40";
	public static THLApp App		= null;
	public static THLConfig Config	= null;
	

	public static THLApp getApp()
	{
		return App;
	}
	

	@Override
	public void onCreate()
	{
		super.onCreate();
		PreferenceUtils.init(getApplicationContext());

		SendBird.init(APP_ID, getApplicationContext());
		
		App		= this;
		Config	= new THLConfig(this);
		
		Config.loadSettings();
	}


	@Override
	public void onTerminate()
	{
		Config.saveSettings();
		
		super.onTerminate();
	}
}*/




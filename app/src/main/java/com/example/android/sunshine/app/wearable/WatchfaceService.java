package com.example.android.sunshine.app.wearable;

import android.util.Log;

import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

public class WatchfaceService extends WearableListenerService {


    private static final String TAG = WatchfaceService.class.getSimpleName();


    private static final String ARG_WATCHFACE_URI = "/sunshine_watchface_data";


    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG,"onDataChanged");
        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                String path = dataEvent.getDataItem().getUri().getPath();
                Log.d(TAG, path);
                if (path.equals(ARG_WATCHFACE_URI)) {
                    Log.d(TAG,"data found at:" + path);
                    DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                    for (String key : dataMap.keySet()) Log.d(TAG,"" + key + ":" + dataMap.getString(key));
                    SunshineSyncAdapter.syncImmediately(this);
                }
                else Log.e(TAG,"uri not equal to:" + ARG_WATCHFACE_URI + "; it was:" + path);
            }
        }
    }
}
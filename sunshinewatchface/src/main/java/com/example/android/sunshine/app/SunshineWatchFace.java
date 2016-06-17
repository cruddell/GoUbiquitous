/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.example.android.sunshine.app.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;


import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final String TAG = "SunshineWatchFace";
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    private static final String ARG_WATCHFACE_URI = "/sunshine_watchface_data";
    private static final String ARG_WEATHER_URI = "/sunshine_weather_data";
    private static final String ARG_UUID = "uuid";
    private static final String ARG_WEATHER_ID = "weatherId";
    private static final String ARG_HIGH_TEMP = "high";
    private static final String ARG_LOW_TEMP = "low";
    private static final String ARG_HIGH_DOUBLE = "highDouble";
    private static final String ARG_LOW_DOUBLE = "lowDouble";
    private static final String ARG_IS_METRIC = "isMetric";

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }


    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mDatePaint;
        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        float mXOffset;
        float mYOffset;
        float mSeparatorLineSize;

        Paint mTextTempHighPaint;
        Paint mTextTempLowPaint;
        Paint mTextTempLowAmbientPaint;

        String mWeatherUUID = "";
        Bitmap mWeatherIcon;
        String mWeatherHigh = "";
        String mWeatherLow = "";
        String mWeatherText = "";
        float mIconSize;
        float mPaddingHorizontal;

        GoogleApiClient mGoogleApiClient;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);



            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mSeparatorLineSize = resources.getDimension(R.dimen.sunshine_horizontal_line_size);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mDatePaint = new Paint();
            mDatePaint = createTextPaint(resources.getColor(R.color.date_text));


            mTextTempHighPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mTextTempLowPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mTextTempLowAmbientPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mIconSize = resources.getDimension(R.dimen.sunshine_weather_icon_size);
            mPaddingHorizontal = resources.getDimension(R.dimen.padding_horiz);


            mTime = new Time();

            //get data
            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();



        }



        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                //connect to device
                Log.e(TAG,"attempting to connect to device to retrieve weather data...");
                mGoogleApiClient.connect();

                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();

                //disconnect from device
                Log.e(TAG,"disconnecting from device...");
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
            mTextPaint.setTextAlign(Paint.Align.CENTER);

            mDatePaint.setTextSize(resources.getDimension(R.dimen.sunshine_date_text_size));
            mDatePaint.setTextAlign(Paint.Align.CENTER);

            mTextTempHighPaint.setTextSize(resources.getDimension(R.dimen.sunshine_date_text_size));
            mTextTempLowPaint.setTextSize(resources.getDimension(R.dimen.sunshine_date_text_size));
            mTextTempHighPaint.setTextAlign(Paint.Align.LEFT);
            mTextTempLowPaint.setTextAlign(Paint.Align.LEFT);


        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            Log.d(TAG,"onTimeTick()");
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = SunshineWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }



            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }


            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();

            String text = String.format("%d:%02d", mTime.hour == 0 ? 12 : mTime.hour, mTime.minute);
            String dateText = mTime.format("%a, %b %d %Y").toUpperCase();


            Rect timeBounds = new Rect();
            float width = canvas.getWidth();
            float x = width/2, y = 100;
            mTextPaint.getTextBounds(text, 0, text.length(), timeBounds); // Measure the text

            canvas.drawText(text, x, mYOffset + timeBounds.height() * 0.5f, mTextPaint); // Draw the text


            y = mYOffset + timeBounds.height() + 10;
            Rect dateBounds = new Rect();
            mDatePaint.getTextBounds(dateText, 0, dateText.length(), dateBounds);
            canvas.drawText(dateText, x, y, mDatePaint);


            //draw short horizontal separator line
            float x1 = (width-mSeparatorLineSize)/2;
            float x2 = (width+mSeparatorLineSize)/2;
            float lineY = y + dateBounds.height();
            canvas.drawLine(x1,lineY,x2,lineY, mDatePaint);

            Rect tempBounds = new Rect();
            mTextTempHighPaint.getTextBounds(mWeatherText, 0, mWeatherText.length(), tempBounds);
            float weatherY = lineY + 40;
            float weatherSize = tempBounds.width() + mIconSize + mPaddingHorizontal;
            float iconX = (width-weatherSize)/2;
            float tempX = iconX + mPaddingHorizontal + mIconSize;
            canvas.drawText(mWeatherText, tempX, weatherY, mTextTempHighPaint);
            if (mWeatherIcon!=null) canvas.drawBitmap(mWeatherIcon, iconX, weatherY-mIconSize, mTextTempHighPaint);
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        //methods to get data from phone
        @Override
        public void onConnected(final Bundle bundle) {
            Log.d(TAG,"onConnected");
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            requestWeatherInfo();
        }

        @Override
        public void onConnectionSuspended(final int i) {
            Log.e(TAG, "onConnectionSuspended(" + i + ")");
        }

        @Override
        public void onDataChanged(final DataEventBuffer dataEventBuffer) {
            Log.d(TAG, "onDataChanged");
            for (DataEvent dataEvent : dataEventBuffer) {
                Log.d(TAG,"dataEvent:" + dataEvent.getType());
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                    String path = dataEvent.getDataItem().getUri().getPath();
                    Log.d(TAG,"path = " + path);
                    Log.d(TAG,"data = " + dataMap.toString());

                    if (path.equals(ARG_WEATHER_URI)) {

                        if (dataMap.containsKey(ARG_LOW_TEMP)) {
                            mWeatherLow = dataMap.getString(ARG_LOW_TEMP);
                            Log.d(TAG,"found low temp:" + mWeatherLow);
                        }

                        double mWeatherHighDouble = 0;
                        double mWeatherLowDouble = 0;
                        if (dataMap.containsKey(ARG_HIGH_DOUBLE)) mWeatherHighDouble = dataMap.getDouble(ARG_HIGH_DOUBLE);
                        if (dataMap.containsKey(ARG_LOW_DOUBLE)) mWeatherLowDouble = dataMap.getDouble(ARG_LOW_DOUBLE);
                        boolean isMetric = false;
                        if (dataMap.containsKey(ARG_IS_METRIC)) isMetric = dataMap.getBoolean(ARG_IS_METRIC);
                        mWeatherHigh = Utility.formatTemperature(SunshineWatchFace.this, mWeatherHighDouble, isMetric);
                        mWeatherLow = Utility.formatTemperature(SunshineWatchFace.this, mWeatherLowDouble, isMetric);

                        mWeatherText = mWeatherHigh + " / " + mWeatherLow + "  " + (isMetric ? "(C)" : "(F)");


                        if (dataMap.containsKey(ARG_WEATHER_ID)) {
                            int weatherId = dataMap.getInt(ARG_WEATHER_ID);
                            Drawable b = getResources().getDrawable(Utility.getIconResourceForWeatherCondition(weatherId));
                            Bitmap icon = ((BitmapDrawable) b).getBitmap();
                            float scaledWidth = (mTextTempHighPaint.getTextSize() / icon.getHeight()) * icon.getWidth();
                            mWeatherIcon = Bitmap.createScaledBitmap(icon, (int) scaledWidth, (int) mTextTempHighPaint.getTextSize(), true);

                            Log.d(TAG,"found icon:" + weatherId);

                        }

                        invalidate();
                    }
                    else Log.e(TAG,"path not equal to :" + ARG_WEATHER_URI + ": instead it is:" + path);
                }
            }
        }

        @Override
        public void onConnectionFailed(final ConnectionResult connectionResult) {
            Log.e(TAG, "onConnectionFailed:" + connectionResult.toString());
        }

        public void requestWeatherInfo() {
            Log.d(TAG,"requestWeatherInfo()");
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(ARG_WATCHFACE_URI);
            putDataMapRequest.getDataMap().putString(ARG_UUID, java.util.UUID.randomUUID().toString());
            PutDataRequest request = putDataMapRequest.asPutDataRequest();

            Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                    .setResultCallback(new com.google.android.gms.common.api.ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(DataApi.DataItemResult dataItemResult) {
                            if (!dataItemResult.getStatus().isSuccess()) {
                                android.util.Log.e(TAG,"error getting data:" + dataItemResult.getStatus());
                            } else {
                                android.util.Log.d(TAG, "data retrieved:" + dataItemResult.getDataItem().toString());
                                DataMap dataMap = DataMapItem.fromDataItem(dataItemResult.getDataItem()).getDataMap();
                                for (String key : dataMap.keySet()) Log.d(TAG,"" + key + ":" + dataMap.getString(key));
                            }
                        }
                    });
        }
    }
}

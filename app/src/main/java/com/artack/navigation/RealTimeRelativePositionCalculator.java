package com.artack.navigation;

import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import com.google.android.apps.location.gps.gnsslogger.GnssListener;
import com.google.location.lbs.gnss.gps.pseudorange.Ecef2LlaConverter;
import com.google.location.lbs.gnss.gps.pseudorange.GpsTime;
import com.google.location.lbs.gnss.gps.pseudorange.Lla2EcefConverter;

import Jama.Matrix;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.Calendar;

public class RealTimeRelativePositionCalculator implements GnssListener {


    /** Constants*/
    private static final double SPEED_OF_LIGHT_MPS = 299792458.0;
    private static final int SECONDS_IN_WEEK = 604800;
    private static final double LEAST_SQUARE_TOLERANCE_METERS = 4.0e-8;
    private static final int C_TO_N0_THRESHOLD_DB_HZ = 18;
    private static final int TOW_DECODED_MEASUREMENT_STATE_BIT = 3;

    private double[] mReferenceLocation = null;
    private double[] mReferenceLocationECEF = null;
    /**связаны с расчетом*/
    private double mArrivalTimeSinceGPSWeekNs = 0.0;
    private int mDayOfYear1To366 = 0;
    private int mGpsWeekNumber = 0;
    private long mArrivalTimeSinceGpsEpochNs = 0;
    private long mLargestTowNs = Long.MIN_VALUE;

    GnssMeasurement currentmeasurementBase;
    GnssMeasurement currentmeasurementObject;
    GnssClock currentclockBase;
    GnssClock currentclockObject;
    double[] positionSolutionECEF;
    Matrix GradientMatrix;//Used Jama matrix class
    RealMatrix HMatrix;
    RealMatrix SolutionMatrix;

    public RealTimeRelativePositionCalculator(GnssMeasurement measurement) {
    }
    /** Iterative WLS method for relative navigation solution*/
    public void WLSSolution()
    {

    }

    /** Sets a rough location of the receiver that can be used to request SUPL assistance data */
    public void setReferencePosition(double lat, double lng, double alt) {
        if (mReferenceLocation == null) {
            mReferenceLocation = new double[3];
            mReferenceLocationECEF = new double[3];
        }
        mReferenceLocation[0] = lat;
        mReferenceLocation[1] = lng;
        mReferenceLocation[2] = alt;
        Ecef2LlaConverter.GeodeticLlaValues geodeticLlaValues = new Ecef2LlaConverter.GeodeticLlaValues(lat,lng,alt);
        mReferenceLocationECEF = Lla2EcefConverter.convertFromLlaToEcefMeters(geodeticLlaValues);
        Log.e("RefLocation:",String.valueOf(mReferenceLocationECEF[0]) +" " +
                String.valueOf(mReferenceLocationECEF[1])+ " " +
                String.valueOf(mReferenceLocationECEF[2]));
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    /**Отсюда можно получить первое решение, для получения хорошего начального приближения*/
    @Override
    public void onLocationChanged(Location location) {
        if (location.getProvider().equals(LocationManager.GPS_PROVIDER)){
            if(mReferenceLocationECEF == null)
            setReferencePosition(location.getLatitude(),location.getLongitude(),location.getAltitude());
        }
    }

    @Override
    public void onLocationStatusChanged(String provider, int status, Bundle extras) {
    }

    /**тут надо замутить магию */
    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
        GnssClock gnssClock = event.getClock();
        mArrivalTimeSinceGpsEpochNs = gnssClock.getTimeNanos() - gnssClock.getFullBiasNanos();

        for (GnssMeasurement measurement : event.getMeasurements()) {
            // ignore any measurement if it is not from GPS constellation
            if (measurement.getConstellationType() != GnssStatus.CONSTELLATION_GPS) {
                continue;
            }
            // ignore raw data if time is zero, if signal to noise ratio is below threshold or if
            // TOW is not yet decoded
            if (measurement.getCn0DbHz() >= C_TO_N0_THRESHOLD_DB_HZ
                    && (measurement.getState() & (1L << TOW_DECODED_MEASUREMENT_STATE_BIT)) != 0) {

                // calculate day of year and Gps week number needed for the least square
                GpsTime gpsTime = new GpsTime(mArrivalTimeSinceGpsEpochNs);
                // Gps weekly epoch in Nanoseconds: defined as of every Sunday night at 00:00:000
                long gpsWeekEpochNs = GpsTime.getGpsWeekEpochNano(gpsTime);
                mArrivalTimeSinceGPSWeekNs = mArrivalTimeSinceGpsEpochNs - gpsWeekEpochNs;
                mGpsWeekNumber = gpsTime.getGpsWeekSecond().first;
                // calculate day of the year between 1 and 366
                Calendar cal = gpsTime.getTimeInCalendar();
                mDayOfYear1To366 = cal.get(Calendar.DAY_OF_YEAR);

                long receivedGPSTowNs = measurement.getReceivedSvTimeNanos();
                if (receivedGPSTowNs > mLargestTowNs) {
                    mLargestTowNs = receivedGPSTowNs;
                }
            }
        }
    }

    @Override
    public void onGnssMeasurementsStatusChanged(int status) {
    }

    @Override
    public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {
    }

    @Override
    public void onGnssNavigationMessageStatusChanged(int status) {
    }

    @Override
    public void onGnssStatusChanged(GnssStatus gnssStatus) {
    }

    @Override
    public void onListenerRegistration(String listener, boolean result) {
    }

    @Override
    public void onNmeaReceived(long l, String s) {
    }

    @Override
    public void onTTFFReceived(long l) {
    }

}
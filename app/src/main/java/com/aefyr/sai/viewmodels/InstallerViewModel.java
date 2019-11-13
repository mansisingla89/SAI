package com.aefyr.sai.viewmodels;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.aefyr.sai.installer.ApkSourceBuilder;
import com.aefyr.sai.installer2.base.SaiPiSessionObserver;
import com.aefyr.sai.installer2.base.model.SaiPiSessionParams;
import com.aefyr.sai.installer2.base.model.SaiPiSessionState;
import com.aefyr.sai.installer2.impl.FlexSaiPackageInstaller;
import com.aefyr.sai.model.apksource.ApkSource;
import com.aefyr.sai.utils.Event;
import com.aefyr.sai.utils.PreferencesHelper;
import com.aefyr.sai.utils.Utils;

import java.io.File;
import java.util.List;

public class InstallerViewModel extends AndroidViewModel implements SaiPiSessionObserver {
    public static final String EVENT_PACKAGE_INSTALLED = "package_installed";
    public static final String EVENT_INSTALLATION_FAILED = "installation_failed";

    private FlexSaiPackageInstaller mInstaller;
    private PreferencesHelper mPrefsHelper;

    public enum InstallerState {
        IDLE, INSTALLING
    }

    private MutableLiveData<List<SaiPiSessionState>> mSessions = new MutableLiveData<>();

    private MutableLiveData<InstallerState> mState = new MutableLiveData<>();
    private MutableLiveData<Event<String[]>> mEvents = new MutableLiveData<>();

    public InstallerViewModel(@NonNull Application application) {
        super(application);
        mPrefsHelper = PreferencesHelper.getInstance(getApplication());

        mInstaller = FlexSaiPackageInstaller.getInstance(getApplication());
        mInstaller.registerSessionObserver(this);
    }

    public LiveData<InstallerState> getState() {
        return mState;
    }

    public LiveData<Event<String[]>> getEvents() {
        return mEvents;
    }

    public LiveData<List<SaiPiSessionState>> getSessions() {
        return mSessions;
    }

    public void installPackages(List<File> apkFiles) {
        ApkSource apkSource = new ApkSourceBuilder(getApplication())
                .fromApkFiles(apkFiles)
                .setSigningEnabled(mPrefsHelper.shouldSignApks())
                .build();

        String sessionId = mInstaller.createSessionOnInstaller(mPrefsHelper.getInstaller(), new SaiPiSessionParams(apkSource));
        mInstaller.enqueueSession(sessionId);
    }

    public void installPackagesFromZip(List<File> zipWithApkFiles) {
        for (File zipFile : zipWithApkFiles) {
            ApkSource apkSource = new ApkSourceBuilder(getApplication())
                    .fromZipFile(zipFile)
                    .setZipExtractionEnabled(mPrefsHelper.shouldExtractArchives())
                    .setSigningEnabled(mPrefsHelper.shouldSignApks())
                    .build();

            String sessionId = mInstaller.createSessionOnInstaller(mPrefsHelper.getInstaller(), new SaiPiSessionParams(apkSource));
            mInstaller.enqueueSession(sessionId);
        }
    }

    public void installPackagesFromContentProviderZip(Uri zipContentUri) {
        ApkSource apkSource = new ApkSourceBuilder(getApplication())
                .fromZipContentUri(zipContentUri)
                .setZipExtractionEnabled(mPrefsHelper.shouldExtractArchives())
                .setSigningEnabled(mPrefsHelper.shouldSignApks())
                .build();

        String sessionId = mInstaller.createSessionOnInstaller(mPrefsHelper.getInstaller(), new SaiPiSessionParams(apkSource));
        mInstaller.enqueueSession(sessionId);
    }

    public void installPackagesFromContentProviderUris(List<Uri> apkUris) {
        ApkSource apkSource = new ApkSourceBuilder(getApplication())
                .fromApkContentUris(apkUris)
                .setSigningEnabled(mPrefsHelper.shouldSignApks())
                .build();

        String sessionId = mInstaller.createSessionOnInstaller(mPrefsHelper.getInstaller(), new SaiPiSessionParams(apkSource));
        mInstaller.enqueueSession(sessionId);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mInstaller.unregisterSessionObserver(this);
    }

    @Override
    public void onSessionStateChanged(SaiPiSessionState state) {
        switch (state.status()) {
            case CREATED:
            case QUEUED:
            case INSTALLING:
                mState.setValue(InstallerState.INSTALLING);
                break;
            case INSTALLATION_SUCCEED:
                mState.setValue(InstallerState.IDLE);
                mEvents.setValue(new Event<>(new String[]{EVENT_PACKAGE_INSTALLED, state.packageName()}));
                break;
            case INSTALLATION_FAILED:
                mState.setValue(InstallerState.IDLE);
                mEvents.setValue(new Event<>(new String[]{EVENT_INSTALLATION_FAILED, state.exception() == null ? null : Utils.throwableToString(state.exception())}));
                break;
        }

        mSessions.setValue(mInstaller.getSessions());
    }
}

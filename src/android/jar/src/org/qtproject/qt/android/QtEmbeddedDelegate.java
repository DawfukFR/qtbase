// Copyright (C) 2024 The Qt Company Ltd.
// SPDX-License-Identifier: LicenseRef-Qt-Commercial OR LGPL-3.0-only OR GPL-2.0-only OR GPL-3.0-only

package org.qtproject.qt.android;

import static org.qtproject.qt.android.QtNative.ApplicationState.*;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashMap;

class QtEmbeddedDelegate extends QtActivityDelegateBase implements QtNative.AppStateDetailsListener {
    // TODO simplistic implementation with one QtView, expand to support multiple views QTBUG-117649
    private QtView m_view;
    private long m_rootWindowRef = 0L;
    private QtNative.ApplicationStateDetails m_stateDetails;

    private static native void createRootWindow(View rootView);
    static native void deleteWindow(long windowReference);

    public QtEmbeddedDelegate(Activity context) {
        super(context);

        m_stateDetails = QtNative.getStateDetails();
        QtNative.registerAppStateListener(this);

        m_activity.getApplication().registerActivityLifecycleCallbacks(
        new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(Activity activity) {}

            @Override
            public void onActivityResumed(Activity activity) {
                if (m_activity == activity && m_stateDetails.isStarted) {
                    QtNative.setApplicationState(ApplicationActive);
                    QtNative.updateWindow();
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (m_activity == activity && m_stateDetails.isStarted) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
                        !activity.isInMultiWindowMode()) {
                        QtNative.setApplicationState(ApplicationInactive);
                    }
                }
            }

            @Override
            public void onActivityStopped(Activity activity) {
                if (m_activity == activity && m_stateDetails.isStarted) {
                    QtNative.setApplicationState(ApplicationSuspended);
                }
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

            @Override
            public void onActivityDestroyed(Activity activity) {
                if (m_activity == activity && m_stateDetails.isStarted) {
                    m_activity.getApplication().unregisterActivityLifecycleCallbacks(this);
                    QtNative.unregisterAppStateListener(QtEmbeddedDelegate.this);
                    QtEmbeddedDelegateFactory.remove(m_activity);
                    QtNative.terminateQt();
                    QtNative.setActivity(null);
                    QtNative.getQtThread().exit();
                    onDestroy();
                }
            }
        });
    }

    @Override
    public void onAppStateDetailsChanged(QtNative.ApplicationStateDetails details) {
        m_stateDetails = details;
        if (m_stateDetails.nativePluginIntegrationReady) {
            QtNative.runAction(() -> {
                DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
                QtDisplayManager.setApplicationDisplayMetrics(m_activity,
                                                              metrics.widthPixels,
                                                              metrics.heightPixels);
              if (m_view != null)
                  createRootWindow(m_view);
            });
        }
    }

    @Override
    void startNativeApplicationImpl(String appParams, String mainLib)
    {
        QtNative.startApplication(appParams, mainLib);
    }

    @Override
    QtAccessibilityDelegate createAccessibilityDelegate()
    {
        // FIXME make QtAccessibilityDelegate window based or verify current way works
        // also for child windows: QTBUG-120685
        return null;
    }

    @UsedFromNativeCode
    @Override
    QtLayout getQtLayout()
    {
        // TODO verify if returning m_view here works, this is used by the androidjniinput
        // when e.g. showing a keyboard, so depends on getting the keyboard focus working
        // QTBUG-118873
        return m_view;
    }

    public void queueLoadWindow()
    {
        if (m_stateDetails.nativePluginIntegrationReady)  {
            createRootWindow(m_view);
        }
    }

    void setView(QtView view) {
        m_view = view;
        registerGlobalFocusChangeListener(m_view);
    }

    public void setRootWindowRef(long ref) {
        m_rootWindowRef = ref;
    }

    public void onDestroy() {
        if (m_rootWindowRef != 0L)
            deleteWindow(m_rootWindowRef);
        m_rootWindowRef = 0L;
    }
}
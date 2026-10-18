#!/usr/bin/env node
/**
 * scripts/release-security-check.test.mjs
 *
 * Automated test suite for release-security-check.mjs exported components validator.
 * Validates the narrow allowlist policy:
 * - MainActivity (exported=true) is allowed ONLY with exact canonical class name.
 * - TabbyRushTaskWidgetReceiver (exported=true) is allowed ONLY with exact canonical class name,
 *   exact APPWIDGET_UPDATE action inside <intent-filter>, no extra actions/categories/data,
 *   and exact @xml/task_widget_info or @xml/task-widget-info provider meta-data.
 * - All other exported activities, receivers, services, and providers are rejected.
 */

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { validateExportedComponents } from './release-security-check.mjs';

describe('Release Security Check: Exported Components Auditor', () => {

  it('passes on canonical release manifest with MainActivity and verified TabbyRushTaskWidgetReceiver', () => {
    const validManifest = `
      <manifest xmlns:android="http://schemas.android.com/apk/res/android">
        <application android:name=".TabbyRushApplication">
          <activity
              android:name=".MainActivity"
              android:exported="true">
              <intent-filter>
                  <action android:name="android.intent.action.MAIN" />
                  <category android:name="android.intent.category.LAUNCHER" />
              </intent-filter>
          </activity>

          <receiver
              android:name=".TabbyRushTaskWidgetReceiver"
              android:exported="true">
              <intent-filter>
                  <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
              </intent-filter>
              <meta-data
                  android:name="android.appwidget.provider"
                  android:resource="@xml/task_widget_info" />
          </receiver>

          <receiver android:name=".TaskSurfaceActionReceiver" android:exported="false" />
          <receiver android:name=".TaskSurfaceDismissReceiver" android:exported="false" />
          <receiver android:name=".TabbyRushAlarmReceiver" android:exported="false" />
        </application>
      </manifest>
    `;

    const issues = validateExportedComponents(validManifest);
    assert.deepEqual(issues, []);
  });

  it('rejects foreign-package lookalike for MainActivity (e.g. com.attacker.MainActivity)', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name="com.attacker.MainActivity" android:exported="true" />
        </application>
      </manifest>
    `;

    const issues = validateExportedComponents(manifest);
    assert.equal(issues.length, 1);
    assert.match(issues[0], /Exported activity is not MainActivity: com\.attacker\.MainActivity/);
  });

  it('rejects foreign-package lookalike for TabbyRushTaskWidgetReceiver', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name=".MainActivity" android:exported="true" />
          <receiver android:name="com.malicious.TabbyRushTaskWidgetReceiver" android:exported="true">
            <intent-filter>
              <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/task_widget_info" />
          </receiver>
        </application>
      </manifest>
    `;

    const issues = validateExportedComponents(manifest);
    assert.equal(issues.length, 1);
    assert.match(issues[0], /Exported receiver is not TabbyRushTaskWidgetReceiver: com\.malicious\.TabbyRushTaskWidgetReceiver/);
  });

  it('rejects any exported service', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name=".MainActivity" android:exported="true" />
          <service android:name=".InsecureSyncService" android:exported="true" />
        </application>
      </manifest>
    `;

    const issues = validateExportedComponents(manifest);
    assert.equal(issues.length, 1);
    assert.match(issues[0], /Non-main service is exported: \.InsecureSyncService/);
  });

  it('rejects any exported content provider', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name=".MainActivity" android:exported="true" />
          <provider android:name=".LeakContentProvider" android:exported="true" />
        </application>
      </manifest>
    `;

    const issues = validateExportedComponents(manifest);
    assert.equal(issues.length, 1);
    assert.match(issues[0], /Non-main provider is exported: \.LeakContentProvider/);
  });

  it('rejects exported receiver other than TabbyRushTaskWidgetReceiver', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name=".MainActivity" android:exported="true" />
          <receiver android:name=".ArbitraryExportedReceiver" android:exported="true" />
        </application>
      </manifest>
    `;

    const issues = validateExportedComponents(manifest);
    assert.equal(issues.length, 1);
    assert.match(issues[0], /Exported receiver is not TabbyRushTaskWidgetReceiver: \.ArbitraryExportedReceiver/);
  });

  it('rejects TabbyRushTaskWidgetReceiver missing APPWIDGET_UPDATE action', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name=".MainActivity" android:exported="true" />
          <receiver android:name=".TabbyRushTaskWidgetReceiver" android:exported="true">
            <intent-filter>
              <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/task_widget_info" />
          </receiver>
        </application>
      </manifest>
    `;

    const issues = validateExportedComponents(manifest);
    assert.ok(issues.some(i => i.includes('missing android.appwidget.action.APPWIDGET_UPDATE')));
    assert.ok(issues.some(i => i.includes('unauthorized exported action')));
  });

  it('rejects TabbyRushTaskWidgetReceiver containing unauthorized category in intent filter', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name=".MainActivity" android:exported="true" />
          <receiver android:name=".TabbyRushTaskWidgetReceiver" android:exported="true">
            <intent-filter>
              <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
              <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/task_widget_info" />
          </receiver>
        </application>
      </manifest>
    `;

    const issues = validateExportedComponents(manifest);
    assert.equal(issues.length, 1);
    assert.match(issues[0], /unauthorized <category>/);
  });

  it('rejects TabbyRushTaskWidgetReceiver containing unauthorized data in intent filter', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name=".MainActivity" android:exported="true" />
          <receiver android:name=".TabbyRushTaskWidgetReceiver" android:exported="true">
            <intent-filter>
              <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
              <data android:scheme="http" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/task_widget_info" />
          </receiver>
        </application>
      </manifest>
    `;

    const issues = validateExportedComponents(manifest);
    assert.equal(issues.length, 1);
    assert.match(issues[0], /unauthorized <data>/);
  });

  it('rejects TabbyRushTaskWidgetReceiver missing intent-filter', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name=".MainActivity" android:exported="true" />
          <receiver android:name=".TabbyRushTaskWidgetReceiver" android:exported="true">
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/task_widget_info" />
          </receiver>
        </application>
      </manifest>
    `;

    const issues = validateExportedComponents(manifest);
    assert.equal(issues.length, 1);
    assert.match(issues[0], /missing <intent-filter>/);
  });

  it('rejects TabbyRushTaskWidgetReceiver missing provider meta-data', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name=".MainActivity" android:exported="true" />
          <receiver android:name=".TabbyRushTaskWidgetReceiver" android:exported="true">
            <intent-filter>
              <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
          </receiver>
        </application>
      </manifest>
    `;

    const issues = validateExportedComponents(manifest);
    assert.equal(issues.length, 1);
    assert.match(issues[0], /missing required @xml\/task-widget-info provider meta-data/);
  });
  it('accepts TabbyRushTaskWidgetReceiver with compiled @ref/0x... provider meta-data from apkanalyzer', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name=".MainActivity" android:exported="true" />
          <receiver android:name=".TabbyRushTaskWidgetReceiver" android:exported="true">
            <intent-filter>
              <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@ref/0x7f100003" />
          </receiver>
        </application>
      </manifest>
    `;

    const issues = validateExportedComponents(manifest);
    assert.deepEqual(issues, []);
  });

  it('ignores unexported private receivers with intent filters', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name=".MainActivity" android:exported="true" />
          <receiver android:name=".ReminderRestoreReceiver" android:exported="false">
            <intent-filter>
              <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
          </receiver>
          <receiver android:name=".TaskSurfaceTimeReceiver" android:exported="false">
            <intent-filter>
              <action android:name="android.intent.action.TIME_SET" />
            </intent-filter>
          </receiver>
        </application>
      </manifest>
    `;

    const issues = validateExportedComponents(manifest);
    assert.deepEqual(issues, []);
  });

  it('passes on merged manifest with verified FirebaseInstanceIdReceiver and FirebaseInitProvider', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name="dev.tabbyrush.android.MainActivity" android:exported="true" />
          <receiver
            android:name="com.google.firebase.iid.FirebaseInstanceIdReceiver"
            android:exported="true"
            android:permission="com.google.android.c2dm.permission.SEND">
            <intent-filter>
              <action android:name="com.google.android.c2dm.intent.RECEIVE" />
              <category android:name="dev.tabbyrush.android" />
            </intent-filter>
          </receiver>
          <provider
            android:name="com.google.firebase.provider.FirebaseInitProvider"
            android:exported="false"
            android:authorities="dev.tabbyrush.android.firebaseinitprovider" />
        </application>
      </manifest>
    `;
    const issues = validateExportedComponents(manifest);
    assert.deepEqual(issues, []);
  });

  it('rejects FirebaseInstanceIdReceiver missing SEND permission', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name="dev.tabbyrush.android.MainActivity" android:exported="true" />
          <receiver
            android:name="com.google.firebase.iid.FirebaseInstanceIdReceiver"
            android:exported="true">
            <intent-filter>
              <action android:name="com.google.android.c2dm.intent.RECEIVE" />
            </intent-filter>
          </receiver>
        </application>
      </manifest>
    `;
    const issues = validateExportedComponents(manifest);
    assert.equal(issues.length, 1);
    assert.match(issues[0], /without required com.google.android.c2dm.permission.SEND permission/);
  });

  it('rejects FirebaseInstanceIdReceiver with unauthorized action', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name="dev.tabbyrush.android.MainActivity" android:exported="true" />
          <receiver
            android:name="com.google.firebase.iid.FirebaseInstanceIdReceiver"
            android:exported="true"
            android:permission="com.google.android.c2dm.permission.SEND">
            <intent-filter>
              <action android:name="com.google.android.c2dm.intent.RECEIVE" />
              <action android:name="com.google.android.c2dm.intent.REGISTRATION" />
            </intent-filter>
          </receiver>
        </application>
      </manifest>
    `;
    const issues = validateExportedComponents(manifest);
    assert.equal(issues.length, 1);
    assert.match(issues[0], /contains unauthorized exported action: com.google.android.c2dm.intent.REGISTRATION/);
  });

  it('rejects FirebaseInstanceIdReceiver with foreign category', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name="dev.tabbyrush.android.MainActivity" android:exported="true" />
          <receiver
            android:name="com.google.firebase.iid.FirebaseInstanceIdReceiver"
            android:exported="true"
            android:permission="com.google.android.c2dm.permission.SEND">
            <intent-filter>
              <action android:name="com.google.android.c2dm.intent.RECEIVE" />
              <category android:name="com.attacker.app" />
            </intent-filter>
          </receiver>
        </application>
      </manifest>
    `;
    const issues = validateExportedComponents(manifest);
    assert.equal(issues.length, 1);
    assert.match(issues[0], /contains unauthorized <category>: com.attacker.app/);
  });

  it('rejects FirebaseInstanceIdReceiver with data tag in intent-filter', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name="dev.tabbyrush.android.MainActivity" android:exported="true" />
          <receiver
            android:name="com.google.firebase.iid.FirebaseInstanceIdReceiver"
            android:exported="true"
            android:permission="com.google.android.c2dm.permission.SEND">
            <intent-filter>
              <action android:name="com.google.android.c2dm.intent.RECEIVE" />
              <data android:scheme="package" />
            </intent-filter>
          </receiver>
        </application>
      </manifest>
    `;
    const issues = validateExportedComponents(manifest);
    assert.equal(issues.length, 1);
    assert.match(issues[0], /contains unauthorized <data> in intent filter/);
  });

  it('rejects AppMeasurementReceiver when exported', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name="dev.tabbyrush.android.MainActivity" android:exported="true" />
          <receiver
            android:name="com.google.android.gms.measurement.AppMeasurementReceiver"
            android:exported="true" />
        </application>
      </manifest>
    `;
    const issues = validateExportedComponents(manifest);
    assert.equal(issues.length, 1);
    assert.match(issues[0], /Exported receiver is not TabbyRushTaskWidgetReceiver: com\.google\.android\.gms\.measurement\.AppMeasurementReceiver/);
  });

  it('rejects FirebaseInitProvider when exported', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name="dev.tabbyrush.android.MainActivity" android:exported="true" />
          <provider
            android:name="com.google.firebase.provider.FirebaseInitProvider"
            android:exported="true"
            android:authorities="dev.tabbyrush.android.firebaseinitprovider" />
        </application>
      </manifest>
    `;
    const issues = validateExportedComponents(manifest);
    assert.ok(issues.some(i => i.includes('FirebaseInitProvider must not be exported')));
  });

  it('rejects FirebaseInitProvider with incorrect authority', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name="dev.tabbyrush.android.MainActivity" android:exported="true" />
          <provider
            android:name="com.google.firebase.provider.FirebaseInitProvider"
            android:exported="false"
            android:authorities="com.attacker.firebaseinitprovider" />
        </application>
      </manifest>
    `;
    const issues = validateExportedComponents(manifest);
    assert.ok(issues.some(i => i.includes('FirebaseInitProvider must have exact authority')));
  });

  it('rejects exported activity-alias', () => {
    const manifest = `
      <manifest>
        <application>
          <activity android:name="dev.tabbyrush.android.MainActivity" android:exported="true" />
          <activity-alias
            android:name=".MainActivityAlias"
            android:targetActivity=".MainActivity"
            android:exported="true" />
        </application>
      </manifest>
    `;
    const issues = validateExportedComponents(manifest);
    assert.equal(issues.length, 1);
    assert.match(issues[0], /Exported activity-alias is prohibited/);
  });
});

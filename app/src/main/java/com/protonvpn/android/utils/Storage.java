/*
 * Copyright (c) 2017 Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.protonvpn.android.utils;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Objects;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlinx.serialization.DeserializationStrategy;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.SerializationException;
import kotlinx.serialization.SerializationStrategy;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonKt;

public final class Storage {

    private final static Json JSON = JsonKt.Json(
            Json.Default,
            jsonBuilder -> {
                jsonBuilder.setIgnoreUnknownKeys(true);
                return Unit.INSTANCE;
            }
    );

    private static SharedPreferences preferences;

    private Storage() {
    }

    public static void setPreferences(SharedPreferences preferences) {
        Storage.preferences = preferences;
    }

    public static void saveBoolean(String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    public static boolean getBoolean(String key, Boolean defaultValue) {
        return preferences.getBoolean(key, defaultValue);
    }

    public static void saveInt(String key, int value) {
        preferences.edit().putInt(key, value).apply();
    }

    public static int getInt(String key) {
        try {
            return preferences.getInt(key, 0);
        }
        catch (ClassCastException e) {
            DebugUtils.INSTANCE.fail("Int format exception for key: " + key + ": " + e.getMessage());
            return 0;
        }
    }

    public static void saveString(String key, String value) {
        if (!Objects.equals(getString(key, null), value)) {
            preferences.edit().putString(key, value).apply();
        }
    }

    public static String getString(String key, String defValue) {
        return preferences.getString(key, defValue);
    }

    public static <T, Key> void save(@Nullable T data, Class<Key> key, SerializationStrategy<T> serializer) {
        if (data != null) {
            preferences.edit().putString(key.getName(), JSON.encodeToString(serializer, data)).apply();
        } else {
            preferences.edit().remove(key.getName()).apply();
        }
    }

    @Nullable
    public static <K, V> V load(Class<K> keyClass, DeserializationStrategy<V> deserializer) {
        String key = keyClass.getName();
        if (!preferences.contains(key)) {
            return null;
        }

        V fromJson;
        try {
            String json = preferences.getString(key, null);
            fromJson = json != null ? JSON.decodeFromString(deserializer, json) : null;
        }
        catch (IllegalArgumentException e) {
            DebugUtils.INSTANCE.fail("Json load exception: " + e.getMessage());
            return null;
        }
        return fromJson;
    }

    public static <K> Boolean containsKey(Class<K> keyClass) {
        String key = keyClass.getName();
        return preferences.contains(key);
    }

    public static void delete(String key) {
        preferences.edit().remove(key).apply();
    }

    public static <T> void delete(Class<T> objClass) {
        delete(objClass.getName());
    }

    public static <Key, T> T load(Class<Key> keyClass, KSerializer<T> serializer, Function0<T> defaultValue) {
        T value = load(keyClass, serializer);
        if (value == null) {
            value = defaultValue.invoke();
            save(value, keyClass, serializer);
        }
        return value;
    }

    @SuppressLint("ApplySharedPref")
    @VisibleForTesting
    public static void clearAllPreferencesSync() {
        preferences.edit().clear().commit();
    }
}

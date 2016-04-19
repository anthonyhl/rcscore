/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 * Copyright (C) 2014 Sony Mobile Communications Inc.
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
 *
 * NOTE: This file has been modified by Sony Mobile Communications Inc.
 * Modifications are licensed under the License.
 ******************************************************************************/

package com.orangelabs.rcs.provider.sharing;

import com.gsma.services.rcs.ish.ImageSharingLog;
import com.orangelabs.rcs.utils.DatabaseUtils;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.text.TextUtils;

/**
 * Image sharing provider
 *
 * @author Jean-Marc AUFFRET
 */
public class ImageSharingProvider extends ContentProvider {

    private static final String TABLE = "imageshare";

    private static final String SELECTION_WITH_SHARING_ID_ONLY = ImageSharingData.KEY_SHARING_ID
            .concat("=?");

    private static final String DATABASE_NAME = "imageshare.db";

    private static final UriMatcher sUriMatcher;
    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(ImageSharingLog.CONTENT_URI.getAuthority(),
                ImageSharingLog.CONTENT_URI.getPath().substring(1), UriType.IMAGE_SHARING);
        sUriMatcher.addURI(ImageSharingLog.CONTENT_URI.getAuthority(), ImageSharingLog.CONTENT_URI
                .getPath().substring(1).concat("/*"), UriType.IMAGE_SHARING_WITH_ID);
    }

    private static final class UriType {

        private static final int IMAGE_SHARING = 1;

        private static final int IMAGE_SHARING_WITH_ID = 2;
    }

    private static final class CursorType {

        private static final String TYPE_DIRECTORY = "vnd.android.cursor.dir/imageshare";

        private static final String TYPE_ITEM = "vnd.android.cursor.item/imageshare";
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final int DATABASE_VERSION = 5;

        public DatabaseHelper(Context ctx) {
            super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(TABLE).append("(")
                    .append(ImageSharingData.KEY_SHARING_ID).append(" TEXT NOT NULL PRIMARY KEY,")
                    .append(ImageSharingData.KEY_CONTACT).append(" TEXT NOT NULL,")
                    .append(ImageSharingData.KEY_FILE).append(" TEXT NOT NULL,")
                    .append(ImageSharingData.KEY_FILENAME).append(" TEXT NOT NULL,")
                    .append(ImageSharingData.KEY_MIME_TYPE).append(" TEXT NOT NULL,")
                    .append(ImageSharingData.KEY_STATE).append(" INTEGER NOT NULL,")
                    .append(ImageSharingData.KEY_REASON_CODE).append(" INTEGER NOT NULL,")
                    .append(ImageSharingData.KEY_DIRECTION).append(" INTEGER NOT NULL,")
                    .append(ImageSharingData.KEY_TIMESTAMP).append(" INTEGER NOT NULL,")
                    .append(ImageSharingData.KEY_TRANSFERRED).append(" INTEGER NOT NULL,")
                    .append(ImageSharingData.KEY_FILESIZE).append(" INTEGER NOT NULL)").toString());
            db.execSQL(new StringBuilder("CREATE INDEX ").append(ImageSharingData.KEY_CONTACT)
                    .append("_idx").append(" ON ").append(TABLE).append("(")
                    .append(ImageSharingData.KEY_CONTACT).append(")").toString());
            db.execSQL(new StringBuilder("CREATE INDEX ").append(ImageSharingData.KEY_TIMESTAMP)
                    .append("_idx").append(" ON ").append(TABLE).append("(")
                    .append(ImageSharingData.KEY_TIMESTAMP).append(")").toString());
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int currentVersion) {
            db.execSQL("DROP TABLE IF EXISTS ".concat(TABLE));
            onCreate(db);
        }
    }

    private SQLiteOpenHelper mOpenHelper;

    private String getSelectionWithSharingId(String selection) {
        if (TextUtils.isEmpty(selection)) {
            return SELECTION_WITH_SHARING_ID_ONLY;
        }
        return new StringBuilder("(").append(SELECTION_WITH_SHARING_ID_ONLY).append(") AND (")
                .append(selection).append(")").toString();
    }

    private String[] getSelectionArgsWithSharingId(String[] selectionArgs, String sharingId) {
        String[] sharingSelectionArg = new String[] {
            sharingId
        };
        if (selectionArgs == null) {
            return sharingSelectionArg;
        }
        return DatabaseUtils.appendSelectionArgs(sharingSelectionArg, selectionArgs);
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case UriType.IMAGE_SHARING:
                return CursorType.TYPE_DIRECTORY;

            case UriType.IMAGE_SHARING_WITH_ID:
                return CursorType.TYPE_ITEM;

            default:
                throw new IllegalArgumentException(new StringBuilder("Unsupported URI ")
                        .append(uri).append("!").toString());
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sort) {
        Cursor cursor = null;
        try {
            switch (sUriMatcher.match(uri)) {
                case UriType.IMAGE_SHARING_WITH_ID:
                    String sharingId = uri.getLastPathSegment();
                    selection = getSelectionWithSharingId(selection);
                    selectionArgs = getSelectionArgsWithSharingId(selectionArgs, sharingId);
                    /* Intentional fall through */
                case UriType.IMAGE_SHARING:
                    SQLiteDatabase db = mOpenHelper.getReadableDatabase();
                    cursor = db.query(TABLE, projection, selection, selectionArgs, null,
                            null, sort);
                    cursor.setNotificationUri(getContext().getContentResolver(), uri);
                    return cursor;

                default:
                    throw new IllegalArgumentException(new StringBuilder("Unsupported URI ")
                            .append(uri).append("!").toString());
            }
        } catch (RuntimeException e) {
            if (cursor != null) {
                cursor.close();
            }
            throw e;
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        switch (sUriMatcher.match(uri)) {
            case UriType.IMAGE_SHARING_WITH_ID:
                String sharingId = uri.getLastPathSegment();
                selection = getSelectionWithSharingId(selection);
                selectionArgs = getSelectionArgsWithSharingId(selectionArgs, sharingId);
                /* Intentional fall through */
            case UriType.IMAGE_SHARING:
                SQLiteDatabase db = mOpenHelper.getWritableDatabase();
                int count = db.update(TABLE, values, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                return count;

            default:
                throw new IllegalArgumentException(new StringBuilder("Unsupported URI ")
                        .append(uri).append("!").toString());
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        switch (sUriMatcher.match(uri)) {
            case UriType.IMAGE_SHARING:
                /* Intentional fall through */
            case UriType.IMAGE_SHARING_WITH_ID:
                SQLiteDatabase db = mOpenHelper.getWritableDatabase();
                String sharingId = initialValues.getAsString(ImageSharingData.KEY_SHARING_ID);
                db.insert(TABLE, null, initialValues);
                Uri notificationUri = Uri.withAppendedPath(ImageSharingLog.CONTENT_URI, sharingId);
                getContext().getContentResolver().notifyChange(notificationUri, null);
                return notificationUri;

            default:
                throw new IllegalArgumentException(new StringBuilder("Unsupported URI ")
                        .append(uri).append("!").toString());
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        switch (sUriMatcher.match(uri)) {
            case UriType.IMAGE_SHARING_WITH_ID:
                String sharingId = uri.getLastPathSegment();
                selection = getSelectionWithSharingId(selection);
                selectionArgs = getSelectionArgsWithSharingId(selectionArgs, sharingId);
                /* Intentional fall through */
            case UriType.IMAGE_SHARING:
                SQLiteDatabase db = mOpenHelper.getWritableDatabase();
                int count = db.delete(TABLE, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                return count;

            default:
                throw new IllegalArgumentException(new StringBuilder("Unsupported URI ")
                        .append(uri).append("!").toString());
        }
    }
}

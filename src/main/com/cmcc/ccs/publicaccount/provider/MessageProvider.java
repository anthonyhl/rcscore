/*******************************************************************************
 * Software Name: RCS
 *
 * Copyright (C) 2015 TCL Telecommunication Technology Ltd.
 *
 * Description: TODO
 *
 * Feb 10, 2015
 *
 *Author: lei.zhang9282@icloud.com
 *
 * Modification record:
 *-------------------------------------------------------------------------------------------
 * | Feb 10, 2015 | init it.                                                                     |
 * | Mar 3, 2015 | modify the message table.                                              |
 *-------------------------------------------------------------------------------------------
 ******************************************************************************/

package com.cmcc.ccs.publicaccount.provider;
import com.orangelabs.rcs.provider.messaging.MessageLog;
import com.orangelabs.rcs.utils.logger.Logger;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;

/**
 * @author leizhang
 *
 */
public class MessageProvider extends ContentProvider {
    private final String LOG_TAG = MessageProvider.class.getSimpleName();
    private final boolean DEBUG = false;
    private static final Logger logger = Logger.getLogger(MessageLog.class.getSimpleName());
    /**
     * uri of account info,always used to obtain the list
     */
    public static final Uri CONTENT_URI = Uri.parse("content://com.cmcc.ccs.public_account_info/list");
    /**
     * uri of search,always used to obtain the list when search
     */
    public static final Uri CONTENT_URI_SEARCH = Uri.parse("content://com.cmcc.ccs.public_account_search/list");
    /**
     * uri of public account
     */
    public static final Uri CONTENT_URI_ACCOUNT = Uri.parse("content://com.cmcc.ccs.public_account");
    /**
     * uri used to get the account detail,normal state
     */
    public static final Uri CONTENT_URI_ACCOUNT_DETAIL = Uri.parse("content://com.cmcc.ccs.public_account_info/detail");
    /**
     * uri used to get the account detail,search state
     */
    public static final Uri CONTENT_URI_SEARCH_DETAIL = Uri.parse("content://com.cmcc.ccs.public_account_search/detail");
    public static final String TABLE_ACCOUNT = "account";
    public static final String TABLE_TMP_ACCOUNT = "tmp_account";
    public static final String VIEW_ACCOUNT_SIMPLE = "simple_account";
    private final String DB_NAME = "publicaccount.db";

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    private static void match(Uri uri, int code) {
        String authority = uri.getAuthority();
        String path = uri.getPath().substring(1);
        sUriMatcher.addURI(authority, path, code);
    }

    private static void match(Uri uri, int code, int code2) {
        String authority = uri.getAuthority();
        String path = uri.getPath().substring(1);
        sUriMatcher.addURI(authority, path, code);

        sUriMatcher.addURI(authority, path.concat("/*"), code2);
    }
    static {
        match(CONTENT_URI, UriType.Message.MESSAGE);
        match(CONTENT_URI_SEARCH, UriType.Message.SEARCH);
        match(CONTENT_URI_ACCOUNT_DETAIL, UriType.Message.ACCOUNT_DETAIL);
        match(CONTENT_URI_SEARCH_DETAIL, UriType.Message.ACCOUNT_SEARCH_DETAIL);
    }

    private DBHelper mDBHelper = null;
    private SQLiteDatabase db = null;
    /* (non-Javadoc)
     * @see android.content.ContentProvider#onCreate()
     */
    @Override
    public boolean onCreate() {
        mDBHelper = new DBHelper(getContext());
        return true;
    }

    /* (non-Javadoc)
     * @see android.content.ContentProvider#query(android.net.Uri, java.lang.String[], java.lang.String, java.lang.String[], java.lang.String)
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sort) {
        Cursor cursor = null;
        SQLiteDatabase db;
        SQLiteQueryBuilder qb;
        try {
            switch (sUriMatcher.match(uri)) {
                case UriType.Message.MESSAGE:
                {
                    db = mDBHelper.getReadableDatabase();
                    cursor = db.query(TABLE_ACCOUNT, projection, selection, selectionArgs,
                            null, null, sort);
                    cursor.setNotificationUri(getContext().getContentResolver(), uri);
                    return cursor;
                }
                case UriType.Message.SEARCH:
                {
                    db = mDBHelper.getReadableDatabase();
                    cursor = db.query(TABLE_TMP_ACCOUNT, projection, selection, selectionArgs,
                            null, null, sort);
                    cursor.setNotificationUri(getContext().getContentResolver(), uri);
                    return cursor;
                }
                case UriType.Message.ACCOUNT_DETAIL:
                {
                    db = mDBHelper.getReadableDatabase();
                    cursor = db.query(TABLE_ACCOUNT, projection, selection, selectionArgs,
                            null, null, sort);
                    cursor.setNotificationUri(getContext().getContentResolver(), uri);
                    return cursor;
                }
                case UriType.Message.ACCOUNT_SEARCH_DETAIL:
                {
                    db = mDBHelper.getReadableDatabase();
                    cursor = db.query(TABLE_TMP_ACCOUNT, projection, selection, selectionArgs,
                            null, null, sort);
                    cursor.setNotificationUri(getContext().getContentResolver(), uri);
                    return cursor;
                }
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

    /* (non-Javadoc)
     * @see android.content.ContentProvider#getType(android.net.Uri)
     */
    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case UriType.Message.MESSAGE:
                return CursorType.Message.TYPE_DIRECTORY;
            default:
                throw new IllegalArgumentException(new StringBuilder("Unsupported URI ")
                        .append(uri).append("!").toString());
        }
    }

    /* (non-Javadoc)
     * @see android.content.ContentProvider#insert(android.net.Uri, android.content.ContentValues)
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        switch (sUriMatcher.match(uri)) {
            case UriType.Message.MESSAGE:
                db = mDBHelper.getWritableDatabase();
                db.insert(TABLE_ACCOUNT, null, initialValues);
                break;
            case UriType.Message.SEARCH:
                db = mDBHelper.getWritableDatabase();
                db.insert(TABLE_TMP_ACCOUNT, null, initialValues);
                break;
            default:
                throw new IllegalArgumentException(new StringBuilder("Unsupported URI ")
                        .append(uri).append("!").toString());
        }

        return null;
    }

    /* (non-Javadoc)
     * @see android.content.ContentProvider#delete(android.net.Uri, java.lang.String, java.lang.String[])
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        switch (sUriMatcher.match(uri)) {
            case UriType.Message.MESSAGE:
                db = mDBHelper.getWritableDatabase();
                return db.delete(TABLE_ACCOUNT, selection, selectionArgs);
            case UriType.Message.SEARCH:
                db = mDBHelper.getWritableDatabase();
                return db.delete(TABLE_TMP_ACCOUNT, selection, selectionArgs);
            default:
                throw new IllegalArgumentException(new StringBuilder("Unsupported URI ")
                        .append(uri).append("!").toString());
        }
    }

    /* (non-Javadoc)
     * @see android.content.ContentProvider#update(android.net.Uri, android.content.ContentValues, java.lang.String, java.lang.String[])
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        switch (sUriMatcher.match(uri)) {
            case UriType.Message.MESSAGE:
                db = mDBHelper.getWritableDatabase();
                return db.update(TABLE_ACCOUNT, values, selection, selectionArgs);
            case UriType.Message.SEARCH:
                db = mDBHelper.getWritableDatabase();
                return db.update(TABLE_TMP_ACCOUNT, values, selection, selectionArgs);
            default:
                throw new IllegalArgumentException(new StringBuilder("Unsupported URI ")
                        .append(uri).append("!").toString());
        }
    }

    private void LogI(String msg) {
        if (DEBUG) {
            if (logger.isActivated())
                logger.debug(msg);
        }
    }

    /**
     * this static class defined the columns of message of public account
     * @author leizhang
     *
     */
    public static final class Columns implements BaseColumns{
        /**
         * the filed account
         */
        public static final String ACCOUNT = "account";
        /**
         * the filed name
         */
        public static final String NAME = "name";
        /**
         * the icon filed
         */
        public static final String PORTRAIT = "portrait";
        /**
         * the signature
         */
        public static final String BRIEF_INTRODUCTION = "brief_introduction";
        /**
         * the state
         */
        public static final String STATE  = "state";
        /**
         * the menu config
         */
        public static final String CONFIG = "config";
        /**
         * indicates is follow
         */
        public static final String ISFOLLOW = "isfollow";

        /**
         * the id type. 0 indicates company, and personal is 1
         */
        public static final String IDTYPE = "idtype";

        /**
         * recommend level which between 1 and 5, default is 1
         */
        public static final String RECOMMEND_LEVEL = "recommend_level";

        ////////////////For detail////////////////////////////
        public static final String TYPE = "type";//string
        public static final String UPDATETIME = "updatetime";//string
        public static final String MENUTYPE  = "menutype";//menu type
        public static final String MENUTIMESTAMP = "menutimestamp";//string
        public static final String ACCEPTSTATUS = "acceptstatus";//int
        public static final String TEL = "tel";//string
        public static final String EMAIL = "email";//string
        public static final String ZIP = "zip";//string
        public static final String ADDR = "addr";//string
        public static final String FILED = "field";//string
        public static final String QRCODE = "qrcode";//string
    }

    private final class DBHelper extends SQLiteOpenHelper{
        private static final int DATABASE_VERSION = 5;

        public DBHelper(Context ctx) {
            super(ctx, DB_NAME, null, DATABASE_VERSION);
        }

        /* (non-Javadoc)
         * @see android.database.sqlite.SQLiteOpenHelper#onCreate(android.database.sqlite.SQLiteDatabase)
         */
        @Override
        public void onCreate(SQLiteDatabase db) {
            createMessageTable(db);
        }

        /* (non-Javadoc)
         * @see android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database.sqlite.SQLiteDatabase, int, int)
         */
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onCreate(db);
        }

        private void createMessageTable(SQLiteDatabase database){
            //create account table
            database.execSQL(new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(TABLE_ACCOUNT)
                    .append("(")
                    .append(Columns._ID).append(" TEXT NOT NULL PRIMARY KEY,")
                    .append(Columns.ACCOUNT).append(" TEXT NOT NULL,")
                    .append(Columns.NAME).append(" TEXT NOT NULL,")
                    .append(Columns.PORTRAIT).append(" TEXT NOT NULL,")
                    .append(Columns.BRIEF_INTRODUCTION).append(" TEXT NOT NULL,")
                    .append(Columns.STATE).append(" TEXT NOT NULL,")
                    .append(Columns.CONFIG).append(" TEXT NOT NULL,")
                    .append(Columns.IDTYPE).append(" INTEGER DEFAULT (0),")//the id type
                    .append(Columns.RECOMMEND_LEVEL).append(" INTEGER DEFAULT (1),")//the recommend level
                    .append(Columns.UPDATETIME).append(" TEXT,")
                    .append(Columns.MENUTYPE).append(" INTEGER DEFAULT (0),")
                    .append(Columns.MENUTIMESTAMP).append(" TEXT,")
                    .append(Columns.TEL).append(" TEXT,")
                    .append(Columns.EMAIL).append(" TEXT,")
                    .append(Columns.ZIP).append(" TEXT,")
                    .append(Columns.ADDR).append(" TEXT,")
                    .append(Columns.FILED).append(" TEXT,")
                    .append(Columns.QRCODE).append(" TEXT,")
                    .append(Columns.ISFOLLOW).append(" INTEGER DEFAULT (0)")
                    .append(")").toString()
                    );

            //create tmp account table for search
            database.execSQL(new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(TABLE_TMP_ACCOUNT)
                    .append("(")
                    .append(Columns._ID).append(" TEXT NOT NULL PRIMARY KEY,")
                    .append(Columns.ACCOUNT).append(" TEXT NOT NULL,")
                    .append(Columns.NAME).append(" TEXT NOT NULL,")
                    .append(Columns.PORTRAIT).append(" TEXT NOT NULL,")
                    .append(Columns.BRIEF_INTRODUCTION).append(" TEXT NOT NULL,")
                    .append(Columns.STATE).append(" TEXT NOT NULL,")
                    .append(Columns.CONFIG).append(" TEXT NOT NULL,")
                    .append(Columns.IDTYPE).append(" INTEGER DEFAULT (0),")//the id type
                    .append(Columns.RECOMMEND_LEVEL).append(" INTEGER DEFAULT (1),")//the recommend level
                    .append(Columns.UPDATETIME).append(" TEXT,")
                    .append(Columns.MENUTYPE).append(" INTEGER DEFAULT (0),")
                    .append(Columns.MENUTIMESTAMP).append(" TEXT,")
                    .append(Columns.TEL).append(" TEXT,")
                    .append(Columns.EMAIL).append(" TEXT,")
                    .append(Columns.ZIP).append(" TEXT,")
                    .append(Columns.ADDR).append(" TEXT,")
                    .append(Columns.FILED).append(" TEXT,")
                    .append(Columns.QRCODE).append(" TEXT,")
                    .append(Columns.ISFOLLOW).append(" INTEGER DEFAULT (0)")
                    .append(")").toString()
                    );

            //create simple account view
            database.execSQL(new StringBuilder("CREATE VIEW ").append(VIEW_ACCOUNT_SIMPLE).append(" AS SELECT ")
                    .append(Columns.ACCOUNT).append(" ,")
                    .append(Columns.NAME).append(" ,")
                    .append(Columns.IDTYPE).append(" ,")
                    .append(Columns.BRIEF_INTRODUCTION).append(" ,")
                    .append(Columns.RECOMMEND_LEVEL).append(" ,")
                    .append(Columns.PORTRAIT).append(" ,")
                    .append(Columns.ISFOLLOW)
                    .append(" FROM ").append(TABLE_ACCOUNT)
                    .toString());
        }
    }

    private static final class UriType{
        private static final class Message{
            public static final int MESSAGE = 1;
            public static final int SEARCH = 2;
            public static final int ACCOUNT_DETAIL = 3;
            public static final int ACCOUNT_SEARCH_DETAIL = 4;
        }

    }

    private static final class CursorType {

        private static final class Message {

            private static final String TYPE_DIRECTORY = "vnd.android.cursor.dir/publicaccount";

            private static final String TYPE_ITEM = "vnd.android.cursor.item/publicaccount";
        }
    }
}

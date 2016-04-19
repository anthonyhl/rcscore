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

package com.orangelabs.rcs.provider.messaging;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

import com.gsma.services.rcs.chat.ChatLog;
import com.orangelabs.rcs.utils.DatabaseUtils;

/**
 * Chat provider
 *
 * @author Jean-Marc AUFFRET
 */
public class ChatProvider extends ContentProvider {
	

    private static final String TABLE = "filetransfer";

    private static final String SELECTION_WITH_FT_ID_ONLY = FileTransferLog.KEY_FT_ID.concat("=?");

    private static final String TABLE_GROUP_CHAT = "groupchat";

    private static final String TABLE_MESSAGE = "message";

    private static final String SELECTION_WITH_CHAT_ID_ONLY = GroupChatLog.KEY_CHAT_ID.concat("=?");

    private static final String SELECTION_WITH_MSG_ID_ONLY = MessageLog.KEY_MESSAGE_ID.concat("=?");

    private static final String DATABASE_NAME = "chat.db";

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
    	match(ChatLog.GroupChat.CONTENT_URI, UriType.Chat.CHAT, UriType.Chat.CHAT_WITH_ID);  	
    	match(ChatLog.Message.CONTENT_URI, UriType.Message.MESSAGE, UriType.Message.MESSAGE_WITH_ID);  
       
    	match(ChatLog.Message.THREAD_URI, UriType.Thread.THREAD);
    	match(ChatLog.Message.THREAD_COUNT_URI, UriType.Thread.THREAD_COUNT);
    	match(ChatLog.Message.MESSAGE_URI, UriType.Thread.ALL);  
    	match(FileTransferLog.CONTENT_URI, UriType.FileTransfer.FILE_TRANSFER, 
    			UriType.FileTransfer.FILE_TRANSFER_WITH_ID);
        match(ChatLog.Message.MESSAGE_URI_SEARCH, UriType.Thread.SEARCH);
    }

    /**
     * String to restrict projection for exposed URI to a set of columns
     */
    private static final String[] RESTRICTED_PROJECTION_FOR_EXTERNALLY_DEFINED_COLUMNS = new String[] {
            ChatLog.GroupChat.CHAT_ID, ChatLog.GroupChat.CONTACT, ChatLog.GroupChat.STATE,
            ChatLog.GroupChat.SUBJECT, ChatLog.GroupChat.DIRECTION, ChatLog.GroupChat.TIMESTAMP,
            ChatLog.GroupChat.REASON_CODE, ChatLog.GroupChat.PARTICIPANTS
    };

    private static final Set<String> RESTRICTED_PROJECTION_SET = new HashSet<String>(
            Arrays.asList(RESTRICTED_PROJECTION_FOR_EXTERNALLY_DEFINED_COLUMNS));
    
    /**
     * String to restrict projection for exposed URI to a set of columns
     */
    private static final String[] FT_RESTRICTED_PROJECTION_FOR_EXTERNALLY_DEFINED_COLUMNS = new String[] {
            com.gsma.services.rcs.ft.FileTransferLog.FT_ID, com.gsma.services.rcs.ft.FileTransferLog.CHAT_ID, com.gsma.services.rcs.ft.FileTransferLog.CONTACT,
            com.gsma.services.rcs.ft.FileTransferLog.FILE, com.gsma.services.rcs.ft.FileTransferLog.FILENAME, com.gsma.services.rcs.ft.FileTransferLog.MIME_TYPE,
            com.gsma.services.rcs.ft.FileTransferLog.FILEICON, com.gsma.services.rcs.ft.FileTransferLog.FILEICON_MIME_TYPE,
            com.gsma.services.rcs.ft.FileTransferLog.DIRECTION, com.gsma.services.rcs.ft.FileTransferLog.FILESIZE, com.gsma.services.rcs.ft.FileTransferLog.TRANSFERRED,
            com.gsma.services.rcs.ft.FileTransferLog.TIMESTAMP, com.gsma.services.rcs.ft.FileTransferLog.TIMESTAMP_SENT,
            com.gsma.services.rcs.ft.FileTransferLog.TIMESTAMP_DELIVERED, com.gsma.services.rcs.ft.FileTransferLog.TIMESTAMP_DISPLAYED,
            com.gsma.services.rcs.ft.FileTransferLog.STATE, com.gsma.services.rcs.ft.FileTransferLog.REASON_CODE, com.gsma.services.rcs.ft.FileTransferLog.READ_STATUS
    };

    private static final Set<String> FT_RESTRICTED_PROJECTION_SET = new HashSet<String>(
            Arrays.asList(FT_RESTRICTED_PROJECTION_FOR_EXTERNALLY_DEFINED_COLUMNS));

    private static final class UriType {

        private static final class Chat {

            private static final int CHAT = 1;

            private static final int CHAT_WITH_ID = 2;
        }

        private static final class Message {

            private static final int MESSAGE = 3;

            private static final int MESSAGE_WITH_ID = 4;
                     
        }
        
        private static final class Thread {

            public static final int ALL = 9;

			private static final int THREAD = 7;

            private static final int THREAD_COUNT = 8;

            public static final int SEARCH = 10;
        }
        
        private static final class FileTransfer {

            private static final int FILE_TRANSFER = 11;

            private static final int FILE_TRANSFER_WITH_ID = 12;
        }
    }

    private static final class CursorType {

        private static final class Chat {

            private static final String TYPE_DIRECTORY = "vnd.android.cursor.dir/groupchat";

            private static final String TYPE_ITEM = "vnd.android.cursor.item/groupchat";
        }

        private static final class Message {

            private static final String TYPE_DIRECTORY = "vnd.android.cursor.dir/chatmessage";

            private static final String TYPE_ITEM = "vnd.android.cursor.item/chatmessage";
        }
        
        private static final String TYPE_DIRECTORY = "vnd.android.cursor.dir/filetransfer";

        private static final String TYPE_ITEM = "vnd.android.cursor.item/filetransfer";
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final int DATABASE_VERSION = 17;
        private boolean isTriggerOn = false;
        public DatabaseHelper(Context ctx) {
            super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(TABLE_GROUP_CHAT)
                    .append("(")
                    .append(GroupChatLog.KEY_CHAT_ID).append(" TEXT NOT NULL PRIMARY KEY,")
                    .append(GroupChatLog.KEY_REJOIN_ID).append(" TEXT,")
                    .append(GroupChatLog.KEY_SUBJECT).append(" TEXT,")
                    .append(GroupChatLog.KEY_PARTICIPANTS).append(" TEXT NOT NULL,")
                    .append(GroupChatLog.KEY_STATE).append(" INTEGER NOT NULL,")
                    .append(GroupChatLog.KEY_REASON_CODE).append(" INTEGER NOT NULL,")
                    .append(GroupChatLog.KEY_DIRECTION).append(" INTEGER NOT NULL,")
                    .append(GroupChatLog.KEY_TIMESTAMP).append(" INTEGER NOT NULL,")
                    .append(GroupChatLog.KEY_USER_ABORTION).append(" INTEGER NOT NULL,")
                    .append(GroupChatLog.KEY_CONTACT).append(" TEXT)").toString());
            db.execSQL(new StringBuilder("CREATE INDEX ").append(TABLE_GROUP_CHAT).append("_")
                    .append(GroupChatLog.KEY_TIMESTAMP).append("_idx").append(" ON ")
                    .append(TABLE_GROUP_CHAT).append("(").append(GroupChatLog.KEY_TIMESTAMP)
                    .append(")").toString());
            db.execSQL(new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(TABLE_MESSAGE)
                    .append("(").append(MessageLog.KEY_CHAT_ID).append(" TEXT NOT NULL,")
                    .append(MessageLog.KEY_CONTACT).append(" TEXT,")
                    .append(MessageLog.KEY_MESSAGE_ID).append(" TEXT NOT NULL PRIMARY KEY,")
                    .append(MessageLog.KEY_CONV_ID).append(" TEXT,")
                    .append(MessageLog.KEY_CONTENT).append(" TEXT,")
                    .append(MessageLog.KEY_MIME_TYPE).append(" TEXT NOT NULL,")
                    .append(MessageLog.KEY_DIRECTION).append(" INTEGER NOT NULL,")
                    .append(MessageLog.KEY_STATUS).append(" INTEGER NOT NULL,")
                    .append(MessageLog.KEY_REASON_CODE).append(" INTEGER NOT NULL,")
                    .append(MessageLog.KEY_READ_STATUS).append(" INTEGER NOT NULL,")
                    .append(MessageLog.KEY_TIMESTAMP).append(" INTEGER NOT NULL,")
                    .append(MessageLog.KEY_TIMESTAMP_SENT).append(" INTEGER NOT NULL,")
                    .append(MessageLog.KEY_TIMESTAMP_DELIVERED).append(" INTEGER NOT NULL,")
                    .append(MessageLog.KEY_TIMESTAMP_DISPLAYED).append(" INTEGER NOT NULL)")
                    .toString());
            db.execSQL(new StringBuilder("CREATE INDEX ").append(TABLE_MESSAGE).append("_")
                    .append(MessageLog.KEY_CHAT_ID).append("_idx").append(" ON ")
                    .append(TABLE_MESSAGE).append("(").append(MessageLog.KEY_CHAT_ID).append(")")
                    .toString());
            db.execSQL(new StringBuilder("CREATE INDEX ").append(MessageLog.KEY_TIMESTAMP)
                    .append("_idx").append(" ON ").append(TABLE_MESSAGE).append("(")
                    .append(MessageLog.KEY_TIMESTAMP).append(")").toString());
            db.execSQL(new StringBuilder("CREATE INDEX ").append(MessageLog.KEY_TIMESTAMP_SENT)
                    .append("_idx").append(" ON ").append(TABLE_MESSAGE).append("(")
                    .append(MessageLog.KEY_TIMESTAMP_SENT).append(")").toString());
            
            db.execSQL(new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(TABLE).append("(")
                    .append(FileTransferLog.KEY_FT_ID).append(" TEXT NOT NULL PRIMARY KEY,")
                    .append(MessageLog.KEY_CONV_ID).append(" TEXT,")
                    .append(FileTransferLog.KEY_CONTACT).append(" TEXT,")
                    .append(FileTransferLog.KEY_FILE).append(" TEXT NOT NULL,")
                    .append(FileTransferLog.KEY_FILENAME).append(" TEXT NOT NULL,")
                    .append(FileTransferLog.KEY_CHAT_ID).append(" TEXT NOT NULL,")
                    .append(FileTransferLog.KEY_MIME_TYPE).append(" TEXT NOT NULL,")
                    .append(FileTransferLog.KEY_STATE).append(" INTEGER NOT NULL,")
                    .append(FileTransferLog.KEY_REASON_CODE).append(" INTEGER NOT NULL,")
                    .append(FileTransferLog.KEY_READ_STATUS).append(" INTEGER NOT NULL,")
                    .append(FileTransferLog.KEY_DIRECTION).append(" INTEGER NOT NULL,")
                    .append(FileTransferLog.KEY_TIMESTAMP).append(" INTEGER NOT NULL,")
                    .append(FileTransferLog.KEY_TIMESTAMP_SENT).append(" INTEGER NOT NULL,")
                    .append(FileTransferLog.KEY_TIMESTAMP_DELIVERED).append(" INTEGER NOT NULL,")
                    .append(FileTransferLog.KEY_TIMESTAMP_DISPLAYED).append(" INTEGER NOT NULL,")
                    .append(FileTransferLog.KEY_TRANSFERRED).append(" INTEGER NOT NULL,")
                    .append(FileTransferLog.KEY_FILESIZE).append(" INTEGER NOT NULL,")
                    .append(FileTransferLog.KEY_FILEICON).append(" TEXT,")
                    .append(FileTransferLog.KEY_UPLOAD_TID).append(" TEXT,")
                    .append(FileTransferLog.KEY_DOWNLOAD_URI).append(" TEXT,")
                    .append(FileTransferLog.KEY_FILEICON_MIME_TYPE).append(" TEXT)").toString());
            db.execSQL(new StringBuilder("CREATE INDEX ").append(FileTransferLog.KEY_CHAT_ID)
                    .append("_ft_idx").append(" ON ").append(TABLE).append("(")
                    .append(FileTransferLog.KEY_CHAT_ID).append(")").toString());
            db.execSQL(new StringBuilder("CREATE INDEX ").append(FileTransferLog.KEY_TIMESTAMP)
                    .append("_ft_idx").append(" ON ").append(TABLE).append("(")
                    .append(FileTransferLog.KEY_TIMESTAMP).append(")").toString());
            db.execSQL(new StringBuilder("CREATE INDEX ")
                    .append(FileTransferLog.KEY_TIMESTAMP_SENT).append("_ft_idx").append(" ON ")
                    .append(TABLE).append("(").append(FileTransferLog.KEY_TIMESTAMP_SENT)
                    .append(")").toString());
           
            SQLiteQueryBuilder qb = new SQLiteQueryBuilder();                       
            
            String[] msg_columns = new String[] {
            		"1 as " + ChatLog.Message.MESSAGE_TYPE,
            		MessageLog.KEY_CHAT_ID,
            		MessageLog.KEY_CONTACT,
            		MessageLog.KEY_MESSAGE_ID,
                    MessageLog.KEY_CONV_ID,
            		MessageLog.KEY_CONTENT,
            		MessageLog.KEY_MIME_TYPE,
            		MessageLog.KEY_DIRECTION,
            		MessageLog.KEY_STATUS,
            		MessageLog.KEY_REASON_CODE,
            		MessageLog.KEY_READ_STATUS,
            		MessageLog.KEY_TIMESTAMP,
            		MessageLog.KEY_TIMESTAMP_SENT,
            		MessageLog.KEY_TIMESTAMP_DELIVERED,
            		MessageLog.KEY_TIMESTAMP_DISPLAYED
            };
            
            String[] ft_columns = new String[] {
            		"2 as " + ChatLog.Message.MESSAGE_TYPE,

            		FileTransferLog.KEY_CHAT_ID,
            		FileTransferLog.KEY_CONTACT,
            		FileTransferLog.KEY_FT_ID + " AS " + MessageLog.KEY_MESSAGE_ID,
                    MessageLog.KEY_CONV_ID,
            		FileTransferLog.KEY_FILE + " AS " + MessageLog.KEY_CONTENT,
            		FileTransferLog.KEY_MIME_TYPE,
            		FileTransferLog.KEY_DIRECTION,
            		FileTransferLog.KEY_STATE + " AS " + MessageLog.KEY_STATUS,
            		FileTransferLog.KEY_REASON_CODE,
            		FileTransferLog.KEY_READ_STATUS,
            		FileTransferLog.KEY_TIMESTAMP,
            		FileTransferLog.KEY_TIMESTAMP_SENT,
            		FileTransferLog.KEY_TIMESTAMP_DELIVERED,
            		FileTransferLog.KEY_TIMESTAMP_DISPLAYED
            };          
            
            String[] subsql = new String[2];
            
            qb.setTables(TABLE_MESSAGE);
            subsql[0] = qb.buildQuery(msg_columns, null, null, null, null, null);
            qb.setTables(TABLE);
            subsql[1] = qb.buildQuery(ft_columns, null, null, null, null, null);
      
            db.execSQL(new StringBuilder("CREATE VIEW "). append("message_view").append(" AS ")
            		.append(qb.buildUnionQuery(subsql, MessageLog.KEY_TIMESTAMP, null)).toString());
            
            
            String[] thread_columns = new String[] {
            		"message_view." + ChatLog.Message.MESSAGE_TYPE,
            		
            		"message_view." + MessageLog.KEY_CONTACT,
            		"message_view." + MessageLog.KEY_MESSAGE_ID,
            		"message_view." + MessageLog.KEY_CONV_ID,
            		"message_view." + MessageLog.KEY_CONTENT,
            		"message_view." + MessageLog.KEY_MIME_TYPE,
            		"message_view." + MessageLog.KEY_DIRECTION,
            		"message_view." + MessageLog.KEY_STATUS,
            		"message_view." + MessageLog.KEY_REASON_CODE,
            		"message_view." + MessageLog.KEY_READ_STATUS,
            		"message_view." + MessageLog.KEY_TIMESTAMP_SENT,
            		"message_view." + MessageLog.KEY_TIMESTAMP_DELIVERED,
            		"message_view." + MessageLog.KEY_TIMESTAMP_DISPLAYED,
            		
            		"message_view." + GroupChatLog.KEY_CHAT_ID,
                    "groupchat." + GroupChatLog.KEY_REJOIN_ID,
                    "groupchat." + GroupChatLog.KEY_SUBJECT,
                    "groupchat." + GroupChatLog.KEY_PARTICIPANTS,
                    "groupchat." + GroupChatLog.KEY_USER_ABORTION,

                    
                    "max(message_view." + MessageLog.KEY_TIMESTAMP + ") AS timestamp",
            		"count(message_view." + MessageLog.KEY_CHAT_ID + ") AS message_count",
            };

		    qb.setTables("message_view LEFT OUTER JOIN groupchat on message_view.chat_id = groupchat.chat_id");
		    		
            String sort = "message_view." + MessageLog.KEY_TIMESTAMP  + " ASC";
            String groupby = "message_view." + MessageLog.KEY_CHAT_ID;
            
            db.execSQL(new StringBuilder("CREATE VIEW "). append("thread_view").append(" AS ")
            		.append(qb.buildQuery(thread_columns, null, groupby, null, sort, null)).toString());

            if (isTriggerOn) {
                //[Rcs]Added by zhanglei for create table threads begin
                //create table threads
                StringBuilder sb = new StringBuilder();
                sb.append("--TABLE THREAD")
                .append("\r\n")
                .append("CREATE TABLE IF NOT EXISTS THREADS")
                .append("\r\n")
                .append("AS SELECT")
                .append("\r\n")
                .append("message_view.message_type AS message_type,")
                .append("\r\n")
                .append("message_view.contact AS contact,")
                .append("\r\n")
                .append("message_view.msg_id AS msg_id,")
                .append("\r\n")
                .append("message_view.content AS content,")
                .append("\r\n")
                .append("message_view.mime_type AS mime_type,")
                .append("\r\n")
                .append("message_view.direction AS direction,")
                .append("\r\n")
                .append("message_view.status AS status,")
                .append("\r\n")
                .append("message_view.reason_code AS reason_code,")
                .append("\r\n")
                .append("message_view.read_status AS read_status,")
                .append("\r\n")
                .append("message_view.timestamp_sent AS timestamp_sent,")
                .append("\r\n")
                .append("message_view.timestamp_delivered AS timestamp_delivered,")
                .append("\r\n")
                .append("message_view.timestamp_displayed AS timestamp_displayed,")
                .append("\r\n")
                .append("message_view.chat_id AS chat_id,")
                .append("\r\n")
                .append("groupchat.rejoin_id AS rejoin_id,")
                .append("\r\n")
                .append("groupchat.subject AS subject, ")
                .append("\r\n")
                .append("groupchat.participants AS participants,")
                .append("\r\n")
                .append("groupchat.user_abortion AS user_abortion,")
                .append("\r\n")
                .append("max(message_view.timestamp) AS timestamp,")
                .append("\r\n")
                .append("0 AS message_count,")
                .append("\r\n")
                .append("0 AS unread_count,")
                .append("\r\n")
                .append("'' AS wallpaper")
                .append("\r\n")
                .append("FROM message_view")
                .append("\r\n")
                .append("LEFT OUTER JOIN groupchat on message_view.chat_id = groupchat.chat_id")
                .append("\r\n")
                .append("GROUP BY message_view.chat_id").append("\r\n")
                .append("ORDER BY message_view.timestamp ASC");
                db.execSQL(sb.toString());

                // create add trigger for table groupchat
                sb = new StringBuilder();
                sb.append("CREATE TRIGGER IF NOT EXISTS[TGR_GC_ADD]")
                        .append("\r\n")
                        .append("AFTER INSERT")
                        .append("\r\n")
                        .append("ON groupchat")
                        .append("\r\n")
                        .append("FOR EACH ROW")
                        .append("\r\n")
                        .append("BEGIN")
                        .append("\r\n")
                        .append("INSERT INTO THREADS(")
                        .append("\r\n")
                        .append("message_type,")
                        .append("\r\n")
                        .append("contact,")
                        .append("\r\n")
                        .append("direction,")
                        .append("\r\n")
                        .append("reason_code,")
                        .append("\r\n")
                        .append("chat_id,")
                        .append("\r\n")
                        .append("rejoin_id,")
                        .append("\r\n")
                        .append("subject,")
                        .append("\r\n")
                        .append("participants,")
                        .append("\r\n")
                        .append("user_abortion,")
                        .append("\r\n")
                        .append("timestamp,")
                        .append("\r\n")
                        .append("message_count,")
                        .append("\r\n")
                        .append("unread_count")
                        .append("\r\n")
                        .append(")")
                        .append("\r\n")
                        .append("VALUES(")
                        .append("\r\n")
                        .append("1,")
                        .append("\r\n")
                        .append("NEW.CHAT_ID,")
                        .append("\r\n")
                        .append("NEW.direction,")
                        .append("\r\n")
                        .append("NEW.reason_code, ")
                        .append("\r\n")
                        .append("NEW.chat_id,")
                        .append("\r\n")
                        .append("NEW.rejoin_id,")
                        .append("\r\n")
                        .append("NEW.subject,")
                        .append("\r\n")
                        .append("NEW.participants,")
                        .append("\r\n")
                        .append("NEW.user_abortion,")
                        .append("\r\n")
                        .append("NEW.timestamp,")
                        .append("\r\n")
                        .append("(select count(message_view.chat_id) from message_view where message_view.chat_id = new.chat_id),")
                        .append("\r\n")
                        .append("(select count(*) from message_view where message_view.chat_id = new.chat_id and message_view.status = 0 and message_view.read_status = 0)")
                        .append("\r\n")
                        .append(");").append("\r\n")
                        .append("END");
                db.execSQL(sb.toString());

                // create update trigger for group chat
                sb = new StringBuilder();
                sb.append("CREATE TRIGGER IF NOT EXISTS [TGR_GC_UPDATE]")
                        .append("\r\n")
                        .append("AFTER UPDATE")
                        .append("\r\n")
                        .append("ON groupchat")
                        .append("\r\n")
                        .append("FOR EACH ROW")
                        .append("\r\n")
                        .append("BEGIN")
                        .append("\r\n")
                        .append("UPDATE THREADS SET")
                        .append("\r\n")
                        .append("reason_code = NEW.reason_code,")
                        .append("\r\n")
                        .append("rejoin_id = NEW.rejoin_id,")
                        .append("\r\n")
                        .append("subject = NEW.subject,")
                        .append("\r\n")
                        .append("participants = NEW.participants,")
                        .append("\r\n")
                        .append("user_abortion = NEW.user_abortion,")
                        .append("\r\n")
                        .append("timestamp = NEW.timestamp,")
                        .append("\r\n")
                        .append("message_count = (select count(message_view.chat_id) from message_view where message_view.chat_id = new.chat_id),")
                        .append("\r\n")
                        .append("unread_count = (select count(*) from message_view where message_view.chat_id = new.chat_id and message_view.status = 0 and message_view.read_status = 0)")
                        .append("\r\n")
                        .append("WHERE CHAT_ID = NEW.CHAT_ID;").append("\r\n")
                        .append("END");
                db.execSQL(sb.toString());

                // create insert trigger for ft
                sb = new StringBuilder();
                sb.append("CREATE TRIGGER IF NOT EXISTS[TGR_FT_ADD]")
                        .append("\r\n")
                        .append("AFTER INSERT")
                        .append("\r\n")
                        .append("ON filetransfer")
                        .append("\r\n")
                        .append("FOR EACH ROW")
                        .append("\r\n")
                        .append("BEGIN")
                        .append("\r\n")
                        .append("UPDATE THREADS SET")
                        .append("\r\n")
                        .append("message_type = 1,")
                        .append("\r\n")
                        .append("contact = NEW.contact,")
                        .append("\r\n")
                        .append("msg_id = NEW.FT_ID,")
                        .append("\r\n")
                        .append("content = NEW.file,")
                        .append("\r\n")
                        .append("mime_type = NEW.mime_type,")
                        .append("\r\n")
                        .append("direction = NEW.direction,")
                        .append("\r\n")
                        .append("status = NEW.status,")
                        .append("\r\n")
                        .append("reason_code = NEW.status,")
                        .append("\r\n")
                        .append("read_status = NEW.read_status,")
                        .append("\r\n")
                        .append("timestamp_sent = NEW.timestamp_sent,")
                        .append("\r\n")
                        .append("timestamp_delivered = NEW.timestamp_delivered,")
                        .append("\r\n")
                        .append("timestamp_displayed = NEW.timestamp_displayed,")
                        .append("\r\n")
                        .append("chat_id = NEW.chat_id,")
                        .append("\r\n")
                        .append("timestamp = NEW.timestamp,")
                        .append("\r\n")
                        .append("message_count = (select count(message_view.chat_id) from message_view where message_view.chat_id = new.chat_id),")
                        .append("\r\n")
                        .append("unread_count = (select count(*) from message_view where message_view.chat_id = new.chat_id and message_view.status = 0 and message_view.read_status = 0)")
                        .append("\r\n")
                        .append("WHERE CHAT_ID = NEW.CHAT_ID;").append("\r\n")
                        .append("END");
                db.execSQL(sb.toString());

                // create update trigger for ft
                sb = new StringBuilder();
                sb.append("CREATE TRIGGER IF NOT EXISTS[TGR_FT_UPDATE]")
                        .append("\r\n")
                        .append("AFTER UPDATE")
                        .append("\r\n")
                        .append("ON filetransfer")
                        .append("\r\n")
                        .append("FOR EACH ROW")
                        .append("\r\n")
                        .append("BEGIN")
                        .append("\r\n")
                        .append("UPDATE THREADS SET")
                        .append("\r\n")
                        .append("message_type = 1,")
                        .append("\r\n")
                        .append("contact = NEW.contact,")
                        .append("\r\n")
                        .append("msg_id = NEW.FT_ID,")
                        .append("\r\n")
                        .append("content = NEW.file,")
                        .append("\r\n")
                        .append("mime_type = NEW.mime_type,")
                        .append("\r\n")
                        .append("direction = NEW.direction,")
                        .append("\r\n")
                        .append("status = NEW.status,")
                        .append("\r\n")
                        .append("reason_code = NEW.status,")
                        .append("\r\n")
                        .append("read_status = NEW.read_status,")
                        .append("\r\n")
                        .append("timestamp_sent = NEW.timestamp_sent,")
                        .append("\r\n")
                        .append("timestamp_delivered = NEW.timestamp_delivered,")
                        .append("\r\n")
                        .append("timestamp_displayed = NEW.timestamp_displayed,")
                        .append("\r\n")
                        .append("chat_id = NEW.chat_id,")
                        .append("\r\n")
                        .append("timestamp = NEW.timestamp,")
                        .append("\r\n")
                        .append("message_count = (select count(message_view.chat_id) from message_view where message_view.chat_id = new.chat_id),")
                        .append("\r\n")
                        .append("unread_count = (select count(*) from message_view where message_view.chat_id = new.chat_id and message_view.status = 0 and message_view.read_status = 0)")
                        .append("\r\n")
                        .append("WHERE CHAT_ID = NEW.CHAT_ID;").append("\r\n")
                        .append("END");
                db.execSQL(sb.toString());

                // create insert trigger for message
                sb = new StringBuilder();
                sb.append("CREATE TRIGGER IF NOT EXISTS[TGR_MESSAGE_ADD]")
                        .append("\r\n")
                        .append("AFTER INSERT")
                        .append("\r\n")
                        .append("ON MESSAGE")
                        .append("\r\n")
                        .append("FOR EACH ROW")
                        .append("\r\n")
                        .append("BEGIN")
                        .append("\r\n")
                        .append("UPDATE THREADS SET")
                        .append("\r\n")
                        .append("message_type = 1,")
                        .append("\r\n")
                        .append("contact = NEW.contact,")
                        .append("\r\n")
                        .append("msg_id = NEW.msg_id,")
                        .append("\r\n")
                        .append("content = NEW.content,")
                        .append("\r\n")
                        .append("mime_type = NEW.mime_type,")
                        .append("\r\n")
                        .append("direction = NEW.direction,")
                        .append("\r\n")
                        .append("status = NEW.status,")
                        .append("\r\n")
                        .append("reason_code = NEW.status,")
                        .append("\r\n")
                        .append("read_status = NEW.read_status,")
                        .append("\r\n")
                        .append("timestamp_sent = NEW.timestamp_sent,")
                        .append("\r\n")
                        .append("timestamp_delivered = NEW.timestamp_delivered,")
                        .append("\r\n")
                        .append("timestamp_displayed = NEW.timestamp_displayed,")
                        .append("\r\n")
                        .append("chat_id = NEW.contact,")
                        .append("\r\n")
                        .append("timestamp = NEW.timestamp,")
                        .append("\r\n")
                        .append("message_count = (select count(message_view.chat_id) from message_view where message_view.chat_id = new.chat_id),")
                        .append("\r\n")
                        .append("unread_count = (select count(*) from message_view where message_view.chat_id = new.chat_id and message_view.status = 0 and message_view.read_status = 0)")
                        .append("\r\n")
                        .append("WHERE CHAT_ID = NEW.CHAT_ID;").append("\r\n")
                        .append("END");
                db.execSQL(sb.toString());

                // create update trigger for message
                sb = new StringBuilder();
                sb.append("CREATE TRIGGER IF NOT EXISTS[TGR_MESSAGE_UPDATE]")
                        .append("\r\n")
                        .append("AFTER UPDATE")
                        .append("\r\n")
                        .append("ON MESSAGE")
                        .append("\r\n")
                        .append("FOR EACH ROW")
                        .append("\r\n")
                        .append("BEGIN")
                        .append("\r\n")
                        .append("UPDATE THREADS SET")
                        .append("\r\n")
                        .append("message_type = 1,")
                        .append("\r\n")
                        .append("contact = NEW.contact,")
                        .append("\r\n")
                        .append("msg_id = NEW.msg_id,")
                        .append("\r\n")
                        .append("content = NEW.content,")
                        .append("\r\n")
                        .append("mime_type = NEW.mime_type,")
                        .append("\r\n")
                        .append("direction = NEW.direction,")
                        .append("\r\n")
                        .append("status = NEW.status,")
                        .append("\r\n")
                        .append("reason_code = NEW.status,")
                        .append("\r\n")
                        .append("read_status = NEW.read_status,")
                        .append("\r\n")
                        .append("timestamp_sent = NEW.timestamp_sent,")
                        .append("\r\n")
                        .append("timestamp_delivered = NEW.timestamp_delivered,")
                        .append("\r\n")
                        .append("timestamp_displayed = NEW.timestamp_displayed,")
                        .append("\r\n")
                        .append("chat_id = NEW.contact,")
                        .append("\r\n")
                        .append("timestamp = NEW.timestamp,")
                        .append("\r\n")
                        .append("message_count = (select count(message_view.chat_id) from message_view where message_view.chat_id = new.chat_id),")
                        .append("\r\n")
                        .append("unread_count = (select count(*) from message_view where message_view.chat_id = new.chat_id and message_view.status = 0 and message_view.read_status = 0)")
                        .append("\r\n")
                        .append("WHERE CHAT_ID = NEW.CHAT_ID;").append("\r\n")
                        .append("END");
                db.execSQL(sb.toString());
                // [Rcs]Added by zhanglei for create table threads end
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int currentVersion) {
            db.execSQL("DROP TABLE IF EXISTS ".concat(TABLE_GROUP_CHAT));
            db.execSQL("DROP TABLE IF EXISTS ".concat(TABLE_MESSAGE));
            db.execSQL("DROP TABLE IF EXISTS ".concat(TABLE));
            db.execSQL("DROP VIEW IF EXISTS ".concat("message_view"));
            db.execSQL("DROP VIEW IF EXISTS ".concat("thread_view"));
            onCreate(db);
        }
    }

    private SQLiteOpenHelper mOpenHelper;

    private String getSelectionWithChatId(String selection) {
        if (TextUtils.isEmpty(selection)) {
            return SELECTION_WITH_CHAT_ID_ONLY;
        }
        return new StringBuilder("(").append(SELECTION_WITH_CHAT_ID_ONLY).append(") AND (")
                .append(selection).append(")").toString();
    }

    private String[] getSelectionArgsWithChatId(String[] selectionArgs, String chatId) {
        String[] chatSelectionArg = new String[] {
            chatId
        };
        if (selectionArgs == null) {
            return chatSelectionArg;
        }
        return DatabaseUtils.appendSelectionArgs(chatSelectionArg, selectionArgs);
    }

    private String getSelectionWithMessageId(String selection) {
        if (TextUtils.isEmpty(selection)) {
            return SELECTION_WITH_MSG_ID_ONLY;
        }
        return new StringBuilder("(").append(SELECTION_WITH_MSG_ID_ONLY).append(") AND (")
                .append(selection).append(")").toString();
    }

    private String[] getSelectionArgsWithMessageId(String[] selectionArgs, String messageId) {
        String[] messageSelectionArg = new String[] {
            messageId
        };
        if (selectionArgs == null) {
            return messageSelectionArg;
        }
        return DatabaseUtils.appendSelectionArgs(messageSelectionArg, selectionArgs);
    }
    
    private String getSelectionWithFtId(String selection) {
        if (TextUtils.isEmpty(selection)) {
            return SELECTION_WITH_FT_ID_ONLY;
        }
        return new StringBuilder("(").append(SELECTION_WITH_FT_ID_ONLY).append(") AND (")
                .append(selection).append(")").toString();
    }

    private String[] getSelectionArgsWithFtId(String[] selectionArgs, String ftId) {
        String[] ftSelectionArg = new String[] {
            ftId
        };
        if (selectionArgs == null) {
            return ftSelectionArg;
        }
        return DatabaseUtils.appendSelectionArgs(ftSelectionArg, selectionArgs);
    }

    private String[] ftRestrictProjectionToExternallyDefinedColumns(String[] projection)
            throws UnsupportedOperationException {
        if (projection == null || projection.length == 0) {
            return FT_RESTRICTED_PROJECTION_FOR_EXTERNALLY_DEFINED_COLUMNS;
        }
        for (String projectedColumn : projection) {
            if (!FT_RESTRICTED_PROJECTION_SET.contains(projectedColumn)) {
                throw new UnsupportedOperationException(new StringBuilder(
                        "No visibility to the accessed column ").append(projectedColumn)
                        .append("!").toString());
            }
        }
        return projection;
    }

    private String[] restrictProjectionToExternallyDefinedColumns(String[] projection)
            throws UnsupportedOperationException {
        if (projection == null || projection.length == 0) {
            return RESTRICTED_PROJECTION_FOR_EXTERNALLY_DEFINED_COLUMNS;
        }
        for (String projectedColumn : projection) {
            if (!RESTRICTED_PROJECTION_SET.contains(projectedColumn)) {
                throw new UnsupportedOperationException(new StringBuilder(
                        "No visibility to the accessed column ").append(projectedColumn)
                        .append("!").toString());
            }
        }
        return projection;
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case UriType.Chat.CHAT:
                return CursorType.Chat.TYPE_DIRECTORY;

            case UriType.Chat.CHAT_WITH_ID:
                return CursorType.Chat.TYPE_ITEM;

            case UriType.Message.MESSAGE:
                return CursorType.Message.TYPE_DIRECTORY;

            case UriType.Message.MESSAGE_WITH_ID:
                return CursorType.Message.TYPE_ITEM;


            case UriType.FileTransfer.FILE_TRANSFER:
                return CursorType.TYPE_DIRECTORY;

            case UriType.FileTransfer.FILE_TRANSFER_WITH_ID:
                return CursorType.TYPE_ITEM;
                
            case UriType.Thread.ALL:
            case UriType.Thread.THREAD:
            case UriType.Thread.SEARCH:
            case UriType.Thread.THREAD_COUNT:
                return CursorType.Message.TYPE_DIRECTORY;
          
            default:
                throw new IllegalArgumentException(new StringBuilder("Unsupported URI ")
                        .append(uri).append("!").toString());
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sort) {

        Cursor cursor = null;
        String chatId, ftId;
        SQLiteDatabase db;
        SQLiteQueryBuilder qb;
        try {
            switch (sUriMatcher.match(uri)) {
                case UriType.Chat.CHAT_WITH_ID:
                    chatId = uri.getLastPathSegment();
                    selection = getSelectionWithChatId(selection);
                    selectionArgs = getSelectionArgsWithChatId(selectionArgs, chatId);
                    /* Intentional fall through */
                case UriType.Chat.CHAT:
                    db = mOpenHelper.getReadableDatabase();
                    cursor = db.query(TABLE_GROUP_CHAT,
                            projection, selection,
                            selectionArgs, null, null, sort);
                    cursor.setNotificationUri(getContext().getContentResolver(), uri);
                    return cursor;

                case UriType.Message.MESSAGE_WITH_ID:
                    String msgId = uri.getLastPathSegment();
                    selection = getSelectionWithMessageId(selection);
                    selectionArgs = getSelectionArgsWithMessageId(selectionArgs, msgId);
                    /* Intentional fall through */
                case UriType.Message.MESSAGE:
                    db = mOpenHelper.getReadableDatabase();
                    cursor = db.query(TABLE_MESSAGE, projection, selection, selectionArgs,
                            null, null, sort);
                    cursor.setNotificationUri(getContext().getContentResolver(), uri);
                    return cursor;
                                      
        		case UriType.Thread.THREAD:
        		{
        			db = mOpenHelper.getReadableDatabase();
                    cursor = db.query("thread_view", projection, selection, selectionArgs,
                            null, null, null);
                    cursor.setNotificationUri(getContext().getContentResolver(), uri);
                    return cursor;
        		}
        		case UriType.Thread.THREAD_COUNT:
        		{
        			qb = new SQLiteQueryBuilder();
        			db = mOpenHelper.getReadableDatabase();
        			qb.setTables("message_view");
        			projection = appendString(projection, "COUNT(*) AS unread");       			
        			selection = "(" + MessageLog.KEY_DIRECTION + "= 0) AND (" + MessageLog.KEY_READ_STATUS + "= 0)";
        			String groupby = MessageLog.KEY_CHAT_ID;
        			cursor = qb.query(db, projection, selection, selectionArgs,
                            groupby, null, sort);
                    cursor.setNotificationUri(getContext().getContentResolver(), uri);
                    return cursor;
        		}
        		
        		case UriType.Thread.ALL:
        		{
        			db = mOpenHelper.getReadableDatabase();   			
        			cursor = db.query("message_view", projection, selection, selectionArgs,
                            null, null, sort);
                    cursor.setNotificationUri(getContext().getContentResolver(), uri);
                    return cursor;
        		}       		
                case UriType.Thread.SEARCH:
                {
                    db = mOpenHelper.getReadableDatabase();
                    cursor = db.query(TABLE_MESSAGE, projection, selection, selectionArgs,
                            null, null, sort);
                    cursor.setNotificationUri(getContext().getContentResolver(), uri);
                    return cursor;
                }
                case UriType.FileTransfer.FILE_TRANSFER_WITH_ID:
                    ftId = uri.getLastPathSegment();
                    selection = getSelectionWithFtId(selection);
                    selectionArgs = getSelectionArgsWithFtId(selectionArgs, ftId);
                    /* Intentional fall through */
                case UriType.FileTransfer.FILE_TRANSFER:
                    db = mOpenHelper.getReadableDatabase();
                    cursor = db.query(TABLE,
                            projection, selection,
                            selectionArgs, null, null, sort);
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
    
	private String[] appendString(String[] projectionIn, String string) {
		if (projectionIn == null) return new String[]{string};
		
		int newLength = projectionIn.length + 1;
		String[] result = Arrays.copyOf(projectionIn, newLength);
		result[newLength - 1] = string;
		return result;
	}

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        Uri groupChatNotificationUri = ChatLog.GroupChat.CONTENT_URI;
        Uri notificationUri = FileTransferLog.CONTENT_URI;
        switch (sUriMatcher.match(uri)) {
            case UriType.Chat.CHAT_WITH_ID:
                String chatId = uri.getLastPathSegment();
                selection = getSelectionWithChatId(selection);
                selectionArgs = getSelectionArgsWithChatId(selectionArgs, chatId);
                groupChatNotificationUri = Uri.withAppendedPath(groupChatNotificationUri,
                        chatId);
                /* Intentional fall through */
            case UriType.Chat.CHAT:
                SQLiteDatabase db = mOpenHelper.getWritableDatabase();
                int count = db.update(TABLE_GROUP_CHAT, values, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(groupChatNotificationUri, null);
                }
                return count;

            case UriType.Message.MESSAGE_WITH_ID:
                String msgId = uri.getLastPathSegment();
                selection = getSelectionWithMessageId(selection);
                selectionArgs = getSelectionArgsWithMessageId(selectionArgs, msgId);
                /* Intentional fall through */
            case UriType.Message.MESSAGE:
                db = mOpenHelper.getWritableDatabase();
                count = db.update(TABLE_MESSAGE, values, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                return count;

            case UriType.FileTransfer.FILE_TRANSFER_WITH_ID:
                String ftId = uri.getLastPathSegment();
                selection = getSelectionWithFtId(selection);
                selectionArgs = getSelectionArgsWithFtId(selectionArgs, ftId);
                notificationUri = Uri.withAppendedPath(notificationUri, ftId);
                /* Intentional fall through */
            case UriType.FileTransfer.FILE_TRANSFER:
                db = mOpenHelper.getWritableDatabase();
                count = db.update(TABLE, values, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(notificationUri, null);
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
            case UriType.Chat.CHAT:
                /* Intentional fall through */
            case UriType.Chat.CHAT_WITH_ID:
                SQLiteDatabase db = mOpenHelper.getWritableDatabase();
                String chatId = initialValues.getAsString(GroupChatLog.KEY_CHAT_ID);
                db.insert(TABLE_GROUP_CHAT, null, initialValues);
                Uri notificationUri = Uri.withAppendedPath(ChatLog.GroupChat.CONTENT_URI, chatId);
                getContext().getContentResolver().notifyChange(notificationUri, null);
                return notificationUri;
            case UriType.Message.MESSAGE:
                /* Intentional fall through */
            case UriType.Message.MESSAGE_WITH_ID:
                db = mOpenHelper.getWritableDatabase();
                String messageId = initialValues.getAsString(MessageLog.KEY_MESSAGE_ID);
                db.insert(TABLE_MESSAGE, null, initialValues);
                notificationUri = Uri.withAppendedPath(ChatLog.Message.CONTENT_URI, messageId);
                getContext().getContentResolver().notifyChange(notificationUri, null);
                return notificationUri;

            case UriType.FileTransfer.FILE_TRANSFER:
                /* Intentional fall through */
            case UriType.FileTransfer.FILE_TRANSFER_WITH_ID:
                db = mOpenHelper.getWritableDatabase();
                String ftId = initialValues.getAsString(FileTransferLog.KEY_FT_ID);
                db.insert(TABLE, null, initialValues);
                notificationUri = Uri.withAppendedPath(FileTransferLog.CONTENT_URI, ftId);
                getContext().getContentResolver().notifyChange(notificationUri, null);
                return notificationUri;
            default:
                throw new IllegalArgumentException(new StringBuilder("Unsupported URI ")
                        .append(uri).append("!").toString());
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Uri groupChatNotificationUri = ChatLog.GroupChat.CONTENT_URI;
        Uri notificationUri = FileTransferLog.CONTENT_URI;
        switch (sUriMatcher.match(uri)) {
            case UriType.Chat.CHAT_WITH_ID:
                String chatId = uri.getLastPathSegment();
                selection = getSelectionWithChatId(selection);
                selectionArgs = getSelectionArgsWithChatId(selectionArgs, chatId);
                groupChatNotificationUri = Uri.withAppendedPath(groupChatNotificationUri,
                        chatId);
                /* Intentional fall through */
            case UriType.Chat.CHAT:
                SQLiteDatabase db = mOpenHelper.getWritableDatabase();
                int count = db.delete(TABLE_GROUP_CHAT, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(groupChatNotificationUri, null);
                }
                return count;
            case UriType.Message.MESSAGE_WITH_ID:
                String msgId = uri.getLastPathSegment();
                selection = getSelectionWithMessageId(selection);
                selectionArgs = getSelectionArgsWithMessageId(selectionArgs, msgId);
                /* Intentional fall through */
            case UriType.Message.MESSAGE:
                db = mOpenHelper.getWritableDatabase();
                count = db.delete(TABLE_MESSAGE, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                return count;

            case UriType.FileTransfer.FILE_TRANSFER_WITH_ID:
                String ftId = uri.getLastPathSegment();
                selection = getSelectionWithFtId(selection);
                selectionArgs = getSelectionArgsWithFtId(selectionArgs, ftId);
                notificationUri = Uri.withAppendedPath(notificationUri, ftId);
                /* Intentional fall through */
            case UriType.FileTransfer.FILE_TRANSFER:
                db = mOpenHelper.getWritableDatabase();
                count = db.delete(TABLE, selection, selectionArgs);
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(notificationUri, null);
                }
                return count;
            default:
                throw new IllegalArgumentException(new StringBuilder("Unsupported URI ")
                        .append(uri).append("!").toString());
        }
    }
}

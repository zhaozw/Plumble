/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.morlunk.mumbleclient.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.morlunk.jumble.model.Server;
import com.morlunk.mumbleclient.Constants;

import java.util.ArrayList;
import java.util.List;

public class PlumbleSQLiteDatabase extends SQLiteOpenHelper implements PlumbleDatabase {

    public static final String DATABASE_NAME = "mumble.db";

    public static final String TABLE_SERVER = "server";
    public static final String SERVER_ID = "_id";
    public static final String SERVER_NAME = "name";
    public static final String SERVER_HOST = "host";
    public static final String SERVER_PORT = "port";
    public static final String SERVER_USERNAME = "username";
    public static final String SERVER_PASSWORD = "password";
    public static final String TABLE_SERVER_CREATE_SQL = "CREATE TABLE IF NOT EXISTS `" + TABLE_SERVER + "` ("
            + "`" + SERVER_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "`" + SERVER_NAME + "` TEXT NOT NULL,"
            + "`" + SERVER_HOST + "` TEXT NOT NULL,"
            + "`" + SERVER_PORT + "` INTEGER,"
            + "`" + SERVER_USERNAME + "` TEXT NOT NULL,"
            + "`" + SERVER_PASSWORD + "` TEXT"
            + ");";

    public static final String TABLE_FAVOURITES = "favourites";
    public static final String FAVOURITES_ID = "_id";
    public static final String FAVOURITES_CHANNEL = "channel";
    public static final String FAVOURITES_SERVER = "server";
    public static final String TABLE_FAVOURITES_CREATE_SQL = "CREATE TABLE IF NOT EXISTS `" + TABLE_FAVOURITES + "` ("
            + "`" + FAVOURITES_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "`" + FAVOURITES_CHANNEL + "` TEXT NOT NULL,"
            + "`" + FAVOURITES_SERVER + "` INTEGER NOT NULL"
            + ");";

    public static final String TABLE_TOKENS = "tokens";
    public static final String TOKENS_ID = "_id";
    public static final String TOKENS_VALUE = "value";
    public static final String TOKENS_SERVER = "server";
    public static final String TABLE_TOKENS_CREATE_SQL = "CREATE TABLE IF NOT EXISTS `" + TABLE_TOKENS + "` ("
            + "`" + TOKENS_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "`" + TOKENS_VALUE + "` TEXT NOT NULL,"
            + "`" + TOKENS_SERVER + "` INTEGER NOT NULL"
            + ");";

    public static final String TABLE_COMMENTS = "comments";
    public static final String COMMENTS_WHO = "who";
    public static final String COMMENTS_COMMENT = "comment";
    public static final String COMMENTS_SEEN = "seen";
    public static final String TABLE_COMMENTS_CREATE_SQL = "CREATE TABLE IF NOT EXISTS `" + TABLE_COMMENTS + "` ("
            + "`" + COMMENTS_WHO + "` TEXT NOT NULL,"
            + "`" + COMMENTS_COMMENT + "` TEXT NOT NULL,"
            + "`" + COMMENTS_SEEN + "` DATE NOT NULL"
            + ");";

    public static final Integer PRE_FAVOURITES_DB_VERSION = 2;
    public static final Integer PRE_TOKENS_DB_VERSION = 3;
    public static final Integer PRE_COMMENTS_DB_VERSION = 4;
    public static final Integer CURRENT_DB_VERSION = 5;

    public PlumbleSQLiteDatabase(Context context) {
        super(context, DATABASE_NAME, null, CURRENT_DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_SERVER_CREATE_SQL);
        db.execSQL(TABLE_FAVOURITES_CREATE_SQL);
        db.execSQL(TABLE_TOKENS_CREATE_SQL);
        db.execSQL(TABLE_COMMENTS_CREATE_SQL);
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion) {
        Log.w(Constants.TAG, "Database upgrade from " + oldVersion + " to " + newVersion);
        if (oldVersion <= PRE_FAVOURITES_DB_VERSION) {
            db.execSQL(TABLE_FAVOURITES_CREATE_SQL);
        }

        if (oldVersion <= PRE_TOKENS_DB_VERSION) {
            db.execSQL(TABLE_TOKENS_CREATE_SQL);
        }

        if (oldVersion <= PRE_COMMENTS_DB_VERSION) {
            db.execSQL(TABLE_COMMENTS_CREATE_SQL);
        }
    }

    @Override
    public List<Server> getServers() {
        Cursor c = getReadableDatabase().query(
                TABLE_SERVER,
                new String[]{SERVER_ID, SERVER_NAME, SERVER_HOST,
                        SERVER_PORT, SERVER_USERNAME, SERVER_PASSWORD},
                null,
                null,
                null,
                null,
                null);

        List<Server> servers = new ArrayList<Server>();

        c.moveToFirst();
        while (!c.isAfterLast()) {
            Server server = new Server(c.getInt(c.getColumnIndex(SERVER_ID)),
                    c.getString(c.getColumnIndex(SERVER_NAME)),
                    c.getString(c.getColumnIndex(SERVER_HOST)),
                    c.getInt(c.getColumnIndex(SERVER_PORT)),
                    c.getString(c.getColumnIndex(SERVER_USERNAME)),
                    c.getString(c.getColumnIndex(SERVER_PASSWORD)));
            servers.add(server);
            c.moveToNext();
        }

        c.close();

        return servers;
    }

    @Override
    public void addServer(Server server) {
        ContentValues values = new ContentValues();
        values.put(SERVER_NAME, server.getName());
        values.put(SERVER_HOST, server.getHost());
        values.put(SERVER_PORT, server.getPort());
        values.put(SERVER_USERNAME, server.getUsername());
        values.put(SERVER_PASSWORD, server.getPassword());

        server.setId(getWritableDatabase().insert(TABLE_SERVER, null, values));
    }

    @Override
    public void updateServer(Server server) {
        ContentValues values = new ContentValues();
        values.put(SERVER_NAME, server.getName());
        values.put(SERVER_HOST, server.getHost());
        values.put(SERVER_PORT, server.getPort());
        values.put(SERVER_USERNAME, server.getUsername());
        values.put(SERVER_PASSWORD, server.getPassword());
        getWritableDatabase().update(
                TABLE_SERVER,
                values,
                SERVER_ID + "=?",
                new String[]{Long.toString(server.getId())});
    }

    @Override
    public void removeServer(Server server) {
        getWritableDatabase().delete(TABLE_SERVER, SERVER_ID + " = " + server.getId(), null);
    }

    public List<Integer> getPinnedChannels(long serverId) {

        final Cursor c = getReadableDatabase().query(
                TABLE_FAVOURITES,
                new String[]{FAVOURITES_CHANNEL},
                FAVOURITES_SERVER + "=?",
                new String[]{String.valueOf(serverId)},
                null,
                null,
                null);

        List<Integer> favourites = new ArrayList<Integer>();
        c.moveToFirst();
        while (!c.isAfterLast()) {
            favourites.add(c.getInt(0));
            c.moveToNext();
        }

        c.close();

        return favourites;
    }

    @Override
    public void addPinnedChannel(long serverId, int channelId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(FAVOURITES_CHANNEL, channelId);
        contentValues.put(FAVOURITES_SERVER, serverId);
        getWritableDatabase().insert(TABLE_FAVOURITES, null, contentValues);
    }

    @Override
    public boolean isChannelPinned(long serverId, int channelId) {
        Cursor c = getReadableDatabase().query(
                TABLE_FAVOURITES,
                new String[]{FAVOURITES_CHANNEL},
                FAVOURITES_SERVER + "=? AND " +
                FAVOURITES_CHANNEL + "=?",
                new String[]{String.valueOf(serverId), String.valueOf(channelId)},
                null,
                null,
                null);
        c.moveToFirst();
        return !c.isAfterLast();
    }

    public void removePinnedChannel(long serverId, int channelId) {
        getWritableDatabase().delete(TABLE_FAVOURITES, "server = ? AND channel = ?", new String[] { Long.toString(serverId), Integer.toString(channelId)});
    }

    @Override
    public List<String> getAccessTokens(long serverId) {
        Cursor cursor = getReadableDatabase().query(TABLE_TOKENS, new String[] { TOKENS_VALUE }, TOKENS_SERVER+"=?", new String[] { String.valueOf(serverId) }, null, null, null);
        cursor.moveToFirst();
        List<String> tokens = new ArrayList<String>();
        while(!cursor.isAfterLast()) {
            tokens.add(cursor.getString(0));
            cursor.moveToNext();
        }
        cursor.close();
        return tokens;
    }

    @Override
    public void addAccessToken(long serverId, String token) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(TOKENS_SERVER, serverId);
        contentValues.put(TOKENS_VALUE, token);
        getWritableDatabase().insert(TABLE_TOKENS, null, contentValues);
    }

    @Override
    public void removeAccessToken(long serverId, String token) {
        getWritableDatabase().delete(TABLE_TOKENS, TOKENS_SERVER+"=? AND "+TOKENS_VALUE+"=?", new String[] {String.valueOf(serverId), token });
    }

    @Override
    public boolean isCommentSeen(String hash, byte[] commentHash) {
        Cursor cursor = getReadableDatabase().query(TABLE_COMMENTS,
                new String[]{COMMENTS_WHO, COMMENTS_COMMENT, COMMENTS_SEEN}, COMMENTS_WHO + "=? AND " + COMMENTS_COMMENT + "=?",
                new String[]{hash, new String(commentHash)},
                null, null, null);
        boolean hasNext = cursor.moveToNext();
        cursor.close();
        return hasNext;
    }

    @Override
    public void markCommentSeen(String hash, byte[] commentHash) {
        ContentValues values = new ContentValues();
        values.put(COMMENTS_WHO, hash);
        values.put(COMMENTS_COMMENT, commentHash);
        values.put(COMMENTS_SEEN, "datetime('now')");
        getWritableDatabase().replace(TABLE_COMMENTS, null, values);
    }
}

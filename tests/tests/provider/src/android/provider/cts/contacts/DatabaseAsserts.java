/*
 * Copyright (C) 2013 The Android Open Source Project
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
 * limitations under the License
 */

package android.provider.cts.contacts;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.ContactsContract.Contacts;
import android.provider.cts.contacts.account.StaticAccountAuthenticator;
import android.test.MoreAsserts;

import junit.framework.Assert;

import java.util.BitSet;

/**
 * Common methods for asserting database related operations.
 */
public class DatabaseAsserts {

    public static void assertDeleteIsUnsupported(ContentResolver resolver, Uri uri) {
        try {
            resolver.delete(uri, null, null);
            Assert.fail("delete operation should have failed with UnsupportedOperationException on"
                    + uri);
        } catch (UnsupportedOperationException e) {
            // pass
        }
    }

    public static void assertInsertIsUnsupported(ContentResolver resolver, Uri  uri) {
        try {
            ContentValues values = new ContentValues();
            resolver.insert(uri, values);
            Assert.fail("insert operation should have failed with UnsupportedOperationException on"
                    + uri);
        } catch (UnsupportedOperationException e) {
            // pass
        }
    }

    /**
     * Create a contact in account 1 and assert that the record exists.
     *
     * @return The created contact id pair.
     */
    public static ContactIdPair assertAndCreateContact(ContentResolver resolver) {
        return assertAndCreateContact(resolver, StaticAccountAuthenticator.ACCOUNT_1);
    }

    /**
     * Create a contact in a specified account and assert that the record exists.
     *
     * @return The created contact id pair.
     */
    public static ContactIdPair assertAndCreateContactWithName(ContentResolver resolver,
            Account account, String name) {
        long rawContactId = RawContactUtil.createRawContactWithName(resolver, account, name);

        long contactId = RawContactUtil.queryContactIdByRawContactId(resolver, rawContactId);
        MoreAsserts.assertNotEqual(CommonDatabaseUtils.NOT_FOUND, contactId);

        return new ContactIdPair(contactId, rawContactId);
    }

    /**
     * Create a contact in a specified account and assert that the record exists.
     *
     * @return The created contact id pair.
     */
    public static ContactIdPair assertAndCreateContact(ContentResolver resolver, Account account) {
        long rawContactId = RawContactUtil.createRawContactWithAutoGeneratedName(resolver, account);

        long contactId = RawContactUtil.queryContactIdByRawContactId(resolver, rawContactId);
        MoreAsserts.assertNotEqual(CommonDatabaseUtils.NOT_FOUND, contactId);

        return new ContactIdPair(contactId, rawContactId);
    }

    /**
     * Asserts that a contact id was deleted, has a delete log, and that log has a timestamp greater
     * than the given timestamp.
     *
     * @param contactId The contact id to check.
     * @param start The timestamp that the delete log should be greater than.
     */
    public static void assertHasDeleteLogGreaterThan(ContentResolver resolver, long contactId,
            long start) {
        Assert.assertFalse(ContactUtil.recordExistsForContactId(resolver, contactId));

        long deletedTimestamp = DeletedContactUtil.queryDeletedTimestampForContactId(resolver,
                contactId);
        MoreAsserts.assertNotEqual(CommonDatabaseUtils.NOT_FOUND, deletedTimestamp);
        Assert.assertTrue(deletedTimestamp > start);
    }

    /**
     * Holds a single contact id and raw contact id relationship.
     */
    public static class ContactIdPair {
        public long mContactId;
        public long mRawContactId;

        public ContactIdPair(long contactId, long rawContactId) {
            this.mContactId = contactId;
            this.mRawContactId = rawContactId;
        }

        @Override
        public String toString() {
            return "ContactIdPair{" +
                    "mContactId=" + mContactId +
                    ", mRawContactId=" + mRawContactId +
                    '}';
        }
    }

    /**
     * Queries for a given {@link Uri} against a provided {@link ContentResolver}, and
     * ensures that the returned cursor contains exactly the expected values.
     *
     * @param resolver - ContentResolver to query against
     * @param uri - {@link Uri} to perform the query for
     * contained in <code>expectedValues</code> in order for the assert to pass.
     * @param expectedValues - Array of {@link ContentValues} which the cursor returned from the
     * query should contain.
     */
    public static void assertStoredValuesInUriMatchExactly(ContentResolver resolver, Uri uri,
            ContentValues... expectedValues) {
        assertStoredValuesInUriMatchExactly(resolver, uri, null, null, null, null, false, expectedValues);
    }

    /**
     * Queries for a given {@link Uri} against a provided {@link ContentResolver}, and
     * ensures that the returned cursor contains exactly the expected values.
     *
     * @param resolver - ContentResolver to query against
     * @param uri - {@link Uri} to perform the query for
     * @param projection - Projection to use for the query. Must contain at least the columns
     * contained in <code>expectedValues</code> in order for the assert to pass.
     * @param selection - Selection string to use for the query.
     * @param selectionArgs - Selection arguments to use for the query.
     * @param sortOrder - Sort order to use for the query.
     * @param inOrder Whether or not the returned rows in the cursor should correspond to the
     * order of the provided ContentValues
     * @param expectedValues - Array of {@link ContentValues} which the cursor returned from the
     * query should contain.
     */
    public static void assertStoredValuesInUriMatchExactly(ContentResolver resolver, Uri uri,
            String[] projection, String selection, String[] selectionArgs, String sortOrder,
            boolean inOrder, ContentValues... expectedValues) {
        final Cursor cursor = resolver.query(uri, projection, selection, selectionArgs, sortOrder);
        try {
            if (inOrder) {
                assertCursorValuesMatchExactlyInOrder(cursor, expectedValues);
            } else {
                assertCursorValuesMatchExactly(cursor, expectedValues);
            }
        } finally {
            cursor.close();
        }
    }

    /**
     * Ensures that the rows in the cursor match the rows in the expected values exactly. However,
     * does not require that the rows in the cursor are ordered the same way as those in the
     * expected values.
     *
     * @param cursor - Cursor containing the values to check for
     * @param expectedValues - Array of ContentValues that the cursor should be expected to
     * contain.
     */
    public static void assertCursorValuesMatchExactly(Cursor cursor,
            ContentValues... expectedValues) {
        Assert.assertEquals("Cursor does not contain the number of expected rows",
                expectedValues.length, cursor.getCount());
        StringBuilder message = new StringBuilder();
        // In case if expectedValues contains multiple identical values, remember which cursor
        // rows are "consumed" to prevent multiple ContentValues from hitting the same row.
        final BitSet used = new BitSet(cursor.getCount());

        for (ContentValues v : expectedValues) {
            boolean found = false;
            cursor.moveToPosition(-1);
            while (cursor.moveToNext()) {
                final int pos = cursor.getPosition();
                if (used.get(pos)) continue;
                found = equalsWithExpectedValues(cursor, v, message);
                if (found) {
                    used.set(pos);
                    break;
                }
            }
            Assert.assertTrue("Expected values can not be found " + v + "," + message.toString(),
                    found);
        }
    }

    /**
     * Ensures that the rows in the cursor match the rows in the expected values exactly. Requires
     * that the rows in the cursor are ordered the same way as those in the expected values.
     *
     * @param cursor - Cursor containing the values to check for
     * @param expectedValues - Array of ContentValues that the cursor should be expected to
     * contain.
     */
    public static void assertCursorValuesMatchExactlyInOrder(Cursor cursor,
            ContentValues... expectedValues) {
        Assert.assertEquals("Cursor does not contain the number of expected rows",
                expectedValues.length, cursor.getCount());
        StringBuilder message = new StringBuilder();

        cursor.moveToPosition(-1);
        for (ContentValues v : expectedValues) {
            cursor.moveToNext();
            Assert.assertTrue("Expected values can not be found " + v + "," + message.toString(),
                    equalsWithExpectedValues(cursor, v, message));
        }
    }


    private static boolean equalsWithExpectedValues(Cursor cursor, ContentValues expectedValues,
            StringBuilder msgBuffer) {
        for (String column : expectedValues.keySet()) {
            int index = cursor.getColumnIndex(column);
            if (index == -1) {
                msgBuffer.append(" No such column: ").append(column);
                return false;
            }
            Object expectedValue = expectedValues.get(column);
            String value;
            expectedValue = expectedValues.getAsString(column);
            value = cursor.getString(cursor.getColumnIndex(column));
            if (expectedValue != null && !expectedValue.equals(value) || value != null
                    && !value.equals(expectedValue)) {
                msgBuffer
                        .append(" Column value ")
                        .append(column)
                        .append(" expected <")
                        .append(expectedValue)
                        .append(">, but was <")
                        .append(value)
                        .append('>');
                return false;
            }
        }
        return true;
    }

    public static String buildIdSelection(long[] ids) {
        StringBuilder selection = new StringBuilder();
        selection.append(BaseColumns._ID + " in ");
        selection.append("(");
        for (int i = 0; i < ids.length; i++) {
            if (i != 0) selection.append(",");
            selection.append(ids[i]);
        }
        selection.append(")");
        return selection.toString();
    }

    /**
     * Check if a query accepts all columns in the projection, and a resulting cursor contains
     * all expected columns.  This also checks the number of resulting rows, but don't check
     * actual content in a returned cursor.
     */
    public static void checkProjection(ContentResolver resolver,
            Uri uri, String[] allColumns, long[] ids) {
        final String selection = buildIdSelection(ids);

        // First, check the null projection (i.e. all columns).
        checkProjectionInner(resolver, uri, null, allColumns, selection, ids.length,
                /* keepPosition =*/ false);

        // All columns.
        checkProjectionInner(resolver, uri, allColumns, allColumns, selection, ids.length,
                /* keepPosition =*/ true);

        // Select each column one by one.
        for (int i = 0; i < allColumns.length; i++) {
            final String[] columns = new String[] {allColumns[i]};
            checkProjectionInner(resolver, uri, columns, columns, selection, ids.length,
                        /* keepPosition =*/ true);
        }

        // Select two columns.
        for (int i = 0; i < allColumns.length; i++) {
            for (int j = 0; j < allColumns.length; j++) {
                // Requesting the same column multiple times is okay, but it'd make the column
                // order check harder.
                if (i == j) continue;
                final String[] columns = new String[] {allColumns[i], allColumns[j]};
                checkProjectionInner(resolver, uri, columns, columns, selection, ids.length,
                        /* keepPosition =*/ true);
            }
        }
    }

    private static void checkProjectionInner(ContentResolver resolver,
            Uri uri, String[] projection, String[] expectedColumns,
            String selection, int expectedRowCount, boolean keepPosition) {
        final Cursor c = resolver.query(uri, projection, selection,
                /* args =*/ null, /* sort =*/ null);
        Assert.assertNotNull(c);
        try {
            Assert.assertEquals("# of rows", expectedRowCount, c.getCount());

            // Make sure expected columns exist.
            for (int i = 0; i < expectedColumns.length; i++) {
                final String column = expectedColumns[i];
                if (keepPosition) {
                    Assert.assertEquals(column, c.getColumnName(i));
                } else {
                    Assert.assertTrue(column, c.getColumnIndex(column) >= 0);
                }
            }
        } finally {
            c.close();
        }
    }
}
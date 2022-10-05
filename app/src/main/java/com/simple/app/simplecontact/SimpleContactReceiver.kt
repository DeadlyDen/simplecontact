package com.simple.app.simplecontact

import android.content.BroadcastReceiver
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// EXAMPLES:
// adb shell
// am broadcast -a com.simple.app.simplecontact.add --es name 'test4' --es phone '12313' -n com.simple.app.simplecontact/.SimpleContactReceiver

// am broadcast -a com.simple.app.simplecontact.read_by_name --es name 'test11' -n com.simple.app.simplecontact/.SimpleContactReceiver
// am broadcast -a com.simple.app.simplecontact.read_by_phone --es phone '+38(096)2994559' -n com.simple.app.simplecontact/.SimpleContactReceiver

// am broadcast -a com.simple.app.simplecontact.modify_by_phone --es phone '+380993334535' --es new_name 'test102' --es new_phone '+38(098)2994559' -n com.simple.app.simplecontact/.SimpleContactReceiver
// am broadcast -a com.simple.app.simplecontact.modify_by_phone --es phone '+38(098)2994559'  --es new_phone '+18(092)2994559' -n com.simple.app.simplecontact/.SimpleContactReceiver

class SimpleContactReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action?.isNotEmpty() == true) {
            when (action) {
                ACTION_ADD -> {
                    addContact(context, intent)
                }
                ACTION_READ_BY_NAME -> {
                    readContactByName(context, intent)
                }
                ACTION_READ_BY_PHONE -> {
                    readContactByPhone(context, intent)
                }
                ACTION_MODIFY_BY_PHONE -> {
                    updateBy(context, intent)
                }
            }
        }
    }

    private fun addContact(context: Context, intent: Intent) {
        val name = findNameArg(intent)
        val phone = findPhoneArg(intent)
        if (name != null && phone != null) {
            // Uncomment it if u need this check.
            scope.launch(Dispatchers.IO) {
                Log.d(TAG, Thread.currentThread().name)
                if (isExistContact(context, phone)) {
                    Log.d(TAG, "Number is already exist")
                    return@launch
                }
                val contactsOp = arrayListOf<ContentProviderOperation>()
                val accountNameOp = ContentProviderOperation
                    .newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, null)
                    .withValue(RawContacts.ACCOUNT_NAME, null).build()
                contactsOp.add(accountNameOp)
                val phoneOp = ContentProviderOperation
                    .newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, phone)
                    .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                    .build()

                contactsOp.add(phoneOp)
                val nameOp = ContentProviderOperation
                    .newInsert(Data.CONTENT_URI)
                    .withValueBackReference(
                        Data.RAW_CONTACT_ID,
                        0
                    )
                    .withValue(
                        Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
                contactsOp.add(nameOp)
                kotlin.runCatching {
                    context.contentResolver.applyBatch(ContactsContract.AUTHORITY, contactsOp)
                }.onFailure {
                    Log.d(
                        TAG,
                        "ContentResolver apply batch failure:${it.message}"
                    )
                }
            }
        } else {
            Log.d(TAG, "Some of args is null: Name ${name}, Phone $phone")
        }
    }

    // use if need. But i was try added duplicate items, phone ignored it. Maybe it will be work so different on other android version.
    // maybe take some times if user have to much phones.
    private fun isExistContact(context: Context, searchPhone: String): Boolean {
        val result = kotlin.runCatching {
            val contentResolver = context.contentResolver
            val cursor =
                getContentCursor(contentResolver)
            if (cursor != null && cursor.count > 0) {
                while (cursor.moveToNext()) {
                    val contactIdIndex =
                        cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val phoneIdIndex =
                        cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                    var contactId = ""
                    var phone: Int = -1
                    if (contactIdIndex >= 0) {
                        contactId = cursor.getString(contactIdIndex)
                    }
                    if (phoneIdIndex >= 0) {
                        phone = cursor.getString(phoneIdIndex)?.toIntOrNull() ?: -1
                    }
                    if (phone > 0) {
                        val allPhonesCursor = allPhonesCursor(contentResolver, contactId)
                        if (allPhonesCursor != null) {
                            while (allPhonesCursor.moveToNext()) {
                                val currentPhoneNumberIndex = allPhonesCursor
                                    .getColumnIndex(Phone.NUMBER)
                                if (currentPhoneNumberIndex >= 0) {
                                    var currentPhone: String = allPhonesCursor
                                        .getString(currentPhoneNumberIndex)

                                    currentPhone = cleanPhoneNumber(currentPhone)
                                    val forSearch = cleanPhoneNumber(searchPhone)
                                    if (currentPhone.contains(forSearch, true)) {
                                        allPhonesCursor.close()
                                        return@runCatching true
                                    }
                                } else {
                                    allPhonesCursor.close()
                                    throw NullPointerException("Failure on get all phones")
                                }
                            }
                            allPhonesCursor.close()
                        } else {
                            throw NullPointerException("All phones cursor is null")
                        }
                    }
                }
                cursor.close()
                return@runCatching false
            } else throw NullPointerException("Cursor is null")
        }.onFailure {
            Log.v(TAG, "Failure on read: ${it.message}")
        }
        return result.getOrNull() ?: false
    }

    private fun cleanPhoneNumber(currentPhone: String) = currentPhone
        .replace("+", "")
        .replace(" ", "")
        .replace("(", "")
        .replace(")", "")

    private fun allPhonesCursor(
        contentResolver: ContentResolver,
        contactId: String
    ) = contentResolver.query(
        /* uri = */ Phone.CONTENT_URI,
        /* projection = */ null,
        /* selection = */ Phone.CONTACT_ID + " = " + contactId,
        /* selectionArgs = */ null,
        /* sortOrder = */ null
    )

    private fun getContentCursor(contentResolver: ContentResolver): Cursor? {
        return contentResolver.query(/* uri = */ ContactsContract.Contacts.CONTENT_URI, /* projection = */
                                                 null, /* selection = */
                                                 null, /* selectionArgs = */
                                                 null, /* sortOrder = */
                                                 null
        )
    }

    private fun readContactByName(context: Context, intent: Intent) {
        findBy(context = context, intent = intent, finByName = true, findByPhone = false)
    }

    private fun readContactByPhone(context: Context, intent: Intent) {
        findBy(context = context, intent = intent, finByName = false, findByPhone = true)
    }

    private fun findBy(context: Context, intent: Intent, finByName: Boolean, findByPhone: Boolean) {
        val searchName = findNameArg(intent)
        val searchPhone = findPhoneArg(intent)

        if (finByName && searchName == null) {
            Log.d(TAG, "Illegal arguments: search by name : ${finByName}, name: ${searchName}")
            return
        }
        if (findByPhone && searchPhone == null) {
            Log.d(TAG, "Illegal arguments: search by phone : ${findByPhone}, phone: ${searchPhone}")
            return
        }

        scope.launch(Dispatchers.IO) {
            kotlin.runCatching {
                val contentResolver = context.contentResolver
                val cursor =
                    getContentCursor(contentResolver)
                if (cursor != null && cursor.count > 0) {
                    while (cursor.moveToNext()) {
                        val contactIdIndex =
                            cursor.getColumnIndex(ContactsContract.Contacts._ID)

                        val nameIdIndex =
                            cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        val phoneIdIndex =
                            cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                        var contactId = ""
                        var name = ""
                        var hasPhone: Int = -1
                        if (contactIdIndex >= 0) {
                            contactId = cursor.getString(contactIdIndex)
                        }
                        if (nameIdIndex >= 0) {
                            name = cursor.getString(nameIdIndex)
                        }
                        if (phoneIdIndex >= 0) {
                            hasPhone = cursor.getString(phoneIdIndex)?.toIntOrNull() ?: -1
                        }

                        if (name.isNotEmpty() && hasPhone > 0) {
                            val allPhonesCursor = allPhonesCursor(contentResolver, contactId)

                            if (allPhonesCursor != null) {
                                while (allPhonesCursor.moveToNext()) {
                                    val currentNameIndex = allPhonesCursor
                                        .getColumnIndex(Phone.DISPLAY_NAME)
                                    val currentPhoneNumberIndex = allPhonesCursor
                                        .getColumnIndex(Phone.NUMBER)

                                    if (currentNameIndex >= 0) {
                                        val currentName: String = allPhonesCursor
                                            .getString(currentNameIndex)
                                        var currentPhone: String = allPhonesCursor
                                            .getString(currentPhoneNumberIndex)

                                        if (findByPhone) {
                                            currentPhone = cleanPhoneNumber(currentPhone)
                                            if (currentPhone.contains(
                                                    searchPhone?.toString() ?: "",
                                                    true
                                                )
                                            ) {
                                                Log.d(
                                                    TAG,
                                                    "Phone (${searchPhone}) is find: mame: $currentName"
                                                )
                                                allPhonesCursor.close()
                                            }
                                        } else if (finByName) {
                                            if (currentName.equals(searchName, true)) {
                                                Log.d(
                                                    TAG,
                                                    "Name (${searchName}) is find: phone: $currentPhone"
                                                )
                                                allPhonesCursor.close()
                                            }
                                        }

                                    } else {
                                        allPhonesCursor.close()
                                        throw NullPointerException("Failure on get all phones")
                                    }
                                }
                                allPhonesCursor.close()
                            } else {
                                throw NullPointerException("All phones cursor is null")
                            }
                        }
                    }
                    cursor.close()
                }
            }.onFailure {
                Log.v(TAG, "Failure on read: ${it.message}")
            }
        }
    }

    private fun updateBy(context: Context, intent: Intent) {
        val searchPhone = findPhoneArg(intent)

        val newName = findNewNameArg(intent)
        val newPhone = findNewPhoneArg(intent) ?: return

        kotlin.runCatching {
            val contactId = getContactIdByPhone(context, searchPhone.toString()) ?: return

            var where = java.lang.String.format(
                "%s = '%s' AND %s = ?",
                Data.MIMETYPE,  //mimetype
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                Data.CONTACT_ID/*contactId*/
            )

            val args = arrayOf(contactId)
            val operations: ArrayList<ContentProviderOperation> = ArrayList()
            if (newName != null) {
                operations.add(
                    ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                        .withSelection(where, args)
                        .withValue(
                            ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                            newName
                        )
                        .build()
                )
            }
            where = java.lang.String.format(
                "%s = '%s' AND %s = ?",
                Data.MIMETYPE,  //mimetype
                Phone.CONTENT_ITEM_TYPE,
                Data.DATA1/*number*/
            )

            args[0] = searchPhone ?: ""

            operations.add(
                ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                    .withSelection(where, args)
                    .withValue(Data.DATA1 /*number*/, newPhone)
                    .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                    .build()
            )
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
        }.onFailure {
            Log.d(TAG, "Failure on update: ${it.message}")
        }
    }

    private fun getContactIdByPhone(context: Context, number: String): String? {
        val cursor = context.contentResolver.query(
            Phone.CONTENT_URI, arrayOf(Phone.CONTACT_ID, Phone.NUMBER),
            Phone.NUMBER + "=?", arrayOf(number),
            null
        )
        if (cursor == null || cursor.count == 0) return null
        cursor.moveToFirst()
        val contactIndex = cursor.getColumnIndex(Phone.CONTACT_ID)
        var id: String? = ""
        if (contactIndex >= 0) {
            id = cursor.getString(contactIndex)
        } else {
            return null
        }
        cursor.close()
        return id
    }


    private fun findNameArg(intent: Intent): String? {
        return intent.getStringExtra(ARG_NAME)
    }

    private fun findNewNameArg(intent: Intent): String? {
        return intent.getStringExtra(NEW_NAME)
    }

    private fun findPhoneArg(intent: Intent): String? {
        return intent.getStringExtra(ARG_PHONE)
    }

    private fun findNewPhoneArg(intent: Intent): String? {
        return intent.getStringExtra(NEW_PHONE)
    }

    companion object {
        private const val TAG = "SimpleContactTag"

        private const val ACTION_ADD = "com.simple.app.simplecontact.add"

        private const val ACTION_READ_BY_NAME = "com.simple.app.simplecontact.read_by_name"
        private const val ACTION_READ_BY_PHONE = "com.simple.app.simplecontact.read_by_phone"

        private const val ACTION_MODIFY_BY_PHONE = "com.simple.app.simplecontact.modify_by_phone"

        private const val ARG_NAME = "name"
        private const val ARG_PHONE = "phone"

        private const val NEW_NAME = "new_name"
        private const val NEW_PHONE = "new_phone"
    }
}
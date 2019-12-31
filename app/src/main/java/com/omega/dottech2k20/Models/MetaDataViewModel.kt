package com.omega.dottech2k20.Models

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import es.dmoral.toasty.Toasty

class MetaDataViewModel(application: Application) : AndroidViewModel(application) {


	lateinit var mFaqLiveData: MutableLiveData<List<FAQ>>
	lateinit var mAboutDescription: MutableLiveData<String>
	lateinit var mContactsLiveData: MutableLiveData<List<Contact>>
	val TAG = this.javaClass.simpleName
	val mFirestore = FirebaseFirestore.getInstance()
	val FAQ_DOC_NAME = "FAQ"
	val META_DATA_COLLECTION_NAME = "MetaData"

	fun getFAQs(): LiveData<List<FAQ>> {
		if (!::mFaqLiveData.isInitialized) {
			mFaqLiveData = MutableLiveData()
			val query = mFirestore.collection(META_DATA_COLLECTION_NAME).document(FAQ_DOC_NAME)
			query.get().addOnCompleteListener {
				if (it.isSuccessful) {
					val result = it.result
					result?.let { res ->
						val data = res.data
						if (data != null) {
							val faqs = data["faqs"] as List<HashMap<String, String>>
							val listOfFaqs = mutableListOf<FAQ>()
							for (faq in faqs) {
								val question = faq["question"]
								val answer = faq["answer"]
								if (question != null && answer != null) {
									listOfFaqs.add(FAQ(question, answer))
								}
							}
							mFaqLiveData.value = listOfFaqs
						}
					}
				}
			}
		}
		return mFaqLiveData
	}

	fun requestQuery(uid: String, query: String) {
		val queryMap = hashMapOf<String, String>()
		queryMap[uid] = query

		val firestoreQuery = mFirestore.collection("MetaData").document(FAQ_DOC_NAME)
		firestoreQuery.update(FieldPath.of("pendingFaqs"), FieldValue.arrayUnion(queryMap))
			.addOnCompleteListener {
				if (it.isSuccessful) {
					displayFeedback("Request Placed Successfully")
					Log.i(TAG, "FAQ request successfully placed : $queryMap")
				}
			}
	}


	fun getAboutDescription(): LiveData<String> {
		if (!::mAboutDescription.isInitialized) {
			mAboutDescription = MutableLiveData()

			val query = mFirestore.collection(META_DATA_COLLECTION_NAME).document("about")
			query.get().addOnCompleteListener {
				if (it.isSuccessful) {
					it.result?.let { res ->
						mAboutDescription.value = res["about"] as String
					}
				}
			}
		}

		return mAboutDescription
	}

	fun getContacts(): LiveData<List<Contact>> {

		if (!::mContactsLiveData.isInitialized) {
			mContactsLiveData = MutableLiveData()

			val query = mFirestore.collection(META_DATA_COLLECTION_NAME).document("Contacts")

			query.get().addOnCompleteListener {
				if (it.isSuccessful) {
					it.result?.let { res ->
						val contactsMap = res.get("contacts") as List<HashMap<String, String>>
						val contacts = mutableListOf<Contact>()
						for (obj in contactsMap) {
							val contact = Contact(obj["post"], obj["name"], obj["contactDetail"])
							contacts.add(contact)
						}

						mContactsLiveData.value = contacts
					}
				}
			}
		}


		return mContactsLiveData
	}

	private fun displayFeedback(message: String) {
		try {
			Toasty.success(getApplication(), message, Toast.LENGTH_SHORT).show()
		} catch (e: Exception) {
			Log.e(TAG, "Error Occured while processing Toasty Request: ", e)
		}
	}
}
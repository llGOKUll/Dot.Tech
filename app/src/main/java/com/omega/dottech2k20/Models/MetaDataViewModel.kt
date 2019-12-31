package com.omega.dottech2k20.Models

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.*
import com.omega.dottech2k20.Utils.AuthenticationUtils
import es.dmoral.toasty.Toasty

class MetaDataViewModel(application: Application) : AndroidViewModel(application) {


	lateinit var mFaqLiveData: MutableLiveData<List<FAQ>>
	lateinit var mAboutDescription: MutableLiveData<String>
	lateinit var mContactsLiveData: MutableLiveData<List<Contact>>
	lateinit var mReportsLiveData: MutableLiveData<List<Report>>
	val TAG = this.javaClass.simpleName
	val mFirestore = FirebaseFirestore.getInstance()
	val FAQ_DOC_NAME = "FAQ"
	val META_DATA_COLLECTION_NAME = "MetaData"


	private fun addDocumentSnapShotListener(
		doc: DocumentReference,
		callback: (snapshot: DocumentSnapshot) -> Unit
	) {
		doc.addSnapshotListener { snapshot, e ->
			when {
				e != null -> {
					Log.w(TAG, "Listen failed.", e)
					return@addSnapshotListener
				}
				snapshot != null && snapshot.exists() -> callback(snapshot)
				else -> Log.d(TAG, "Current data: null")
			}

		}
	}

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

	fun getReports(): MutableLiveData<List<Report>> {
		if (!::mReportsLiveData.isInitialized) {
			mReportsLiveData = MutableLiveData()

			val query = mFirestore.collection(META_DATA_COLLECTION_NAME).document("Reports")
			addDocumentSnapShotListener(query) { res ->
				val listOfReports = mutableListOf<Report>()

				val listOfBugs = res.get("bugReports") as List<HashMap<String, String>>
				val listOfFeatureRequests =
					res.get("featureRequests") as List<HashMap<String, String>>

				for (bug in listOfBugs) {
					listOfReports.add(Report("bug", bug["title"], bug["description"]))
				}

				for (feature in listOfFeatureRequests) {
					listOfReports.add(Report("feature", feature["title"], feature["description"]))
				}

				mReportsLiveData.value = listOfReports
				Log.d(TAG, "Reports data set $listOfReports")
			}
		}
		return mReportsLiveData
	}

	fun addBugReport(title: String, description: String) {
		val currentUser = AuthenticationUtils.currentUser
		if (currentUser != null) {
			val map = hashMapOf("title" to title, "description" to description)
			val query = mFirestore.collection(META_DATA_COLLECTION_NAME).document("Reports")
			query.update("bugReports", FieldValue.arrayUnion(map)).addOnCompleteListener {
				if (it.isSuccessful) {
					displayFeedback("Thank you, Bug report has been submitted")
				}
			}
		}
	}

	fun addFeatureRequest(title: String, description: String) {
		val currentUser = AuthenticationUtils.currentUser
		if (currentUser != null) {
			val map = hashMapOf("title" to title, "description" to description)
			val query = mFirestore.collection(META_DATA_COLLECTION_NAME).document("Reports")
			query.update("featureRequests", FieldValue.arrayUnion(map)).addOnCompleteListener {
				if (it.isSuccessful) {
					displayFeedback("Thank you, Feature request has been submitted")
				}
			}
		}
	}

	private fun displayFeedback(message: String) {
		try {
			Toasty.success(getApplication(), message, Toast.LENGTH_SHORT).show()
		} catch (e: Exception) {
			Log.e(TAG, "Error Occured while processing Toasty Request: ", e)
		}
	}
}
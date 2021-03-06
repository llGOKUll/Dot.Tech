package com.omega.dottech2k20.models

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.omega.dottech2k20.Fragments.EventTeamsFragment
import com.omega.dottech2k20.Utils.FirestoreFieldNames.EVENT_COLLECTION
import com.omega.dottech2k20.Utils.FirestoreFieldNames.EVENT_PARTICIPANTS_COUNT_FIELD
import com.omega.dottech2k20.Utils.FirestoreFieldNames.EVENT_TEAM_COLLECTION
import com.omega.dottech2k20.Utils.FirestoreFieldNames.TEAMMATES_FIELD
import com.omega.dottech2k20.Utils.FirestoreFieldNames.USERS_EVENT_FIELD
import com.omega.dottech2k20.Utils.FirestoreFieldNames.USER_COLLECTION
import com.omega.dottech2k20.Utils.SharedPreferenceUtils
import es.dmoral.toasty.Toasty
import java.util.*

class UserEventViewModel(application: Application) : AndroidViewModel(application) {

	private var mUserProfileLiveData: MutableLiveData<User> = MutableLiveData()
	private var mEventsLiveData: MutableLiveData<List<Event>> = MutableLiveData()
	private lateinit var mUserEventsLiveData: MediatorLiveData<List<Event>>
	// index used for resuming and storing index of event on pause
	private val mEventIndex: MutableLiveData<Int> = MutableLiveData()

	private val mFirestore = FirebaseFirestore.getInstance()
	private val mFireAuth = FirebaseAuth.getInstance()
	private val TAG: String = javaClass.simpleName

	fun getUserProfile(runAfterFetching: (user: User) -> Unit = {}): LiveData<User>? {
		val user = mFireAuth.currentUser ?: return null
		val uid = user.uid

		val document: DocumentReference = mFirestore.collection("Users").document(uid)
		if (mUserProfileLiveData.value == null) {
			FirebaseSnapshotListeners.addDocumentSnapShotListener(document) {
				val userObject = it.toObject(User::class.java)
				mUserProfileLiveData.value = userObject
				if (userObject != null) {
					runAfterFetching(userObject)
				} else {
					Log.e(TAG, "User Object Is Null : ", NullPointerException())
				}
			}
		}
		return mUserProfileLiveData

	}

	/**
	 * @deprecated
	 * 	Update User data in Users Collection.
	 *
	 * 	Note :- the data is being replaced!
	 */
	fun updateNotificationId(notificationId: String) {

		val curUser = mFireAuth.currentUser
		if (curUser != null) {
			mFirestore.collection(USER_COLLECTION).document(curUser.uid)
				.update("notificationIds", FieldValue.arrayUnion(notificationId))
		}
	}

	/**
	 *  Updates given user's information across all it's instances e.g Event's Participant's
	 *  sub collection.
	 *
	 *  Note: The Data would be replaced and not merged.
	 */
	fun updateUserInformation(user: User, onSuccessCallback: () -> Unit) {
		mFirestore.runBatch { batch ->
			user.id?.let { userId ->
				// re-set User (Update user)
				val userDoc = mFirestore.collection("Users").document(userId)
				batch.set(userDoc, user)
				val userEventIds = user.events
				for (eventIds in userEventIds) {
					val eventId = eventIds.eventId
					val eventParticipantDoc: DocumentReference

					if (eventId != null) {// Events -> Participant -> user_doc (Update user_doc)
						eventParticipantDoc = mFirestore.collection("Events")
							.document(eventId).collection("Participants").document(userId)

						// Update name in visibleParticipants Map of Event
						user.fullName?.let { name ->
							val eventDoc = mFirestore.collection("Events")
								.document(eventId)
							batch.update(
								eventDoc,
								FieldPath.of("visibleParticipants"),
								hashMapOf(userId to name)
							)
						}
						batch.set(eventParticipantDoc, user)
					}
				}
			}
		}.addOnCompleteListener {
			if (it.isSuccessful) {
				onSuccessCallback()
			}
		}
	}


	/**
	 * Return LiveData Instance of List of All Events.
	 *
	 * Note:- Only one snapshot listener is attached irrespective of number of calls. Once the
	 * SnapShot listener is attached, the same is returned when requested, instead of attaching new
	 * listener.
	 *
	 */
	fun getEvents(): LiveData<List<Event>> {
		val eventTask = mFirestore.collection("Events").orderBy(FieldPath.of("orderPreference"))

		if (mEventsLiveData.value == null) {
			FirebaseSnapshotListeners.addQuerySnapShotListener(eventTask) {
				mEventsLiveData.value = it.toObjects(Event::class.java)
			}
		}
		return mEventsLiveData
	}

	/**
	 * Adds currently signed in user to the Event.
	 *
	 * Executes queries in Batch, writes to Participants sub-collection of Event as well as Adds the
	 * Event Id to User's profile, and increment participantCount of Event.
	 *
	 * @param event : Event which user will join
	 */
	fun joinEvent(event: Event, isAnonymous: Boolean) {
		val user: User? = mUserProfileLiveData.value
		if (user == null) {
			val uid = mFireAuth.currentUser?.uid ?: return

			val document: DocumentReference = mFirestore.collection("Users").document(uid)
			document.get().addOnCompleteListener { task ->
				if (task.isSuccessful) {
					// Check of nullability
					task.result?.let { documentSnapshot ->
						documentSnapshot.toObject(User::class.java)?.let {
							mUserProfileLiveData.value = it
							addUserInEvent(event, it, isAnonymous)
						}
					}

				} else {
					Toasty.error(getApplication(), "Joining Event Failed").show()
					Log.e(TAG, "Failed To get User : ", task.exception)
				}
			}
		} else {
			addUserInEvent(event, user, isAnonymous)
		}
	}

	private fun addUserInEvent(
		event: Event,
		user: User,
		isAnonymous: Boolean = false // By Default allow listing
	) {
		// Inside Sub-collection ( Participants ), set the id of participant document as UID
		// done to easily retrieve the document
		val eventId = event.id
		val userId = user.id
		if (eventId != null && userId != null && user.fullName != null) {
			val eventParticipantsDoc = mFirestore
				.collection("Events")
				.document(eventId).collection("Participants").document(userId)
			val userEvents = mFirestore.collection("Users").document(userId)
			val events = mFirestore.collection("Events").document(eventId)

			// visibleParticipants - with user permission make the user's name public in listing
			// those who do not wish their names to be made public, will be shown as anonymous users
			// i.e the difference between visibleParticipants count and participants could
			var visibleParticipants: Participant? = null
			if (!isAnonymous) {
				visibleParticipants = Participant(userId, user.fullName)
			}

			mFirestore.runBatch {
				val eventIds = Ids(eventId) // Event Ids and Team Id.
				// add event id to user's events field
				it.update(userEvents, FieldPath.of("events"), FieldValue.arrayUnion(eventIds))
				// add Event to participant's collection inside Event
				it.set(eventParticipantsDoc, user)
				// increment participantCount of Event
				it.update(events, FieldPath.of("participantCount"), FieldValue.increment(1))
				// update event's visibleParticipants
				if (visibleParticipants != null) {
					it.update(
						events,
						FieldPath.of("visibleParticipants"),
						FieldValue.arrayUnion(visibleParticipants)
					)
				}
			}.addOnCompleteListener {
				if (it.isSuccessful) {
					Toasty.success(getApplication(), "Joining Event Successful").show()
				} else {
					Toasty.error(getApplication(), "Failed To Join Event").show()
					Log.e(TAG, "Joining Events Failed")
				}
			}
		} else {
			Toasty.error(getApplication(), "Failed to join event").show()
		}
	}

	/**
	 * UnJoin or Leave the Event which the currently signed in user is part of(joined).
	 *
	 * The user instances from this events Participants sub-collection is deleted, ParticipantCount
	 * is decremented and from User collection the Event id is removed from `events` array.
	 */
	fun unjoinEvents(event: Event) {

		val user: User? = mUserProfileLiveData.value

		if (user == null) {
			val uid = mFireAuth.currentUser?.uid ?: return

			val document: DocumentReference = mFirestore.collection("Users").document(uid)
			document.get().addOnCompleteListener {
				if (it.isSuccessful) {
					it.result?.let { res ->
						val userProfile = res.toObject(User::class.java)
						if (userProfile != null) {
							mUserProfileLiveData.value = userProfile
							removeUserFromEvent(event, userProfile)
						}
					}
				} else {
					Log.e(TAG, "Failed To get User : ", it.exception)
				}
			}
		} else {
			removeUserFromEvent(event, user)
		}
	}

	private fun removeUserFromEvent(
		event: Event,
		user: User
	) {

		val eventId = event.id
		val userId = user.id
		if (eventId != null && userId != null && user.fullName != null) {
			val eventParticipantsDoc = mFirestore
				.collection("Events")
				.document(eventId).collection("Participants").document(userId)

			val userEvents = mFirestore.collection("Users").document(userId)

			val events = mFirestore.collection("Events").document(event.id)

			val visibleParticipants = Participant(userId, user.fullName)

			mFirestore.runBatch {
				val eventIds = Ids(eventId) // Event Ids and Team Id.
				// Delete User doc inside Participant collection of Event
				it.delete(eventParticipantsDoc)
				// Remove event ids from events field of User
				it.update(userEvents, FieldPath.of("events"), FieldValue.arrayRemove(eventIds))
				// decrement participantCount
				it.update(events, FieldPath.of("participantCount"), FieldValue.increment(-1))
				// remove participant from visibleParticipant map
				it.update(
					events,
					FieldPath.of("visibleParticipants"),
					FieldValue.arrayRemove(visibleParticipants)
				)
			}.addOnCompleteListener {
				if (it.isSuccessful) {
					Toasty.success(getApplication(), "Leaving Event Successful").show()
				} else {
					Toasty.error(getApplication(), "Failed To Leave Event").show()
					// since task failed, remove timestamp from sp
					SharedPreferenceUtils.removeTimestamp(getApplication(), eventId)
				}
			}
		} else {
			Toasty.error(getApplication(), "Failed to leave event").show()
		}
	}

	/**
	 *  Returns LiveData of List of Events which the User has joined.
	 *
	 *  This computed from User's `events` field, which is a list of event ids, and Events.
	 *  The events from Events data having id in common with `events` field of user are added to list.
	 */
	fun getUserEvent(): LiveData<List<Event>>? {
		// The MediatorLiveData is used to serialize data coming from two asynchronous sources
		// i.e mUserProfileLiveData and mEventsLiveData, to get the events participated by a user
		// we need both of them. Hence, Using MediatorLiveDat we can add them as sources and when
		// the data of either changes the final output would be changed too.

		if (!::mUserEventsLiveData.isInitialized) {
			// Initialize live data in case it hasn't been
			if (mUserProfileLiveData.value == null) {
				getUserProfile()
			}
			if (mEventsLiveData.value == null) {
				getEvents()
			}
			mUserEventsLiveData = MediatorLiveData()

			mUserEventsLiveData.apply {
				addSource(mUserProfileLiveData) {
					mUserEventsLiveData.value =
						liveDataChanged(mUserProfileLiveData, mEventsLiveData)
				}
				addSource(mEventsLiveData) {
					mUserEventsLiveData.value =
						liveDataChanged(mUserProfileLiveData, mEventsLiveData)
				}
			}
		}

		return mUserEventsLiveData
	}

	private fun liveDataChanged(
		userLiveData: MutableLiveData<User>,
		eventsLiveData: MutableLiveData<List<Event>>
	): List<Event>? {
		if (userLiveData.value != null && eventsLiveData.value != null) {
			userLiveData.value?.let { user ->
				val eventIds = user.events
				val events = eventsLiveData.value

				if (events != null) {
					return getMatchingEventsById(eventIds, events)
				}
			}
		}
		return null
	}


	/**
	 * Return ArrayList of Event having Ids matching with eventIds
	 */
	private fun getMatchingEventsById(
		eventIds: List<Ids>,
		events: List<Event>
	): ArrayList<Event> {
		val list = arrayListOf<Event>()
		for (id in eventIds) {
			val eventId = id.eventId
			val event: Event? = events.find { event ->
				if (eventId != null && event.id != null) {
					event.id == eventId
				} else {
					false
				}
			}
			if (event != null) {
				list.add(event)
			}
		}
		return list
	}

	fun getEventIndex(): Int? {
		return mEventIndex.value
	}

	fun setEventIndex(index: Int?) {
		mEventIndex.value = index
	}

	//	-------------------- Team CRUD/Join/Leave ------------------------------  //

	lateinit var mTeams: MutableLiveData<HashMap<String, List<Team>>>
	// Of form { id : [teamObj1,teamObj2...] }

	fun getTeamsOfEvent(eventId: String): MutableLiveData<HashMap<String, List<Team>>> {

		if (!::mTeams.isInitialized) {
			mTeams = MutableLiveData()
			mTeams.value = hashMapOf()
		}

		val query = mFirestore.collection(EVENT_COLLECTION).document(eventId).collection(
			EVENT_TEAM_COLLECTION
		)

		FirebaseSnapshotListeners.addQuerySnapShotListener(query) {
			val teams = it.toObjects(Team::class.java)
			val curValue = mTeams.value
			curValue?.put(eventId, teams)
			mTeams.value = curValue
		}

		return mTeams
	}

	fun createTeamToEvent(eventId: String, teamName: String, teamPasscode: String) {
		val user: User? = mUserProfileLiveData.value
		if (user == null) {
			val uid = mFireAuth.currentUser?.uid ?: return
			val document: DocumentReference = mFirestore.collection("Users").document(uid)
			document.get().addOnCompleteListener { task ->
				if (task.isSuccessful) {
					// Check of nullability
					task.result?.let { documentSnapshot ->
						documentSnapshot.toObject(User::class.java)?.let {
							mUserProfileLiveData.value = it
							addTeamToEvent(eventId, teamName, it, teamPasscode)
						}
					}
				} else {
					Toasty.error(getApplication(), "Failed To Create Team").show()
				}
			}
		} else {
			addTeamToEvent(eventId, teamName, user, teamPasscode)
		}
	}

	private fun addTeamToEvent(
		eventId: String,
		teamName: String,
		creator: User,
		teamPasscode: String
	) {
		val query = mFirestore.collection(EVENT_COLLECTION).document(eventId).collection(
			EVENT_TEAM_COLLECTION
		).document()
		val updateParticipantsCountQuery = mFirestore.collection(EVENT_COLLECTION).document(eventId)
		val teamId = query.id
		val uid = creator.id
		val fullName = creator.fullName
		if (uid != null && fullName != null) {
			val updateUserQuery = mFirestore.collection(USER_COLLECTION).document(uid)
			val ids = Ids(eventId, teamId)

			val teammateObject = Teammate(uid, fullName) // since creator is a teammate too
			val team = Team(
				teamId,
				teamName,
				uid,
				teamPasscode,
				listOf(teammateObject)
			)
			mFirestore.runBatch { batch ->
				batch.set(query, team) // Create Team inside Event Collection
				batch.update(           // Make event and team part of User's events field
					updateUserQuery,
					FieldPath.of(USERS_EVENT_FIELD),
					FieldValue.arrayUnion(ids)
				)
				batch.update(
					updateParticipantsCountQuery,
					EVENT_PARTICIPANTS_COUNT_FIELD,
					FieldValue.increment(1)
				)
			}.addOnCompleteListener {
				if (it.isSuccessful) {
					Toasty.success(getApplication(), "Team Creation Successful!").show()
				} else {
					Toasty.error(getApplication(), "Team Creation Failed").show()
					Log.e(TAG, "Failed to create team", it.exception)
				}
			}
		}

	}

	/**
	 *  Delete the team from the respective event
	 *
	 *  All the teammates, including the Creator are first removed the team, then Team document is
	 *  deleted. Write are queued in Batch, which guaranty that if one write, no data is written,
	 *  changes are rolled back
	 *
	 *  @param team The Team which has to be deleted
	 *  @param eid The Event Id of the event which team belongs to
	 */
	fun deleteTeamFromEvent(team: Team, eid: String) {
		val tid = team.id
		if (tid != null) {
			val teamQuery = mFirestore.collection(EVENT_COLLECTION).document(eid).collection(
				EVENT_TEAM_COLLECTION
			).document(tid)
			val updateParticipantsCountQuery = mFirestore.collection(EVENT_COLLECTION).document(eid)
			val ids = Ids(eid, tid)
			mFirestore.runTransaction { batch ->
				val teamObject = batch.get(teamQuery).toObject(Team::class.java)
				val teammates = teamObject?.teammates
				if (teammates != null) {
					// Remove all teammates
					for (teammate in teammates) {
						val teammateId = teammate.id
						if (teammateId != null) {
							val updateUserQuery =
								mFirestore.collection(USER_COLLECTION).document(teammateId)
							// Remove teammate
							batch.update(
								teamQuery,
								FieldPath.of(TEAMMATES_FIELD),
								FieldValue.arrayRemove(teammate)
							)
							// Remove ids to user's `events` field
							batch.update(
								updateUserQuery,
								FieldPath.of(USERS_EVENT_FIELD),
								FieldValue.arrayRemove(ids)
							)
							// update participant's counter
							batch.update(
								updateParticipantsCountQuery, FieldPath.of(
									EVENT_PARTICIPANTS_COUNT_FIELD
								), FieldValue.increment(-1)
							)
						}
					}
					// Delete team
					batch.delete(teamQuery)
				}
			}.addOnCompleteListener {
				if (it.isSuccessful) {
					Toasty.success(getApplication(), "Deleting Team Successful").show()
				} else {
					Toasty.error(getApplication(), "Deleting Team Failed").show()
					Log.e(TAG, "Failed to Delete Team", it.exception)
					SharedPreferenceUtils.removeTimestamp(
						getApplication(),
						EventTeamsFragment.getSharedPreferenceIdForTeam(tid)
					)
				}
			}

		}
	}

	fun joinTeam(event: Event, team: String) {
		val user: User? = mUserProfileLiveData.value
		if (user == null) {
			val uid = mFireAuth.currentUser?.uid ?: return

			val document: DocumentReference = mFirestore.collection("Users").document(uid)
			document.get().addOnCompleteListener { task ->
				if (task.isSuccessful) {
					task.result?.let { documentSnapshot ->
						documentSnapshot.toObject(User::class.java)?.let {
							mUserProfileLiveData.value = it
							addUserToTeam(event, team, it)
						}
					}
				} else {
					Toasty.error(getApplication(), "Join Team Failed").show()
					Log.e(TAG, "Failed To get User : ", task.exception)
				}
			}
		} else {
			addUserToTeam(event, team, user)
		}
	}

	private fun addUserToTeam(event: Event, tid: String, user: User) {
		val eid = event.id
		val uid = user.id
		val fullName = user.fullName

		if (eid != null && uid != null && fullName != null) {
			val teamQuery =
				mFirestore.collection(EVENT_COLLECTION).document(eid).collection(
					EVENT_TEAM_COLLECTION
				).document(tid)
			val updateUserQuery = mFirestore.collection(USER_COLLECTION).document(uid)
			val updateParticipantsCountQuery = mFirestore.collection(EVENT_COLLECTION).document(eid)

			val ids = Ids(eid, tid)
			val teammate = Teammate(uid, fullName)

			mFirestore.runTransaction { transaction ->
				val team = transaction.get(teamQuery).toObject(Team::class.java)
				val teamSize = event.teamSize
				val nTeammates = team?.teammates?.size
				if (teamSize != null && nTeammates != null) {
					if (nTeammates < teamSize) {
						// Add teammate
						transaction.update(
							teamQuery,
							FieldPath.of(TEAMMATES_FIELD),
							FieldValue.arrayUnion(teammate)
						)
						// Add ids to user's `events` field
						transaction.update(
							updateUserQuery,
							FieldPath.of(USERS_EVENT_FIELD),
							FieldValue.arrayUnion(ids)
						)
						// update participant's counter
						transaction.update(
							updateParticipantsCountQuery, FieldPath.of(
								EVENT_PARTICIPANTS_COUNT_FIELD
							), FieldValue.increment(1)
						)
					}
				}
			}.addOnCompleteListener {
				if (it.isSuccessful) {
					Toasty.success(getApplication(), "Joining Team Successful").show()
				} else {
					Toasty.error(getApplication(), "Joining Team Failed").show()
				}
			}
		}
	}

	fun removeTeammate(eid: String, tid: String, teammate: Teammate) {
		val teammateId = teammate.id
		if (teammateId != null) {
			val updateTeammateQuery =
				mFirestore.collection(EVENT_COLLECTION).document(eid).collection(
					EVENT_TEAM_COLLECTION
				).document(tid)
			val updateUserQuery = mFirestore.collection(USER_COLLECTION).document(teammateId)
			val updateParticipantsCountQuery = mFirestore.collection(EVENT_COLLECTION).document(eid)
			val ids = Ids(eid, tid)

			mFirestore.runTransaction { batch ->
				// Remove teammate
				batch.update(
					updateTeammateQuery,
					FieldPath.of(TEAMMATES_FIELD),
					FieldValue.arrayRemove(teammate)
				)
				// Remove ids to user's `events` field
				batch.update(
					updateUserQuery,
					FieldPath.of(USERS_EVENT_FIELD),
					FieldValue.arrayRemove(ids)
				)
				// update participant's counter
				batch.update(
					updateParticipantsCountQuery, FieldPath.of(
						EVENT_PARTICIPANTS_COUNT_FIELD
					), FieldValue.increment(-1)
				)

			}.addOnCompleteListener {
				if (it.isSuccessful) {
					Toasty.success(getApplication(), "Leaving Team Successful").show()
				} else {
					Toasty.error(getApplication(), "Leaving Team Failed").show()
					// since task failed, remove timestamp from sp
					SharedPreferenceUtils.removeTimestamp(getApplication(), eid)
				}
			}
		}
	}
}


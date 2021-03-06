package com.omega.dottech2k20.Adapters

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.auth.FirebaseUser
import com.omega.dottech2k20.R
import com.omega.dottech2k20.Utils.AuthenticationUtils
import com.omega.dottech2k20.dialogs.BinaryDialog
import com.omega.dottech2k20.models.Team
import com.omega.dottech2k20.models.Teammate
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import com.xwray.groupie.kotlinandroidextensions.Item
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.item_team.*
import kotlinx.android.synthetic.main.item_teammate.*

/**
 *  Responsible for a populating individual team and callback on it.
 *  1) Remove Teammate (Only to Creator)
 *  2) Remove self (Only to logged in user)
 *  3) Delete Team (Only if user is the creator and User tries to leave the Team)
 *  4) Join Team (Any valid user - Verified email)
 */
class TeamItem(
	val context: Context,
	private val mTeam: Team,
	private val isUserPartOfTeam: Boolean, // If user is part of the event
	private val isReadOnly: Boolean, // Do not allow any actions on Team
	private val mTeamSize: Int,
	val onDeleteTeamCallback: (team: Team) -> Unit,
	private val onRemoveTeammateCallback: (team: Team, teammate: Teammate) -> Unit,
	val onJoinTeamCallback: (team: Team) -> Unit
) : Item() {

	override fun bind(viewHolder: GroupieViewHolder, position: Int) {
		viewHolder.apply {
			val currentUser = AuthenticationUtils.currentUser
			val (id, name, creator, passcode, teammates) = mTeam
			if (id != null && name != null && creator != null && passcode != null) {
				tv_team_name.text = name
				val isUserCreator = currentUser?.uid.equals(creator)
				setDeleteTeamCallback(isUserCreator)
				setJoinTeamCallback(isUserCreator)
				setLeaveTeamCallback(teammates, currentUser, isUserCreator)
				// Show Remove teammate option only if user is creator and registration is open
				initTeammatesRV(viewHolder, isUserCreator && !isReadOnly)
				if (isReadOnly) {
					imbtn_remove_team.visibility = View.GONE
					btn_leave.visibility = View.GONE
					btn_join.visibility = View.GONE
				}
			} else {
				Toasty.info(context, "NO Teams to display")
				// Change visibility of card
			}
		}
	}

	private fun GroupieViewHolder.setLeaveTeamCallback(
		teammates: List<Teammate>,
		currentUser: FirebaseUser?,
		userCreator: Boolean
	) {
		for (teammate in teammates) {
			// if join is disabled then leave team is not permitted
			val uid = currentUser?.uid
			if (uid == teammate.id && isUserPartOfTeam && !userCreator) {
				btn_leave.visibility = View.VISIBLE
				break
			} else {
				btn_leave.visibility = View.GONE
			}
		}
		btn_leave.setOnClickListener {

			BinaryDialog(context, R.layout.dialog_event_confirmation).apply {
				title = "Leave ${mTeam.name} ?"
				description =
					"you won't be able to join another team for this event for next 10 minutes"
				rightButtonCallback = {
					val uid = currentUser?.uid
					val userTeammateObject = mTeam.teammates.find { it.id == uid }
					if (userTeammateObject != null) {
						onRemoveTeammateCallback(mTeam, userTeammateObject)
					}
				}
				build()
			}
		}
	}

	private fun GroupieViewHolder.setDeleteTeamCallback(
		userCreator: Boolean
	) {
		if (userCreator) {
			imbtn_remove_team.visibility = View.VISIBLE
			imbtn_remove_team.setOnClickListener {
				BinaryDialog(context, R.layout.dialog_event_confirmation).apply {
					title = "Delete ${mTeam.name} ?"
					description =
						"you won't be able to create another team for this event for next 30 minutes"
					rightButtonCallback = { onDeleteTeamCallback(mTeam) }
					build()
				}
			}
		} else {
			imbtn_remove_team.visibility = View.GONE
		}
	}

	/**
	 * Sets Visibility and action for join team.
	 *
	 * Join button is hidden if User is
	 * 1) Creator
	 * 2) Is a teammate
	 * 3) Team is full
	 */
	private fun GroupieViewHolder.setJoinTeamCallback(
		userCreator: Boolean
	) {
		// Hide join in case team is full
		val isTeamFull = mTeam.teammates.count() == mTeamSize

		if (userCreator || isUserPartOfTeam || isTeamFull) {
			btn_join.visibility = View.GONE
		} else {
			btn_join.visibility = View.VISIBLE
			btn_join.setOnClickListener {
				val passcode = mTeam.passcode
				if (passcode != null) {
					onJoinTeamCallback(mTeam)
				} else {
					Toasty.warning(context, "Invalid Passcode").show()
				}
			}
		}
	}

	private fun initTeammatesRV(
		viewHolder: GroupieViewHolder,
		isRemoveEnabled: Boolean
	) {
		val adapter = GroupAdapter<com.xwray.groupie.GroupieViewHolder>()
		adapter.addAll(getTeammateItems(isRemoveEnabled))
		viewHolder.apply {
			rv_teammates.adapter = adapter
			rv_teammates.layoutManager =
				GridLayoutManager(context, 2, GridLayoutManager.VERTICAL, false)
		}

	}

	private fun getTeammateItems(removeEnabled: Boolean): List<TeammateItem> {
		val listOfItems = mutableListOf<TeammateItem>()
		for (teammate in mTeam.teammates) {
			val creatorsId = mTeam.creator
			val isCreatorsTeammateItem = creatorsId == teammate.id
			if (creatorsId != null) {
				val item = TeammateItem(
					teammate,
					!isCreatorsTeammateItem && removeEnabled,
					onRemoveTeammateCallback
				)
				listOfItems.add(item)
			}
		}
		return listOfItems
	}

	override fun getLayout(): Int {
		return R.layout.item_team
	}

	inner class TeammateItem(
		private val teammate: Teammate,
		private val isRemoveTeammateEnabled: Boolean,
		val onRemoveTeammateCallback: (team: Team, teammate: Teammate) -> Unit
	) : Item() {
		override fun bind(viewHolder: GroupieViewHolder, position: Int) {
			viewHolder.apply {
				val (id, name) = teammate
				if (id != null && name != null) {
					tv_teammate_name.text = name

					if (isRemoveTeammateEnabled) {
						// remove imbtn is hidden by default
						imbtn_remove_teammate.visibility = View.VISIBLE
						imbtn_remove_teammate.setOnClickListener {
							onRemoveTeammateCallback(mTeam, teammate)
						}
					}
				}

			}
		}

		override fun getLayout(): Int {
			return R.layout.item_teammate
		}
	}

}
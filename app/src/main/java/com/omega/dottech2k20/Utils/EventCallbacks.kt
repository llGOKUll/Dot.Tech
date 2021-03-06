package com.omega.dottech2k20.Utils

import android.content.Context
import androidx.navigation.NavController
import com.omega.dottech2k20.Utils.SharedPreferenceUtils.getBackOffTime
import com.omega.dottech2k20.dialogs.*
import com.omega.dottech2k20.models.Event
import com.omega.dottech2k20.models.UserEventViewModel

object EventCallbacks {


	fun joinEvent(
		context: Context,
		event: Event,
		viewModel: UserEventViewModel
	) {
		val id = event.id
		val validBackoff = SharedPreferenceUtils.isValidBackoff(context, id)
		if (validBackoff) {
			BackOffDialog.show(context, getBackOffTime(context, id))
		} else {
			JoinEventConfirmationDialog.show(context, event, viewModel)
		}
	}

	fun leave(context: Context, event: Event, mViewModel: UserEventViewModel) {
		LeaveEventDialog.show(context, event, mViewModel)
	}

	fun authenticationDialog(ctx: Context, navController: NavController): Boolean {
		val currentUser = AuthenticationUtils.currentUser
		when {
			currentUser == null -> {
				RequestForLoginDialog.show(ctx, navController)
				return false
			}
			!currentUser.isEmailVerified -> {
				UnVerifiedEmailDialog.show(ctx)
				return false
			}
		}
		return true
	}

}
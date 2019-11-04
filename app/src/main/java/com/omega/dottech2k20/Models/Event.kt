package com.omega.dottech2k20.Models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class Event(
	var id: String? = null,
	var title: String? = null,
	var thumbnail: String? = null,
	var shortDescription: String? = null,
	var participantCount: Int? = null,
	@ServerTimestamp val startTime: Timestamp? = null,
	@ServerTimestamp val endTime: Timestamp? = null
)


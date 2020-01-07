package com.omega.dottech2k20.dialogs

import android.app.Dialog
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.view.Window
import com.omega.dottech2k20.R
import kotlinx.android.synthetic.main.dialog_submit_text.*

class SingleTextFieldDialog(val context: Context) {

	val layoutId = R.layout.dialog_submit_text
	private val TAG = javaClass.simpleName

	var title = ""
	var name = ""
	var hint = ""
	var onSubmit: (name: String, query: String) -> Unit =
		{ name, query -> Log.d(TAG, "Submit button clicked") }
	var headerIconId = R.drawable.ic_info_outline_white_24dp
	var isNameFieldEnabled = false
	var nameFieldHint = ""
	var minQueryFieldLines = 3

	fun build() {
		val dialog = Dialog(context)
		dialog.setCanceledOnTouchOutside(true)

		dialog.requestWindowFeature(Window.FEATURE_SWIPE_TO_DISMISS)
		dialog.setContentView(layoutId)
		dialog.window.setLayout(
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.WRAP_CONTENT
		)
		dialog.window.setBackgroundDrawableResource(android.R.color.transparent)

		dialog.apply {
			tv_dialog_title.text = title

			et_name.setText(name)
			et_name.isEnabled = isNameFieldEnabled
			et_name.hint = nameFieldHint

			et_query_inputlayout.hint = hint
			et_query.minLines = minQueryFieldLines

			im_dialog_header.setImageResource(headerIconId)
			btn_submit.setOnClickListener {
				onSubmit(et_name.text.toString(), et_query.text.toString())
				dismiss()
			}
		}
		dialog.show()
	}

}
package com.omega.dottech2k20.Fragments


import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.omega.dottech2k20.Adapters.SponsorItem
import com.omega.dottech2k20.Models.Sponsor
import com.omega.dottech2k20.Models.SponsorsViewModel
import com.omega.dottech2k20.R
import com.rd.animation.type.AnimationType
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import kotlinx.android.synthetic.main.fragment_sponsors.*

class SponsorsFragment : Fragment() {

	val TAG = "SponsorsFragment"
	val mAdapter: GroupAdapter<GroupieViewHolder> = GroupAdapter()
	lateinit var mViewModel: SponsorsViewModel
	val linearLayoutManager =
		LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View? {
		mViewModel = ViewModelProviders.of(this).get(SponsorsViewModel::class.java)
		mViewModel.getSponsorsData().observe(this, Observer { sponsors ->
			if (sponsors != null) {
				val sponsorItems = getSponsorItems(sponsors)
				if (mAdapter.itemCount > 0) {
					mAdapter.update(sponsorItems)
				} else {
					mAdapter.addAll(sponsorItems)
				}
				pageIndicatorView.count = mAdapter.itemCount // specify total count of indicators
			}
		})
		return inflater.inflate(R.layout.fragment_sponsors, container, false)
	}

	private fun getSponsorItems(sponsors: List<Sponsor>): List<SponsorItem> {
		val items = mutableListOf<SponsorItem>()
		for (sponsor in sponsors) {
			items.add(SponsorItem(sponsor))
		}
		return items
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		rv_sponsors.layoutManager =
			linearLayoutManager
		rv_sponsors.adapter = mAdapter
		rv_sponsors.isNestedScrollingEnabled = false
		LinearSnapHelper().attachToRecyclerView(rv_sponsors)

		pageIndicatorView.count = 1
		pageIndicatorView.selection = 0
		pageIndicatorView.setAnimationType(AnimationType.DROP)
		pageIndicatorView.radius = 12

		rv_sponsors.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			var position = 0

			override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
				super.onScrollStateChanged(recyclerView, newState)
				if (newState == RecyclerView.SCROLL_STATE_IDLE) {
					var curPosition = linearLayoutManager.findFirstCompletelyVisibleItemPosition()
					Log.d(TAG, "curPosition = $curPosition")
					if (curPosition != position) {
						pageIndicatorView.selection = curPosition
						position = curPosition
					}
				}
			}
		})
	}
}

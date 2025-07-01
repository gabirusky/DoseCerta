package com.example.dosecerta.ui.home

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dosecerta.R
import com.example.dosecerta.data.model.LogStatus
import com.example.dosecerta.databinding.FragmentHomeBinding
import com.example.dosecerta.ui.ViewModelFactory
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    // Use viewModels delegate with the factory
    private val homeViewModel: HomeViewModel by viewModels { ViewModelFactory }
    private lateinit var todayScheduleAdapter: ScheduleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupRecyclerViews()
        setupChart()
        setupButtons()
        observeViewModel()

        return root
    }

    private fun setupRecyclerViews() {
        // Adapter for Today's Schedule
        todayScheduleAdapter = ScheduleAdapter(
            onTakeClick = { item -> homeViewModel.logDose(item, LogStatus.TAKEN) },
            onSkipClick = { item -> homeViewModel.logDose(item, LogStatus.SKIPPED) },
            onUndoSkipClick = { item -> homeViewModel.undoSkip(item) }
        )
        binding.recyclerViewTodaySchedule.adapter = todayScheduleAdapter
        binding.recyclerViewTodaySchedule.layoutManager = LinearLayoutManager(context)
    }

    private fun setupChart() {
        val chart: BarChart = binding.adherenceChart
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setDrawValueAboveBar(true)
        chart.setPinchZoom(false)
        chart.setDrawGridBackground(false)
        chart.isDoubleTapToZoomEnabled = false
        
        chart.axisRight.isEnabled = false // Hide right Y axis
        val leftAxis = chart.axisLeft
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = 100f
        leftAxis.setLabelCount(6, true) // 0, 20, 40, 60, 80, 100
        leftAxis.textColor = ContextCompat.getColor(requireContext(), R.color.gray_600)
        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.toInt()}%"
            }
        }

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = ContextCompat.getColor(requireContext(), R.color.gray_600)
        xAxis.granularity = 1f
    }
    
    private fun setupButtons() {
        binding.buttonAddMedSchedule.setOnClickListener { 
            // Navigate to Add Medication screen
            findNavController().navigate(R.id.action_navigation_home_to_addEditMedFragment)
        }
        binding.buttonSettings.setOnClickListener {
            // Navigate to Language Settings screen
            findNavController().navigate(R.id.action_navigation_home_to_languageSettingsFragment)
        }
    }

    private fun observeViewModel() {

        homeViewModel.todaySchedule.observe(viewLifecycleOwner) { schedule ->
             todayScheduleAdapter.submitList(schedule)
             val isEmpty = schedule.isNullOrEmpty()
             binding.textNoScheduleToday.visibility = if(isEmpty) View.VISIBLE else View.GONE
             binding.buttonAddMedSchedule.visibility = if(isEmpty) View.VISIBLE else View.GONE
             binding.recyclerViewTodaySchedule.visibility = if(isEmpty) View.GONE else View.VISIBLE
        }
        
        homeViewModel.adherenceData.observe(viewLifecycleOwner) {
            data -> updateChart(data)
        }
    }

    private fun updateChart(data: List<AdherenceData>) {
        if (data.isEmpty()) {
             binding.adherenceChart.visibility = View.GONE // Hide chart if no data
             return
        }
        binding.adherenceChart.visibility = View.VISIBLE
        
        val entries = data.mapIndexed { index, adherence ->
            BarEntry(index.toFloat(), adherence.adherencePercentage)
        }

        val dataSet = BarDataSet(entries, "Adherence")
        dataSet.color = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
        dataSet.valueTextColor = ContextCompat.getColor(requireContext(), R.color.black)
        dataSet.valueTextSize = 10f
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value > 0) "${value.toInt()}%" else "" // Don't show 0%
            }
        }

        val barData = BarData(dataSet)
        barData.barWidth = 0.6f // Adjust bar width

        binding.adherenceChart.xAxis.valueFormatter = IndexAxisValueFormatter(data.map { it.dayLabel })
        binding.adherenceChart.data = barData
        binding.adherenceChart.invalidate() // Refresh chart
        binding.adherenceChart.animateY(500) // Add animation
    }

    override fun onResume() {
        super.onResume()
        // Refresh data every time the fragment becomes visible
        homeViewModel.loadHomePageData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerViewTodaySchedule.adapter = null
        _binding = null // Clean up binding
    }

    // TODO: Add functions to setup UI components like the chart
    // private fun setupAdherenceChart(chart: BarChart) { ... }
} 
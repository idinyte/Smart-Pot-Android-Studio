package com.example.smartpot

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartpot.databinding.FragmentSecondBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment(), OnRecipeClickListener, BluetoothListener {

    private var _binding: FragmentSecondBinding? = null
    private var layoutManager: RecyclerView.LayoutManager? = null
    private var adapter: RecyclerView.Adapter<RecyclerAdapter.ViewHolder>? = null
    private var expandedPosition = -1
    private lateinit var recipes: ArrayList<RecipeFormData>
    private lateinit var recipeProgress: ProgressBar
    private lateinit var mainActivity: MainActivity
    private lateinit var thermometerImageView: ImageView
    private lateinit var currentRecipeName: String
    private lateinit var pauseButton: Button
    private lateinit var expandableLayout: RelativeLayout
    var TRUE = "TRUE"
    var FALSE = "FALSE"
    val ERROR = "01"
    val POWER_STATUS_CHANGE = "02"
    val PLUS = "03"
    val MINUS = "04"
    val STIRRING = "05"
    var RECIPE_TEMPERATURE = "06"
    var RECIPE_DURATION = "07"
    var RECIPE_STARTS_IMMEDIATELY = "08"
    var RECIPE_POWER = "09"
    var RECIPE_PAUSE = "10"
    var RECIPE_STIRRING = "11"
    var RECIPE_RESET = "12"
    var RECIPE_SENT = "13"
    var TEMPERATURE_REACHED = "14"
    var UPDATE_PROGRESS_BAR = "15"
    var FINISH_COOKING = "16"
    var STOP_RECIPE = "17"
    var BLUETOOTH_DISCONNECT = "18"
    val ERROR_CODE_STACK_OVERFLOW = "01"
    val ERROR_CORRUPTED_COMMAND = "02"
    var POLLED_TEMPERATURE = "PT"
    var POLLED_STIRRING = "PS"
    var POLLED_POWER = "PP"
    var waitingForBluetoothResponse: Boolean = false
    var receivedResponse: String = ""


    private val binding get() = _binding!!

    companion object{
        lateinit var bluetoothListener: BluetoothListener
    }
    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainActivity = requireActivity() as MainActivity
        bluetoothListener = this

        layoutManager = LinearLayoutManager(MainActivity())
        binding.recyclerViewRecipes.layoutManager = layoutManager

        binding.buttonManualControls.text = resources.getText(R.string.manual)
        binding.recipesRelativeLayout.visibility = View.VISIBLE
        binding.manualControlsConstrainLayout.visibility = View.GONE

        recipes = RecipesToFile.loadRecipesFromFile(requireContext())
        adapter = RecyclerAdapter(requireContext(), recipes, this)
        binding.recyclerViewRecipes.adapter = adapter

        binding.recyclerViewRecipes.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // The RecyclerView has been laid out and populated with children.
                if((activity as MainActivity).bluetoothService!!.automaticCooking)
                {
                    binding.statusState.text = getString(R.string.on)
                    setLayoutToAutomaticRecipe((activity as MainActivity).bluetoothService!!.recyclerViewPos)
                    Log.d("MyLog", "Does timer starts immediately = ${(activity as MainActivity).bluetoothService!!.temperatureReached}")
                    if((activity as MainActivity).bluetoothService!!.temperatureReached || (activity as MainActivity).bluetoothService!!.timerStartsImmediately)
                    {
                        temperatureReached()
                    }
                    if((activity as MainActivity).bluetoothService!!.isPaused)
                    {
                        pauseButton = expandableLayout.findViewById<Button>(R.id.recipeRightButton)
                        pauseRecipe(TRUE)
                    }
                }
                else if((activity as MainActivity).bluetoothService!!.manualCooking)
                {
                    binding.statusState.text = resources.getText(R.string.on)
                    binding.buttonManualControls.text = resources.getText(R.string.recipes)
                    binding.recipesRelativeLayout.visibility = View.GONE
                    binding.manualControlsConstrainLayout.visibility = View.VISIBLE
                    setManualCooking(TRUE)
                }
                binding.recyclerViewRecipes.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        infoPanelDefaults()

        binding.buttonDisconnect.setOnClickListener {
            if(waitingForBluetoothResponse) {
                Toast.makeText(context, "Command sent, waiting for response", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            waitingForBluetoothResponse = true
            sendDataToBluetooth("$BLUETOOTH_DISCONNECT")
        }

        binding.addRecipe.setOnClickListener{
            findNavController().navigate(R.id.action_SecondFragment_to_newRecipeFragment)
        }

        binding.buttonManualControls.setOnClickListener {
            if(binding.buttonManualControls.text == resources.getText(R.string.manual))
            {
                if(binding.statusState.text == getString(R.string.off))
                {
                    binding.buttonManualControls.text = resources.getText(R.string.recipes)
                    binding.recipesRelativeLayout.visibility = View.GONE
                    binding.manualControlsConstrainLayout.visibility = View.VISIBLE
                }
                else
                {
                    Toast.makeText(context, "Can't enter manual control mode while recipe is in progress", Toast.LENGTH_LONG).show()
                }

            }
            else if(binding.buttonManualControls.text == resources.getText(R.string.recipes))
            {
                if(binding.statusState.text == getString(R.string.off)) {
                    binding.buttonManualControls.text = resources.getText(R.string.manual)
                    binding.recipesRelativeLayout.visibility = View.VISIBLE
                    binding.manualControlsConstrainLayout.visibility = View.GONE
                }
                else
                {
                    Toast.makeText(context, "Can't enter recipe mode while cooker is on", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.manualPower.setOnClickListener {
            if(waitingForBluetoothResponse) {
                Toast.makeText(context, "Command sent, waiting for response", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            waitingForBluetoothResponse = true
            sendDataToBluetooth("$POWER_STATUS_CHANGE")
        }

        binding.manualStirring.setOnClickListener {
            if(waitingForBluetoothResponse) {
                Toast.makeText(context, "Command sent, waiting for response", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            waitingForBluetoothResponse = true
            sendDataToBluetooth("$STIRRING")
        }

        binding.manualPlus.setOnClickListener {
            if(waitingForBluetoothResponse) {
                Toast.makeText(context, "Command sent, waiting for response", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            waitingForBluetoothResponse = true
            sendDataToBluetooth("$PLUS")
        }

        binding.manualMinus.setOnClickListener {
            if(waitingForBluetoothResponse) {
                Toast.makeText(context, "Command sent, waiting for response", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            waitingForBluetoothResponse = true
            sendDataToBluetooth("$MINUS")
        }
    }

    private fun infoPanelDefaults() {
        binding.statusState.text = getString(R.string.off)
        binding.motorState.text = "-"
        binding.temperatureState.text = "-"
        binding.powerState.text = "-"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onRecipeRootClick(position: Int) {
        val rootView = binding.recyclerViewRecipes.findViewHolderForLayoutPosition(position)!!.itemView
        val expandableLayout = rootView.findViewById<RelativeLayout>(R.id.expandableRecipeInfoPanel)
        if (expandableLayout.visibility == View.VISIBLE) {
            expandableLayout.visibility = View.GONE
            expandedPosition = -1
        }
        else {
            if(expandedPosition != -1)
            {
                val prevRootView = binding.recyclerViewRecipes.getChildAt(expandedPosition)
                val prevExpandableLayout = prevRootView.findViewById<RelativeLayout>(R.id.expandableRecipeInfoPanel)
                prevExpandableLayout.visibility = View.GONE
            }
            expandableLayout.visibility = View.VISIBLE
            expandedPosition = position
        }
    }

    override suspend fun processRecipePowerClick(recipe: RecipeFormData, position: Int) {
        if(binding.statusState.text == getString(R.string.off))
        {
            if(!sendRecipeToArduino(recipe))
            {
                return
            }
            (activity as MainActivity).bluetoothService!!.setAutomaticRecipeVars(recipe.imageResource, position, recipe.name!!, recipe.timerStartsImmediately)
            (activity as MainActivity).bluetoothService!!.desiredTemperature = recipe.temperature
            withContext(Dispatchers.Main) {
                setLayoutToAutomaticRecipe(position)
            }
        }
    }

    fun setLayoutToAutomaticRecipe(position: Int)
    {
        binding.statusState.text = getString(R.string.on)
        val pressedView = binding.recyclerViewRecipes.findViewHolderForLayoutPosition(position)?.itemView as CardView
        currentRecipeName = pressedView.findViewById<TextView>(R.id.recipeName).text.toString()

        val copiedCardView = LayoutInflater.from(context).inflate(R.layout.card_recipe, null) as CardView
        copiedCardView.findViewById<ImageView>(R.id.recipeImage).setImageDrawable(pressedView.findViewById<ImageView>(R.id.recipeImage).drawable)
        copiedCardView.findViewById<TextView>(R.id.recipeName).text = pressedView.findViewById<TextView>(R.id.recipeName).text
        copiedCardView.findViewById<Button>(R.id.recipePowerOnButton).visibility = View.GONE

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(12, 6, 12, 6)
        copiedCardView.layoutParams = params

        expandableLayout = copiedCardView.findViewById(R.id.expandableRecipeInfoPanel)
        expandableLayout.visibility = View.VISIBLE

        if(!pressedView.findViewById<TextView>(R.id.recipeNotes).text.isNullOrEmpty())
            expandableLayout.findViewById<TextView>(R.id.recipeNotes).text =  pressedView.findViewById<TextView>(R.id.recipeNotes).text
        else
        {
            var params = expandableLayout.findViewById<TextView>(R.id.recipeNotes).layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = 0
            params.height = 0
            expandableLayout.findViewById<TextView>(R.id.recipeNotes).layoutParams = params
        }

        expandableLayout.findViewById<TextView>(R.id.recipeInfoDuration).text = pressedView.findViewById<TextView>(R.id.recipeInfoDuration).text
        expandableLayout.findViewById<TextView>(R.id.recipeInfoTemperature).text = pressedView.findViewById<TextView>(R.id.recipeInfoTemperature).text
        expandableLayout.findViewById<TextView>(R.id.recipeInfoStirring).text = pressedView.findViewById<TextView>(R.id.recipeInfoStirring).text
        expandableLayout.findViewById<TextView>(R.id.recipeInfoPower).text = pressedView.findViewById<TextView>(R.id.recipeInfoPower).text
        expandableLayout.findViewById<TextView>(R.id.recipeInfoTimerStart).text = pressedView.findViewById<TextView>(R.id.recipeInfoTimerStart).text

        val leftButton: Button = expandableLayout.findViewById<Button>(R.id.recipeLeftButton)
        leftButton.text = getString(R.string.stop)
        leftButton.setOnClickListener { stopButton() }
        val rightButton: Button = expandableLayout.findViewById<Button>(R.id.recipeRightButton)
        rightButton.text = getString(R.string.pause)
        rightButton.setOnClickListener { pauseResumeButton(it as Button) }

        recipeProgress = copiedCardView.findViewById(R.id.recipeProgress)
        recipeProgress.visibility = View.GONE
        recipeProgress.isIndeterminate = false
        recipeProgress.progress = 0

        thermometerImageView = copiedCardView.findViewById(R.id.thermometerImageView)
        thermometerImageView.visibility = View.VISIBLE

        binding.copyToLinearLayout.visibility = View.VISIBLE
        binding.copyToLinearLayout.addView(copiedCardView)
        binding.recyclerViewRecipes.visibility = View.GONE
    }

    private suspend fun sendResetRecipeToArduino(): Boolean = suspendCoroutine { continuation ->
        waitingForBluetoothResponse = true
        sendDataToBluetooth("$RECIPE_RESET")

        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (!waitingForBluetoothResponse) {
                    timer.cancel()
                    if (receivedResponse == TRUE)
                    {
                        continuation.resume(true)
                    } else {
                        continuation.resume(false)
                    }
                }
            }
        }, 0, 100)
    }

    private suspend fun sendDurationToArduino(boilingDurationSeconds: Int): Boolean = suspendCoroutine { continuation ->
        waitingForBluetoothResponse = true
        sendDataToBluetooth("$RECIPE_DURATION$boilingDurationSeconds")
        //mainActivity.sendData(RECIPE_DURATION + boilingDurationSeconds.toString(), mainActivity.outputStream)

        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (!waitingForBluetoothResponse) {
                    timer.cancel()
                    val responseInt = receivedResponse.toIntOrNull()
                    if (responseInt == boilingDurationSeconds) {
                        continuation.resume(true)
                    } else {
                        continuation.resume(false)
                    }
                }
            }
        }, 0, 100)
    }

    private suspend fun sendTemperatureToArduino(boilingTemperature: Int): Boolean = suspendCoroutine { continuation ->
        waitingForBluetoothResponse = true
        sendDataToBluetooth("$RECIPE_TEMPERATURE$boilingTemperature")
        //mainActivity.sendData(RECIPE_TEMPERATURE + boilingTemperature.toString(), mainActivity.outputStream)

        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (!waitingForBluetoothResponse) {
                    timer.cancel()
                    val responseInt = receivedResponse.toIntOrNull()
                    if (responseInt == boilingTemperature) {
                        continuation.resume(true)
                    } else {
                        continuation.resume(false)
                    }
                }
            }
        }, 0, 100)
    }

    private suspend fun sendPowerToArduino(boilingPower: Int): Boolean = suspendCoroutine { continuation ->
        waitingForBluetoothResponse = true
        sendDataToBluetooth("$RECIPE_POWER$boilingPower")

        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (!waitingForBluetoothResponse) {
                    timer.cancel()
                    val responseInt = receivedResponse.toIntOrNull()
                    if (responseInt == boilingPower) {
                        continuation.resume(true)
                    } else {
                        continuation.resume(false)
                    }
                }
            }
        }, 0, 100)
    }

    private suspend fun sendTimerStartsToArduino(timerStartsImmediately: Boolean): Boolean = suspendCoroutine { continuation ->
        waitingForBluetoothResponse = true
        if(timerStartsImmediately)
            sendDataToBluetooth("$RECIPE_STARTS_IMMEDIATELY$TRUE")
        else
            sendDataToBluetooth("$RECIPE_STARTS_IMMEDIATELY$FALSE")

        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (!waitingForBluetoothResponse) {
                    timer.cancel()
                    if ((receivedResponse == TRUE && timerStartsImmediately) ||
                        (receivedResponse == FALSE && !timerStartsImmediately)) {
                        continuation.resume(true)
                    } else {
                        continuation.resume(false)
                    }
                }
            }
        }, 0, 100)
    }

    private suspend fun sendStirrerSettingToArduino(stirrerSetting: Array<Int?>): Boolean = suspendCoroutine { continuation ->
        waitingForBluetoothResponse = true
        val dutyCycle = stirrerSetting[0]
        val period = stirrerSetting[1]
        val duration = stirrerSetting[2]
        sendDataToBluetooth("$RECIPE_STIRRING$dutyCycle $period $duration")


        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (!waitingForBluetoothResponse) {
                    timer.cancel()
                    if (receivedResponse == "$dutyCycle $period $duration")
                    {
                        continuation.resume(true)
                    } else {
                        continuation.resume(false)
                    }
                }
            }
        }, 0, 100)
    }

    private suspend fun sendSuccessToArduino(): Boolean = suspendCoroutine { continuation ->
        waitingForBluetoothResponse = true
        sendDataToBluetooth("$RECIPE_SENT")

        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (!waitingForBluetoothResponse) {
                    timer.cancel()
                    if (receivedResponse == TRUE)
                    {
                        continuation.resume(true)
                    } else {
                        continuation.resume(false)
                    }
                }
            }
        }, 0, 100)
    }

    private suspend fun sendRecipeToArduino(recipe: RecipeFormData): Boolean = coroutineScope {
        val resetSentSuccessfully = sendResetRecipeToArduino()
        if (!resetSentSuccessfully) {
            return@coroutineScope false
        }

        val durationSentSuccessfully = sendDurationToArduino((recipe.boilingDurationMin!! * 60) + recipe.boilingDurationSec!!)
        if (!durationSentSuccessfully) {
            return@coroutineScope false
        }

        val temperatureSentSuccessfully = sendTemperatureToArduino(recipe.temperature)
        if (!temperatureSentSuccessfully) {
            return@coroutineScope false
        }

        val timerStartsSentSuccessfully = sendTimerStartsToArduino(recipe.timerStartsImmediately)
        if (!timerStartsSentSuccessfully) {
            return@coroutineScope false
        }

        val powerSentSuccessfully = sendPowerToArduino(recipe.powerDuringHeatup)
        if (!powerSentSuccessfully) {
            return@coroutineScope false
        }

        for(i in 0 until (recipe.motorSettings?.size ?: 0))
        {
            var settingSentSuccessfully = sendStirrerSettingToArduino(recipe.motorSettings!![i])
            if (!settingSentSuccessfully) {
                return@coroutineScope false
            }
        }

        val successSentSuccessfully = sendSuccessToArduino()
        if(!successSentSuccessfully)
        {
            return@coroutineScope false
        }
        return@coroutineScope true
    }

    private fun stopButton() {
        if(waitingForBluetoothResponse) {
            Toast.makeText(context, "Command sent, waiting for response", Toast.LENGTH_LONG).show()
            return
        }

        waitingForBluetoothResponse = true

        sendDataToBluetooth("$STOP_RECIPE")
    }

    private fun pauseResumeButton(button: Button) {
        if(waitingForBluetoothResponse) {
            Toast.makeText(context, "Command sent, waiting for response", Toast.LENGTH_LONG).show()
            return
        }

        waitingForBluetoothResponse = true
        pauseButton = button
        if(button.text == getString(R.string.pause))
        {
            sendDataToBluetooth("$RECIPE_PAUSE$TRUE")
        }
        else
        {
            sendDataToBluetooth("$RECIPE_PAUSE$FALSE")
        }
    }

    private fun completeDisconnect()
    {
        finishCooking()
        (activity as MainActivity).stopMyService()
        findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
    }

    override fun onRecipeLeftButtonClick(recipe: RecipeFormData, position: Int) {
        val rootView = binding.recyclerViewRecipes.findViewHolderForLayoutPosition(position)!!.itemView
        val button = rootView.findViewById<Button>(R.id.recipeLeftButton)
            if(button.text == requireContext().getString(R.string.delete))
            {
                recipes.removeAt(position)
                expandedPosition = -1
                adapter!!.notifyItemRemoved(position)
                RecipesToFile.saveRecipesToFile(requireContext(), recipes)
            }
    }

    override fun onRecipeRightButtonClick(recipe: RecipeFormData, position: Int) {
        val rootView = binding.recyclerViewRecipes.findViewHolderForLayoutPosition(position)!!.itemView
        val button = rootView.findViewById<Button>(R.id.recipeRightButton)
        if(button.text == context?.getString(R.string.edit))
        {
            val bundle = Bundle()
            bundle.putString("editButton", position.toString())
            findNavController().navigate(R.id.action_SecondFragment_to_newRecipeFragment, bundle)
        }
    }

    override fun onBluetoothReceived(command: String, parameters: String) {
        if(!this.isVisible)
            return
        when(command)
        {
            ERROR -> handleError(parameters)
            POWER_STATUS_CHANGE ->  setManualCooking(parameters)
            PLUS -> setPowerState(parameters)
            MINUS -> setPowerState(parameters)
            STIRRING -> setStirringState(parameters)
            POLLED_TEMPERATURE -> binding.temperatureState.text = "$parameters °C"
            POLLED_STIRRING -> setStirringState(parameters)
            POLLED_POWER -> setPowerState(parameters)
            RECIPE_DURATION -> receivedResponse = parameters
            RECIPE_TEMPERATURE -> receivedResponse = parameters
            RECIPE_POWER -> receivedResponse = parameters
            RECIPE_STARTS_IMMEDIATELY -> receivedResponse = parameters
            RECIPE_RESET -> receivedResponse = parameters
            RECIPE_STIRRING -> receivedResponse = parameters
            RECIPE_SENT -> receivedResponse = parameters
            TEMPERATURE_REACHED -> temperatureReached()
            UPDATE_PROGRESS_BAR -> updateProgressBar(parameters)
            FINISH_COOKING -> finishCooking()
            RECIPE_PAUSE -> pauseRecipe(parameters)
            STOP_RECIPE -> stopRecipe()
            BLUETOOTH_DISCONNECT -> completeDisconnect()

        }
        waitingForBluetoothResponse = false
    }

    private fun setManualCooking(parameters: String) {
        if (parameters == TRUE)
        {
            binding.statusState.text = getString(R.string.on)
            (activity as MainActivity).bluetoothService!!.manualCooking = true
            (activity as MainActivity).bluetoothService!!.updateNotificationToManualCooking()
        }
        else if (parameters == FALSE)
        {
            (activity as MainActivity).bluetoothService!!.manualCooking = false
            (activity as MainActivity).bluetoothService!!.updateToDefaultNotification()
            binding.statusState.text = getString(R.string.off)
            binding.temperatureState.text = "-"
            binding.motorState.text = "-"
            binding.powerState.text = "-"
        }
    }

    private fun stopRecipe() {
        infoPanelDefaults()
        binding.copyToLinearLayout.removeAllViews()
        binding.copyToLinearLayout.visibility = View.GONE
        binding.recyclerViewRecipes.visibility = View.VISIBLE
    }

    private fun pauseRecipe(parameters: String) {
        if(parameters == TRUE){
            binding.statusState.text = getString(R.string.paused)
            pauseButton.text = getString(R.string.resume)
        }
        else if(parameters == FALSE)
        {
            binding.statusState.text = getString(R.string.on)
            pauseButton.text = getString(R.string.pause)
        }
    }

    private fun finishCooking() {
        binding.copyToLinearLayout.removeAllViews()
        binding.copyToLinearLayout.visibility = View.GONE
        binding.recyclerViewRecipes.visibility = View.VISIBLE
        binding.statusState.text = getString(R.string.off)
        binding.motorState.text = "-"
        binding.powerState.text = "-"
        binding.temperatureState.text = "-"
    }

    private fun updateProgressBar(progress: String) {
        if(!::recipeProgress.isInitialized)
            return

        val progressInt = progress.toIntOrNull()
        if(progressInt != null)
        {
            recipeProgress.progress = progressInt
        }
    }

    private fun temperatureReached() {
        thermometerImageView.visibility = View.GONE
        recipeProgress.visibility = View.VISIBLE
    }

    private fun setPowerState(power: String)
    {
        binding.powerState.text = "$power W"
    }

    private fun setStirringState(state: String)
    {
        if (state == "0")
            binding.motorState.text = getString(R.string.motor_state_idle)
        else
            binding.motorState.text = getString(R.string.on)
    }

    private fun handleError(errorCode: String)
    {
        when(errorCode)
        {
            ERROR_CODE_STACK_OVERFLOW -> Toast.makeText(context, "Too many inputs, please wait", Toast.LENGTH_LONG).show()
            ERROR_CORRUPTED_COMMAND -> Toast.makeText(context, "Failed to send command", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendDataToBluetooth(message: String) {
        (activity as MainActivity).sendData(message)
    }
}
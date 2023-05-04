package com.example.smartpot

import android.R
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowId
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.iterator
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.smartpot.databinding.FragmentNewRecipeBinding
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import java.io.File


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [NewRecipeFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class NewRecipeFragment : Fragment() {
    private var _binding: FragmentNewRecipeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var temperatureSlider: SeekBar
    private lateinit var inputContainer: LinearLayout
    private lateinit var deleteButton: Button
    private lateinit var addButton: Button
    private lateinit var formData: RecipeFormData
    private lateinit var createdView: View
    private lateinit var recipes: ArrayList<RecipeFormData>
    private lateinit var selectedBorder: Drawable
    private var editRecipePos = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentNewRecipeBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupToolbar()
        selectedBorder = ContextCompat.getDrawable(requireContext(), com.example.smartpot.R.drawable.border_selected)!!
        createdView = view
        recipes = RecipesToFile.loadRecipesFromFile(requireContext())
        if(arguments != null)
        {
            val editPosString = arguments!!.getString("editButton")
            val editPos = editPosString?.toIntOrNull()
            if (editPos != null) {
                editRecipePos = editPos
                formData = recipes[editPos]
            }
        }
        else
        {
            formData = RecipeFormData()
            formData.motorSettings = ArrayList()
        }
        hideInputOnEnter(binding.nameInputLayout)
        hideInputOnEnter(binding.notesInputLayout)
        hideInputOnEnter(binding.boilingDurationMin)
        hideInputOnEnter(binding.boilingDurationSec)
        setupImageField()
        if(formData.selectedImageId <= 0)
            binding.imageView17.performClick()
        else
            binding.imageTableLayout.findViewById<ImageView>(formData.selectedImageId).performClick()
        setupNameInputField()
        setupNotesInputField()
        setupBoilingDurationInputField()
        setupTemperatureSlider()
        setupTimerStartsDropdown()
        setupPowerDuringHeatup()
        inputContainer = binding.motorInputContainer
        deleteButton = binding.DeleteStirrerSettingsButton
        addButton = binding.AddStirrerSettingsButton
        setupAddStirrerSettings()
        setupRemoveStirrerSetting()
        fillRecipeData()
        super.onViewCreated(view, savedInstanceState)

    }

    private fun setupImageField() {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(R.attr.colorAccent, typedValue, true)
        val primaryColor = typedValue.data

        for(view in binding.imageTableLayout)
        {
            if (view is TableRow)
            {
                for(v in view)
                {
                    if (v is ImageView)
                    {
                        v.setOnClickListener {
                                if(formData.selectedImageId > 0)
                                {0
                                    binding.imageTableLayout.findViewById<ImageView>(formData.selectedImageId).background = null
                                }

                                v.background = selectedBorder
                                formData.selectedImageId = v.id
                                formData.imageResource = requireContext().resources.getIdentifier(v.tag as String, "drawable", requireContext().packageName)
                        }
                    }
                }
            }
        }
    }

    private fun fillRecipeData()
    {
        if(editRecipePos == -1)
            return

        binding.includeToolbar.toolbarText.text = "Edit recipe"
        binding.nameInputLayout.editText?.setText(formData.name.toString())
        if(formData.notes != null)
            binding.notesInputLayout.editText?.setText(formData.notes.toString())
        binding.boilingDurationMin.editText?.setText(formData.boilingDurationMin.toString())
        binding.boilingDurationSec.editText?.setText(formData.boilingDurationSec.toString())
        when (formData.powerDuringHeatup) {
            200 -> binding.powerDuringHeatupButtonGroup.onClick(binding.radioButtonPower200)
            500 -> binding.powerDuringHeatupButtonGroup.onClick(binding.radioButtonPower500)
            800 -> binding.powerDuringHeatupButtonGroup.onClick(binding.radioButtonPower800)
            1000 -> binding.powerDuringHeatupButtonGroup.onClick(binding.radioButtonPower1000)
            1600 -> binding.powerDuringHeatupButtonGroup.onClick(binding.radioButtonPower1600)
            1800 -> binding.powerDuringHeatupButtonGroup.onClick(binding.radioButtonPower1800)
            2000 -> binding.powerDuringHeatupButtonGroup.onClick(binding.radioButtonPower2000)
            else -> binding.powerDuringHeatupButtonGroup.onClick(binding.radioButtonPower1300)
        }
        if(formData.timerStartsImmediately)
            binding.dropdownTimerStarts.setSelection(1)

        binding.temperatureSlider.progress = formData.temperature - 50
        binding.temperatureSliderTextView.text = formData.temperature.toString() + " °C"

        for (i in 0 until (formData.motorSettings?.size ?: 0))
        {
            addStirrerSetting(setup = true)
        }

        for(i in 0 until inputContainer.childCount)
        {
            var cardView: View = inputContainer.getChildAt(i)
            var relativeLayout =
                cardView.findViewById<ConstraintLayout>(com.example.smartpot.R.id.recipeRoot)
            for (j in 0 until relativeLayout.childCount) {
                val view = relativeLayout.getChildAt(j)
                if (view is TextInputLayout) {
                    view.editText?.setText(formData.motorSettings?.get(i)?.get(j).toString())
                }
            }
        }
    }

    private fun setupNotesInputField() {
        binding.notesInputLayout.editText?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                formData.notes = binding.notesInputLayout.editText?.text.toString()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupPowerDuringHeatup() {
        binding.radioButtonPower200.setOnClickListener{
            binding.powerDuringHeatupButtonGroup.onClick(binding.radioButtonPower200)
            setPowerDuringHeatup()
        }
        binding.radioButtonPower500.setOnClickListener{
            binding.powerDuringHeatupButtonGroup.onClick(binding.radioButtonPower500)
            setPowerDuringHeatup()
        }
        binding.radioButtonPower800.setOnClickListener{
            binding.powerDuringHeatupButtonGroup.onClick(binding.radioButtonPower800)
            setPowerDuringHeatup()
        }
        binding.radioButtonPower1000.setOnClickListener{
            binding.powerDuringHeatupButtonGroup.onClick(binding.radioButtonPower1000)
            setPowerDuringHeatup()
        }
        binding.radioButtonPower1300.setOnClickListener{
            binding.powerDuringHeatupButtonGroup.onClick(binding.radioButtonPower1300)
            setPowerDuringHeatup()
        }
        binding.radioButtonPower1600.setOnClickListener{
            binding.powerDuringHeatupButtonGroup.onClick(binding.radioButtonPower1600)
            setPowerDuringHeatup()
        }
        binding.radioButtonPower1800.setOnClickListener{
            binding.powerDuringHeatupButtonGroup.onClick(binding.radioButtonPower1800)
            setPowerDuringHeatup()
        }
        binding.radioButtonPower2000.setOnClickListener{
            binding.powerDuringHeatupButtonGroup.onClick(binding.radioButtonPower2000)
            setPowerDuringHeatup()
        }
    }

    private fun setPowerDuringHeatup()
    {
        when (binding.powerDuringHeatupButtonGroup.checkedRadioButtonId) {
            binding.radioButtonPower200.id -> formData.powerDuringHeatup = 200
            binding.radioButtonPower500.id -> formData.powerDuringHeatup = 500
            binding.radioButtonPower800.id -> formData.powerDuringHeatup = 800
            binding.radioButtonPower1000.id -> formData.powerDuringHeatup = 1000
            binding.radioButtonPower1600.id -> formData.powerDuringHeatup = 1600
            binding.radioButtonPower1800.id -> formData.powerDuringHeatup = 1800
            binding.radioButtonPower2000.id -> formData.powerDuringHeatup = 2000
            else -> formData.powerDuringHeatup = 1300
        }
    }

    private fun setupBoilingDurationInputField() {
        binding.boilingDurationMin.editText?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val text = binding.boilingDurationMin.editText?.text.toString()
                var intValue = text.toIntOrNull()?.let { intValue ->
                    intValue.coerceIn(0, 180)
                }
                if (intValue != null) {
                    formData.boilingDurationMin = intValue
                    binding.boilingDurationMin.editText?.removeTextChangedListener(this)
                    binding.boilingDurationMin.editText?.setText(intValue.toString())
                    binding.boilingDurationMin.editText?.text?.let { binding.boilingDurationMin.editText?.setSelection(it.length) }
                    binding.boilingDurationMin.editText?.addTextChangedListener(this)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        binding.boilingDurationSec.editText?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val text = binding.boilingDurationSec.editText?.text.toString()
                var intValue = text.toIntOrNull()?.let { intValue ->
                    intValue.coerceIn(0, 59)
                }
                if (intValue != null) {
                    formData.boilingDurationSec = intValue
                    binding.boilingDurationSec.editText?.removeTextChangedListener(this)
                    binding.boilingDurationSec.editText?.setText(intValue.toString())
                    binding.boilingDurationSec.editText?.text?.let {
                        val selectionIndex = it.length.coerceAtMost(binding.boilingDurationSec.editText?.length() ?: 0)
                        binding.boilingDurationSec.editText?.setSelection(selectionIndex)
                    }
                    binding.boilingDurationSec.editText?.addTextChangedListener(this)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupNameInputField() {
        binding.nameInputLayout.editText?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                formData.name = binding.nameInputLayout.editText?.text.toString()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupRemoveStirrerSetting() {
        deleteButton.isEnabled = inputContainer.childCount > 0
        deleteButton.setOnClickListener {
            inputContainer.removeViewAt(inputContainer.childCount - 1)
            deleteButton.isEnabled = inputContainer.childCount > 0
            formData.motorSettings?.removeLast()
        }
    }

    private fun setupAddStirrerSettings() {
        addButton.setOnClickListener {
            if (lastStirrerSettingFieldsAreNotFilled()) {
                Toast.makeText(context, "Fill previous stirrer settings first", Toast.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }

            if ((formData.motorSettings?.size ?: 0) >= 10) {
                Toast.makeText(context, "Limit reached", Toast.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }

            addStirrerSetting()
        }
    }

    private fun addStirrerSetting(setup : Boolean = false) {
        val inflater = LayoutInflater.from(context)
        inputContainer.addView(
            inflater.inflate(
                com.example.smartpot.R.layout.card_stirrer_setting,
                null,
                false
            )
        )
        deleteButton.isEnabled = inputContainer.childCount > 0

        if(!setup)
            formData.motorSettings?.add(arrayOfNulls<Int?>(3))
        var cardIndex = inputContainer.childCount - 1
        val cardView: View = inputContainer.getChildAt(cardIndex)
        val relativeLayout =
            cardView.findViewById<ConstraintLayout>(com.example.smartpot.R.id.recipeRoot)

        // loop through all the TextInputLayouts and set event listeners
        for (i in 0 until relativeLayout.childCount) {
            val view = relativeLayout.getChildAt(i)
            if (view is TextInputLayout) {
                view.editText?.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        if(i == 1 || i == 2)
                        {
                            val originalValue = view.editText?.text.toString().toIntOrNull()
                            if(originalValue != null)
                            {
                                val coercedValue = originalValue!!.coerceIn(10, 10080)
                                view.editText?.setText(coercedValue.toString())
                            }
                        }
                    }
                }
                view.editText?.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        val text = s.toString()
                        var intValue = text.toIntOrNull()
                        if (intValue != null) {
                            if (i == 0) {
                                intValue = intValue.coerceIn(0, 100)
                            } else if (i == 1 || i == 2) {
                                intValue = intValue.coerceIn(0, 10800)
                            }
                            formData.motorSettings!![cardIndex][i] = intValue
                            view.editText?.removeTextChangedListener(this)
                            view.editText?.setText(intValue.toString())
                            view.editText?.text?.let {
                                val selectionIndex = it.length.coerceAtMost(view.editText?.length() ?: 0)
                                view.editText?.setSelection(selectionIndex)
                            }
                            view.editText?.addTextChangedListener(this)
                        } else {
                            view.editText?.removeTextChangedListener(this)
                            view.editText?.setText("")
                            view.editText?.text?.let {
                                val selectionIndex = it.length.coerceAtMost(view.editText?.length() ?: 0)
                                view.editText?.setSelection(selectionIndex)
                            }
                            view.editText?.text?.let { view.editText?.setSelection(it.length) }
                            view.editText?.addTextChangedListener(this)
                            formData.motorSettings?.get(cardIndex)?.set(i, null)
                        }
                    }

                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                    }
                })
                hideInputOnEnter(view)
            }
        }

    }

    private fun lastStirrerSettingFieldsAreNotFilled(): Boolean {
        if (formData.motorSettings?.isNullOrEmpty() == true || formData.motorSettings?.size == null)
            return false

        val lastSettings = formData.motorSettings!!.last()

        for (value in lastSettings) {
            if (value == null)
                return true
        }
        return false
    }

    private fun hideInputOnEnter(input: TextInputLayout) {
        input.editText?.setOnEditorActionListener { _, actionId, _ ->
            var handled = false
            if (actionId == 0 || actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                input.clearFocus()
                val imm =
                    activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(createdView.windowToken, 0)
                handled = true
            }
            handled
        }
    }

    private fun setupTimerStartsDropdown() {
        val timerStarts: Spinner = binding.dropdownTimerStarts
        val adapter = context?.let {
            ArrayAdapter.createFromResource(
                it,
                com.example.smartpot.R.array.time_starts_array,
                R.layout.simple_spinner_item
            )
        }
        adapter!!.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
        timerStarts.adapter = adapter
        timerStarts.setSelection(0)
        timerStarts.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                formData.timerStartsImmediately = position == 1
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupToolbar() {
        binding.includeToolbar.toolbarBackButton.setOnClickListener {
            findNavController().navigate(com.example.smartpot.R.id.action_newRecipeFragment_to_SecondFragment)
        }
        binding.includeToolbar.toolbarDoneButton.setOnClickListener {
            if(recipeIsFilled())
            {
                if(editRecipePos == -1)
                    recipes.add(formData)
                else
                    recipes[editRecipePos] = formData
                RecipesToFile.saveRecipesToFile(requireContext(), recipes)
                findNavController().navigate(com.example.smartpot.R.id.action_newRecipeFragment_to_SecondFragment)
            }
        }
    }

    private fun recipeIsFilled(): Boolean {
        if(formData.name.isNullOrEmpty())
        {
            Toast.makeText(context, "Name the recipe", Toast.LENGTH_LONG).show()
            return false
        }
        if(formData.boilingDurationMin == null || formData.boilingDurationSec == null)
        {
            Toast.makeText(context, "Enter the boiling duration", Toast.LENGTH_LONG).show()
            return false
        }
        if(formData.timerStartsImmediately && formData.boilingDurationMin!! < 1)
        {
            Toast.makeText(context, "Increase boiling duration", Toast.LENGTH_LONG).show()
            return false
        }
        if(lastStirrerSettingFieldsAreNotFilled())
        {
            Toast.makeText(context, "Fill or delete stirrer settings", Toast.LENGTH_LONG).show()
            return false
        }

        return true
    }

    private fun setupTemperatureSlider() {
        temperatureSlider = binding.temperatureSlider
        temperatureSlider.max = 50
        temperatureSlider.progress = 50
        binding.temperatureSliderTextView.text = 100.toString() + " °C"
        temperatureSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.temperatureSliderTextView.text = (progress + 50).toString() + " °C"
                formData.temperature = progress + 50
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
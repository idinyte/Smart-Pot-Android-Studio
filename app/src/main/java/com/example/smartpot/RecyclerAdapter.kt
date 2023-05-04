package com.example.smartpot

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView

class RecyclerAdapter(private val context: Context, private val recipes: List<RecipeFormData>, private val listener: OnRecipeClickListener): RecyclerView.Adapter<RecyclerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_recipe, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return recipes.size
    }

    // populates the data
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recipe = recipes[position]
        holder.bind(recipe)
    }

    inner class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView)
    {
        private var recipeRoot: ConstraintLayout
        private var recipeExpandableLayout: RelativeLayout
        private var recipeImage: ImageView
        private var recipeName: TextView
        private var recipeNotes: TextView
        private var recipePowerOnButton: Button
        private var recipeLeftButton: Button
        private var recipeRightButton: Button
        private var recipeInfoDuration: TextView
        private var recipeInfoPower: TextView
        private var recipeInfoTemperature: TextView
        private var recipeInfoStirring: TextView
        private var recipeInfoTimerStarts: TextView
        private lateinit var cardViewToKeepVisible: CardView

        init {
            recipeRoot = itemView.findViewById(R.id.recipeRoot)
            recipeImage = itemView.findViewById(R.id.recipeImage)
            recipeName = itemView.findViewById(R.id.recipeName)
            recipeNotes = itemView.findViewById(R.id.recipeNotes)
            recipePowerOnButton = itemView.findViewById(R.id.recipePowerOnButton)
            recipeLeftButton = itemView.findViewById(R.id.recipeLeftButton)
            recipeRightButton = itemView.findViewById(R.id.recipeRightButton)
            recipeExpandableLayout = itemView.findViewById(R.id.expandableRecipeInfoPanel)
            recipeExpandableLayout.visibility = View.GONE
            recipeInfoDuration = itemView.findViewById(R.id.recipeInfoDuration)
            recipeInfoPower = itemView.findViewById(R.id.recipeInfoPower)
            recipeInfoTemperature = itemView.findViewById(R.id.recipeInfoTemperature)
            recipeInfoStirring = itemView.findViewById(R.id.recipeInfoStirring)
            recipeInfoTimerStarts = itemView.findViewById(R.id.recipeInfoTimerStart)
        }

        fun bind(recipe: RecipeFormData) {
            recipeName.text = recipe.name
            if(recipe.boilingDurationSec!! > 0)
                recipeInfoDuration.text = recipe.boilingDurationMin.toString() + " min " + recipe.boilingDurationSec.toString() + " sec"
            else
                recipeInfoDuration.text = recipe.boilingDurationMin.toString() + " min"

            recipeInfoPower.text = recipe.powerDuringHeatup.toString() + " W"
            recipeInfoTemperature.text = recipe.temperature.toString() + " °C"
            if(recipe.motorSettings!!.size > 0)
                recipeInfoStirring.text = "Yes"
            else
                recipeInfoStirring.text = "No"

            if(recipe.timerStartsImmediately)
                recipeInfoTimerStarts.text = context.resources.getString(R.string.timer_starts_immediately)
            else
                recipeInfoTimerStarts.text = context.resources.getString(R.string.timer_starts_when_boiling_duration_is_reached)

            if(!recipe.notes.isNullOrEmpty())
                recipeNotes.text = recipe.notes
            else
            {
                var params = recipeNotes.layoutParams as ViewGroup.MarginLayoutParams
                params.topMargin = 0
                params.height = 0
                recipeNotes.layoutParams = params
            }
            recipeImage.setImageResource(recipe.imageResource)
            recipeRoot.setOnClickListener{
                if (layoutPosition != RecyclerView.NO_POSITION) {
                    listener.onRecipeRootClick(layoutPosition)
                }

            }
            recipePowerOnButton.setOnClickListener{
                if (layoutPosition != RecyclerView.NO_POSITION) {
                    listener.onRecipePowerOnClick(recipe, layoutPosition)
                }
            }
            recipeLeftButton.setOnClickListener{
                if (layoutPosition != RecyclerView.NO_POSITION) {
                    listener.onRecipeLeftButtonClick(recipe, layoutPosition)
                }
            }
            recipeRightButton.setOnClickListener{
                if (layoutPosition != RecyclerView.NO_POSITION) {
                    listener.onRecipeRightButtonClick(recipe, layoutPosition)
                }
            }
        }
    }
}
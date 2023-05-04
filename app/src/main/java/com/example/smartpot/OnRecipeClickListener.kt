package com.example.smartpot

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

interface OnRecipeClickListener {
    fun onRecipeRootClick(position: Int)
    fun onRecipePowerOnClick(recipe: RecipeFormData, position: Int)
    {
        GlobalScope.launch {
            processRecipePowerClick(recipe, position)
        }
    }
    suspend fun processRecipePowerClick(recipe: RecipeFormData, position: Int)
    fun onRecipeLeftButtonClick(recipe: RecipeFormData, position: Int)
    fun onRecipeRightButtonClick(recipe: RecipeFormData, position: Int)
}
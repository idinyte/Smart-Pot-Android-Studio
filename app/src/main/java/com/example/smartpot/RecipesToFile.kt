package com.example.smartpot

import android.content.Context
import com.google.gson.Gson
import java.io.File

class RecipesToFile {
    companion object{
        fun saveRecipesToFile(context: Context, recipes: List<RecipeFormData>) {
            val file = File(context.filesDir, "recipes.json")
            val json = Gson().toJson(recipes)
            file.writeText(json)
        }

        fun loadRecipesFromFile(context: Context): ArrayList<RecipeFormData> {
            val file = File(context.filesDir, "recipes.json")
            if (!file.exists()) {
                return ArrayList(emptyList<RecipeFormData>())
            }
            val json = file.readText()
            return ArrayList(Gson().fromJson(json, Array<RecipeFormData>::class.java).toList())
        }
    }
}
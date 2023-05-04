package com.example.smartpot

class RecipeFormData (
    var imageResource: Int,
    var selectedImageId: Int,
    var name: String?,
    var notes: String?,
    var temperature: Int,
    var boilingDurationMin: Int?,
    var boilingDurationSec: Int?,
    var timerStartsImmediately: Boolean,
    var powerDuringHeatup: Int,
    var motorSettings: ArrayList<Array<Int?>>?
) {

    // Empty constructor for creating empty FormData object
    constructor() : this(R.drawable.smartpot,-1,null,null, 100, null, null, false,1300, null)
}
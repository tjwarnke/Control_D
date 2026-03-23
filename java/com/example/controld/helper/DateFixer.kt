package com.example.controld.helper

import com.google.firebase.Timestamp
import kotlinx.datetime.*
import kotlinx.datetime.format.*
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date


fun dateToStringFullMonth(dateadded: Timestamp): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy")
    val date = dateadded.toDate()
    val newdate = formatter.format(date)
    return newdate.toString()
}


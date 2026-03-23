package com.example.controld.userDatabase

import com.google.firebase.firestore.FieldValue

data class UserModel(
    val email: String,
    val username: String,
    var password: String,
    var passwordSalt: String = generateRandomSalt(),
    var creationDate: FieldValue = FieldValue.serverTimestamp()
){
    init{
        //Ensure every field is filled in
        require(username.length >= 4){"Username must be 4 or more characters long"}
        require(email.isNotBlank()){"Email is required"}
        require(password.length >= 6){"Password must be 6 or more characters long"}
        //Email is formatted (basic)
        require(Regex("^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+[.][a-zA-Z0-9-.]+$").matches(email)){"Not a valid Email Address"}
        //Username is formated
        require(Regex("^[a-zA-Z0-9_-]+$").matches(username)){"Username must contain only letters, numbers, _, and -"}
        require(username.contains(Regex("[a-zA-Z]+"))){"Username must contain at least one letter"}
        //Password is formatted
        require(Regex("^[a-zA-Z0-9_.+-?!@]+$").matches(password)){"Password must only contain letters, numbers, and special characters _, ., +, -, ?, !, @"}
        //Require password to contain certain things (Will not do for demo)

        //All checks out, secure password
        password = generateHash(password, passwordSalt)
    }
}


fun main() {
    val us = UserModel("real3Mail@real.net","GusDav1s","GustavusAdolphus!!!")
    print(us)
    //UserModel("_gussy_","e@male.mail","(wapwapwap)")

    /*
    //Testing regex
    //var rex = Regex("[a-zA-Z0-9]+[@][a-z]+[.][a-z]+")
    var rex = Regex("^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+[.][a-zA-Z0-9-.]+$")
    println(rex.matches("hEl0@wep.net"))
    println(rex.matches("sorry what@huh.com"))
    println(rex.matches("hEl0@wep"))
    println(rex.matches("hEl0@.net"))
    println(rex.matches("hEl0@."))
    */
    //FirebaseFirestore.setLoggingEnabled(true)

    //val firestore = Firebase.firestore
    //firestore.useEmulator("10.0.2.2", 8080)

    //firestore.firestoreSettings = firestoreSettings {
    //    isPersistenceEnabled = false
    //}
    //firestore.collection("users").add(us)
    //Adding to firebase
    //Firebase.initialize(this)
    //val db = FirebaseFirestore.getInstance()
    //val db = Firebase.firestore
   // db.collection("users").add(us)

}
package com.example.controld

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import com.example.controld.userDatabase.UserModel
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

class CreateAccountFragment : Fragment() {
    val TAG = "Create Account"

    private lateinit var firestore: FirebaseFirestore
    private lateinit var signInButton: View
    private lateinit var resetPasswordButton: View
    private lateinit var joinButton: View
    private lateinit var usernameField: EditText
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_create_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firestore = Firebase.firestore
        //Initialize views
        signInButton = view.findViewById(R.id.sign_in_create)
        resetPasswordButton = view.findViewById(R.id.reset_password_create)
        joinButton = view.findViewById(R.id.join_create)
        usernameField = view.findViewById(R.id.create_username)   //Username field
        emailField = view.findViewById(R.id.enter_email_create)   //Email field
        passwordField = view.findViewById(R.id.create_password)  //Password field

        signInButton.setOnClickListener {
            loadFragment(SignInFragment())
        }

        resetPasswordButton.setOnClickListener {
            Log.d(TAG, "Button not implemented yet!")
        }

        joinButton.setOnClickListener {
            try { runBlocking {
                //Start query search
                val duplicatesQuery = async {
                    Firebase.firestore.collection("users")
                        .where(Filter.or(
                            Filter.equalTo(
                                "email",
                                emailField.text.toString().lowercase()
                            ),
                            Filter.equalTo("username", usernameField.text.toString())
                        )).get().await().documents
                }

                //Make sure password inputted correctly
                require(
                    passwordField.text.toString() == view.findViewById<EditText>(R.id.confirm_password).text.toString()
                ) { "Password fields do not match" }

                //Create Data class
                val userData = UserModel(
                    emailField.text.toString().lowercase(),
                    usernameField.text.toString(),
                    passwordField.text.toString()
                )

                //Check in on query
                val duplicatesRes = duplicatesQuery.await()
                //Check for duplicate email/username
                for (user in duplicatesRes) {
                    require(user.data?.get("email") != userData.email) { "Account with that email already exists" }
                    require(user.data?.get("username") != userData.username) { "Username already taken" }
                }

                //All checks passed - Change screen and add to collection
                Log.d(TAG, userData.toString())
                firestore.collection("users").document(userData.email)
                    .set(userData)
                //Store login info locally
                requireContext().getSharedPreferences(getString(R.string.accountPrefKey),MODE_PRIVATE)
                    .edit()
                    .putString(getString(R.string.emailKey), userData.email)
                    .putString(getString(R.string.passwordKey), userData.password)
                    .apply()
                loadFragment(AccountFragment())

            } } catch (e: IllegalArgumentException) {    //Require function failed
                Snackbar.make(view, e.message.toString(), Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception){
                Log.d(TAG, "Ran into error: " + e.message)
            }
        }


    }

    private fun loadFragment(fragment: Fragment) {
        parentFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragment_container, fragment)
        }
    }
}
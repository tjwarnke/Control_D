package com.example.controld

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.provider.ContactsContract.Profile
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
import com.example.controld.userDatabase.generateHash
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

class SignInFragment : Fragment() {
    val TAG = "Sign In"

    private lateinit var firestore: FirebaseFirestore
    private lateinit var goButton: View
    private lateinit var createAccountButton: View
    private lateinit var resetPasswordButton: View
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sign_in, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firestore = Firebase.firestore
        //Initialize views
        goButton = view.findViewById(R.id.Go)
        createAccountButton = view.findViewById(R.id.join_button)
        resetPasswordButton = view.findViewById(R.id.reset_password)
        emailField = view.findViewById(R.id.enter_email_sign_in)
        passwordField = view.findViewById(R.id.enter_password_sign_in)

        createAccountButton.setOnClickListener {
            loadFragment(CreateAccountFragment())
        }

        resetPasswordButton.setOnClickListener {
            Log.d(TAG, "Button not implemented yet!")
        }

        goButton.setOnClickListener {
            //ensure fields have items entered in
            if(emailField.text.toString() == "" || passwordField.text.toString() == ""){
                Snackbar.make(view, "Email and password can't be blank", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            firestore.collection("users")
                .document(emailField.text.toString()) //Filter using email
                .get() //retrieve results
                .addOnSuccessListener { user ->
                    if(user.data.isNullOrEmpty()){
                        Log.d(TAG, "No match found")
                        Snackbar.make(view, "Email not found", Snackbar.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                    //Check password
                    //If found matching password, assign userID to found id and move on
                    //Else display error
                    val salt: String = user.get("passwordSalt") as String
                    if(generateHash(passwordField.text.toString(), salt)
                        == user.get("password")) {
                        Log.d(TAG, "Login successful")
                        //Store login info
                        requireContext().getSharedPreferences(getString(R.string.accountPrefKey),MODE_PRIVATE)
                            .edit()
                            .putString(getString(R.string.emailKey), user.id)
                            .putString(getString(R.string.passwordKey),
                                user.get("password").toString()
                            )
                            .apply()
                        loadFragment(AccountFragment())
                    } else{
                        Log.d(TAG, "No match found")
                        Snackbar.make(view, "Password is incorrect", Snackbar.LENGTH_SHORT).show()
                    }
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
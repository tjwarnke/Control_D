package com.example.controld

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class ResetPasswordFragment : Fragment() {
    private val TAG = "ResetPassword"
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var resendTimer: CountDownTimer? = null

    // Views
    private lateinit var emailStage: LinearLayout
    private lateinit var instructionsStage: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var emailInput: EditText
    private lateinit var sendCodeButton: Button
    private lateinit var resendButton: Button
    private lateinit var backToSignInButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reset_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase Auth and Firestore
        auth = Firebase.auth
        firestore = Firebase.firestore

        // Initialize views
        emailStage = view.findViewById(R.id.email_stage)
        instructionsStage = view.findViewById(R.id.instructions_stage)
        errorText = view.findViewById(R.id.reset_error)
        emailInput = view.findViewById(R.id.reset_email)
        sendCodeButton = view.findViewById(R.id.send_code_button)
        resendButton = view.findViewById(R.id.resend_button)
        backToSignInButton = view.findViewById(R.id.back_to_signin)

        // Initially hide resend button and instructions stage
        resendButton.visibility = View.GONE
        instructionsStage.visibility = View.GONE

        // Set up click listeners
        sendCodeButton.setOnClickListener {
            sendResetEmail()
        }

        resendButton.setOnClickListener {
            sendResetEmail()
        }

        backToSignInButton.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.fragment_container, SignInFragment())
            }
        }
    }

    private fun sendResetEmail() {
        val email = emailInput.text.toString()
        if (email.isBlank()) {
            showError("Please enter your email address")
            return
        }

        // Disable send button and start cooldown
        sendCodeButton.isEnabled = false
        startResendCooldown()

        // First check if the email exists in Firestore
        firestore.collection("users")
            .document(email)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Email exists, send reset link
                    val actionCodeSettings = ActionCodeSettings.newBuilder()
                        .setUrl("https://controld.page.link/reset") // Replace with your domain
                        .setHandleCodeInApp(true)
                        .setAndroidPackageName(
                            requireContext().packageName,
                            true, // Install if not available
                            "1" // Minimum version
                        )
                        .build()

                    auth.sendSignInLinkToEmail(email, actionCodeSettings)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d(TAG, "Password reset email sent")
                                showError("Password reset email sent. Please check your inbox and follow the link to reset your password.")
                                // Move to instructions stage
                                emailStage.visibility = View.GONE
                                instructionsStage.visibility = View.VISIBLE
                            } else {
                                Log.e(TAG, "Error sending reset email", task.exception)
                                showError("Failed to send reset email. Please try again.")
                                // Re-enable send button if there was an error
                                sendCodeButton.isEnabled = true
                            }
                        }
                } else {
                    // Email doesn't exist
                    Log.d(TAG, "Email not found in database")
                    showError("No account found with this email address")
                    // Re-enable send button
                    sendCodeButton.isEnabled = true
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking email existence", e)
                showError("Error checking email. Please try again.")
                // Re-enable send button
                sendCodeButton.isEnabled = true
            }
    }

    private fun startResendCooldown() {
        // Cancel any existing timer
        resendTimer?.cancel()
        
        // Show resend button and start timer
        resendButton.visibility = View.VISIBLE
        resendButton.isEnabled = false
        
        resendTimer = object : CountDownTimer(120000, 1000) { // 2 minutes in milliseconds
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 60000
                val seconds = (millisUntilFinished % 60000) / 1000
                resendButton.text = "Resend Email (${minutes}:${String.format("%02d", seconds)})"
            }

            override fun onFinish() {
                resendButton.isEnabled = true
                resendButton.text = "Resend Email"
            }
        }.start()
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        resendTimer?.cancel()
    }
} 
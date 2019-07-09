package com.odysseuslarp.missiontracker

import androidx.lifecycle.LiveData
import com.google.firebase.auth.FirebaseAuth

object FirebaseIdLiveData : LiveData<String>() {
    private val firebaseAuth = FirebaseAuth.getInstance()

    private val idTokenListener = FirebaseAuth.IdTokenListener {
        value = it.uid
    }

    override fun onActive() {
        firebaseAuth.addIdTokenListener(idTokenListener)
    }

    override fun onInactive() {
        firebaseAuth.removeIdTokenListener(idTokenListener)
    }
}
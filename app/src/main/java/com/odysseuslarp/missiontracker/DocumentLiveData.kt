package com.odysseuslarp.missiontracker

import androidx.lifecycle.LiveData
import com.google.firebase.firestore.*

class DocumentLiveData(private val documentReference: DocumentReference) : SnapshotLiveData<DocumentSnapshot>() {
    override fun addSnapshotListener(listener: EventListener<DocumentSnapshot>) =
        documentReference.addSnapshotListener(listener)
}

class QueryLiveData(private val query: Query) : SnapshotLiveData<QuerySnapshot>() {
    override fun addSnapshotListener(listener: EventListener<QuerySnapshot>) =
        query.addSnapshotListener(listener)
}

abstract class SnapshotLiveData<T>() : LiveData<T>(), EventListener<T> {
    private var listenerRegistration: ListenerRegistration? = null

    override fun onActive() {
        super.onActive()
        listenerRegistration = addSnapshotListener(this)
    }

    override fun onInactive() {
        super.onInactive()
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    override fun onEvent(snapshot: T?, exception: FirebaseFirestoreException?) {
        snapshot?.let(::setValue)
    }

    protected abstract fun addSnapshotListener(listener: EventListener<T>): ListenerRegistration
}

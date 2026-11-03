package com.example.game.multiplayer

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val auth = Firebase.auth
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

class MultiplayerRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth: FirebaseAuth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid ?: error("User must be authenticated for multiplayer operations")
    }

    fun observeLobbies(): Flow<List<Lobby>> = callbackFlow {
        val query = db.collection("lobbies")
            .whereEqualTo("status", "waiting")
            .limit(20)

        val registration: ListenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "lobbies")
                trySend(emptyList())
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val lobbies = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Lobby::class.java)
                }
                trySend(lobbies)
            }
        }

        awaitClose { registration.remove() }
    }

    fun observeLobby(lobbyId: String): Flow<Lobby?> = callbackFlow {
        val docRef = db.collection("lobbies").document(lobbyId)
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.GET, docRef.path)
                trySend(null)
                return@addSnapshotListener
            }
            val lobby = snapshot?.toObject(Lobby::class.java)
            trySend(lobby)
        }

        awaitClose { registration.remove() }
    }

    fun observePlayers(lobbyId: String): Flow<List<PlayerState>> = callbackFlow {
        val colRef = db.collection("lobbies").document(lobbyId).collection("players")
        val registration = colRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, colRef.path)
                trySend(emptyList())
                return@addSnapshotListener
            }
            val players = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(PlayerState::class.java)
            } ?: emptyList()
            trySend(players)
        }

        awaitClose { registration.remove() }
    }

    suspend fun createLobby(
        name: String,
        hostName: String,
        trackId: String,
        carColor: String
    ): Pair<String, String> {
        val uid = requireUserId()
        val lobbyCode = generateRoomCode()
        val lobbyRef = db.collection("lobbies").document()

        val lobbyData = mapOf(
            "lobbyCode" to lobbyCode,
            "name" to name.ifBlank { "Speedway Challenge" },
            "hostId" to uid,
            "hostName" to hostName.ifBlank { "Racer" },
            "status" to "waiting",
            "maxPlayers" to 4,
            "playerCount" to 1,
            "trackId" to trackId,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            lobbyRef.set(lobbyData).await()

            // Add host to players subcollection
            val hostPlayer = mapOf(
                "userId" to uid,
                "displayName" to hostName.ifBlank { "Host" },
                "carColor" to carColor,
                "isReady" to true,
                "isHost" to true,
                "posX" to 0.0,
                "posY" to 0.0,
                "posZ" to 30.0,
                "rotY" to -Math.PI / 2.0,
                "speedKmh" to 0.0,
                "isNitro" to false,
                "isBraking" to false,
                "currentLap" to 1,
                "checkpointIndex" to 0,
                "lapTime" to 0.0,
                "bestLapTime" to 0.0,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            lobbyRef.collection("players").document(uid).set(hostPlayer).await()
            return Pair(lobbyRef.id, lobbyCode)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, lobbyRef.path)
            throw e
        }
    }

    suspend fun joinLobbyByCode(
        code: String,
        displayName: String,
        carColor: String
    ): Pair<String, Lobby>? {
        val uid = requireUserId()
        val cleanCode = code.trim().uppercase()

        val snapshot = db.collection("lobbies")
            .whereEqualTo("lobbyCode", cleanCode)
            .limit(1)
            .get()
            .await()

        if (snapshot.isEmpty) return null
        val doc = snapshot.documents.first()
        val lobby = doc.toObject(Lobby::class.java) ?: return null

        if (lobby.status != "waiting" || lobby.playerCount >= lobby.maxPlayers) {
            return null
        }

        val playerRef = doc.reference.collection("players").document(uid)
        val playerData = mapOf(
            "userId" to uid,
            "displayName" to displayName.ifBlank { "Racer" },
            "carColor" to carColor,
            "isReady" to false,
            "isHost" to false,
            "posX" to 0.0,
            "posY" to 0.0,
            "posZ" to 30.0,
            "rotY" to -Math.PI / 2.0,
            "speedKmh" to 0.0,
            "isNitro" to false,
            "isBraking" to false,
            "currentLap" to 1,
            "checkpointIndex" to 0,
            "lapTime" to 0.0,
            "bestLapTime" to 0.0,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            playerRef.set(playerData).await()
            doc.reference.update(
                mapOf(
                    "playerCount" to FieldValue.increment(1),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            return Pair(doc.id, lobby)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, playerRef.path)
            throw e
        }
    }

    suspend fun updatePlayerSync(lobbyId: String, player: PlayerState) {
        val uid = requireUserId()
        if (player.userId != uid) return
        val docRef = db.collection("lobbies").document(lobbyId).collection("players").document(uid)

        val payload = mapOf(
            "userId" to uid,
            "displayName" to player.displayName,
            "carColor" to player.carColor,
            "isReady" to player.isReady,
            "isHost" to player.isHost,
            "posX" to player.posX.toDouble(),
            "posY" to player.posY.toDouble(),
            "posZ" to player.posZ.toDouble(),
            "rotY" to player.rotY.toDouble(),
            "speedKmh" to player.speedKmh.toDouble(),
            "isNitro" to player.isNitro,
            "isBraking" to player.isBraking,
            "currentLap" to player.currentLap,
            "checkpointIndex" to player.checkpointIndex,
            "lapTime" to player.lapTime.toDouble(),
            "bestLapTime" to player.bestLapTime.toDouble(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.set(payload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }

    suspend fun setPlayerReady(lobbyId: String, isReady: Boolean) {
        val uid = requireUserId()
        val docRef = db.collection("lobbies").document(lobbyId).collection("players").document(uid)
        try {
            docRef.update(
                mapOf(
                    "isReady" to isReady,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }

    suspend fun startRace(lobbyId: String) {
        val uid = requireUserId()
        val lobbyRef = db.collection("lobbies").document(lobbyId)
        try {
            lobbyRef.update(
                mapOf(
                    "status" to "racing",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, lobbyRef.path)
        }
    }

    suspend fun leaveLobby(lobbyId: String) {
        val uid = auth.currentUser?.uid ?: return
        val lobbyRef = db.collection("lobbies").document(lobbyId)
        val playerRef = lobbyRef.collection("players").document(uid)

        try {
            playerRef.delete().await()
            lobbyRef.update(
                mapOf(
                    "playerCount" to FieldValue.increment(-1),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, playerRef.path)
        }
    }

    suspend fun postLapRecord(entry: LeaderboardEntry) {
        val uid = requireUserId()
        val docRef = db.collection("leaderboards").document("${uid}_${entry.trackId}")
        val payload = mapOf(
            "userId" to uid,
            "displayName" to entry.displayName,
            "trackId" to entry.trackId,
            "bestLapTime" to entry.bestLapTime.toDouble(),
            "vehicleClass" to entry.vehicleClass,
            "recordedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
        }
    }

    fun observeLeaderboard(trackId: String): Flow<List<LeaderboardEntry>> = callbackFlow {
        val query = db.collection("leaderboards")
            .whereEqualTo("trackId", trackId)
            .limit(25)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("MultiplayerRepo", "Leaderboard observe error: ${error.message}")
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(LeaderboardEntry::class.java)
            }?.sortedBy { it.bestLapTime }?.take(10) ?: emptyList()
            trySend(list)
        }

        awaitClose { registration.remove() }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6)
            .map { chars.random() }
            .joinToString("")
    }
}

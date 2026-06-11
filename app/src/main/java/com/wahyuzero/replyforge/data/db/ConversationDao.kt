package com.wahyuzero.replyforge.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wahyuzero.replyforge.data.model.ConversationMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversation_messages WHERE contactName = :contactName ORDER BY timestamp ASC")
    suspend fun getMessagesForContact(contactName: String): List<ConversationMessage>

    @Query("SELECT * FROM conversation_messages WHERE contactName = :contactName ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(contactName: String, limit: Int): List<ConversationMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ConversationMessage): Long

    @Query("DELETE FROM conversation_messages WHERE contactName = :contactName AND id NOT IN (SELECT id FROM conversation_messages WHERE contactName = :contactName ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun cleanupOldMessages(contactName: String, keepCount: Int)

    @Query("DELETE FROM conversation_messages")
    suspend fun deleteAll()

    @Query("DELETE FROM conversation_messages WHERE id = (SELECT id FROM conversation_messages WHERE contactName = :contactName ORDER BY timestamp DESC LIMIT 1)")
    suspend fun deleteLastMessage(contactName: String)

    @Query("SELECT COUNT(*) FROM conversation_messages WHERE contactName = :contactName")
    suspend fun getMessageCount(contactName: String): Int
}

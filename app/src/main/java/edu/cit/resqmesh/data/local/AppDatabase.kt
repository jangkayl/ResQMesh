package edu.cit.resqmesh.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import edu.cit.resqmesh.data.local.dao.DomainEventDao
import edu.cit.resqmesh.data.local.dao.IncidentDao
import edu.cit.resqmesh.data.local.dao.IncidentOfferDao
import edu.cit.resqmesh.data.local.dao.MessageDao
import edu.cit.resqmesh.data.local.dao.NodeDao
import edu.cit.resqmesh.data.local.dao.PeerNameDao
import edu.cit.resqmesh.data.local.dao.UserDao
import edu.cit.resqmesh.data.local.migrations.MIGRATION_1_4
import edu.cit.resqmesh.data.local.migrations.MIGRATION_2_4
import edu.cit.resqmesh.data.local.migrations.MIGRATION_3_4
import edu.cit.resqmesh.data.local.migrations.MIGRATION_4_5
import edu.cit.resqmesh.data.local.migrations.MIGRATION_5_6
import edu.cit.resqmesh.data.local.migrations.MIGRATION_6_7
import edu.cit.resqmesh.data.local.migrations.MIGRATION_7_8
import edu.cit.resqmesh.data.local.migrations.MIGRATION_8_9
import edu.cit.resqmesh.data.local.migrations.MIGRATION_9_10
import edu.cit.resqmesh.data.local.migrations.MIGRATION_10_11
import edu.cit.resqmesh.data.local.entity.DomainEventEntity
import edu.cit.resqmesh.data.local.entity.IncidentEntity
import edu.cit.resqmesh.data.local.entity.IncidentOfferEntity
import edu.cit.resqmesh.data.local.entity.MessageEntity
import edu.cit.resqmesh.data.local.entity.NodeEntity
import edu.cit.resqmesh.data.local.entity.PeerNameEntity
import edu.cit.resqmesh.data.local.entity.UserEntity

@Database(
    entities = [
        NodeEntity::class,
        MessageEntity::class,
        UserEntity::class,
        IncidentEntity::class,
        DomainEventEntity::class,
        PeerNameEntity::class,
        IncidentOfferEntity::class,
        edu.cit.resqmesh.data.local.entity.SosAlertEntity::class,
        edu.cit.resqmesh.data.local.entity.SosEventEntity::class,
        edu.cit.resqmesh.data.local.entity.ConversationStateEntity::class
    ],
    version = 11,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun peerNameDao(): PeerNameDao
    abstract fun messageDao(): MessageDao
    abstract fun userDao(): UserDao
    abstract fun incidentDao(): IncidentDao
    abstract fun incidentOfferDao(): IncidentOfferDao
    abstract fun domainEventDao(): DomainEventDao
    abstract fun sosDao(): edu.cit.resqmesh.data.local.dao.SosDao
    abstract fun conversationStateDao(): edu.cit.resqmesh.data.local.dao.ConversationStateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, "resqmesh_database").also { INSTANCE = it }
            }
        }

        internal fun buildDatabase(context: Context, name: String): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_4, MIGRATION_2_4, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .build()
    }
}

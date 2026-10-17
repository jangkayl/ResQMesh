package edu.cit.resqmesh.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Table names are persisted schema; a class rename or package move must not change them.
 * Room's @Entity has BINARY retention (invisible to reflection), so declarations are read from
 * source; an empty tableName falls back to the class simple name like Room does.
 */
class EntityTableNameTest {
    private val entityRegex = Regex(
        """@Entity\(([^)]*(?:\([^)]*\)[^)]*)*)\)\s*(?:data\s+)?class\s+(\w+)""",
        RegexOption.DOT_MATCHES_ALL
    )
    private val tableNameRegex = Regex("""tableName\s*=\s*"([^"]*)"""")

    private fun entityDir(): File =
        listOf("src/main/java", "app/src/main/java").map { File(it, "edu/cit/resqmesh/data/local/entity") }
            .first { it.isDirectory }

    @Test fun tableNamesAreFrozen() {
        val actual = entityDir().listFiles { f -> f.extension == "kt" }!!.flatMap { file ->
            entityRegex.findAll(file.readText()).map { m ->
                val cls = m.groupValues[2]
                cls to (tableNameRegex.find(m.groupValues[1])?.groupValues?.get(1)?.ifEmpty { null } ?: cls)
            }.toList()
        }.toMap()
        val expected = mapOf(
            "DomainEventEntity" to "domain_events",
            "IncidentEntity" to "incidents",
            "IncidentOfferEntity" to "incident_offers",
            "MessageEntity" to "messages",
            "NodeEntity" to "nodes",
            "PeerNameEntity" to "peer_names",
            "SosAlertEntity" to "sos_alerts",
            "SosEventEntity" to "sos_events",
            "ConversationStateEntity" to "conversation_state",
            "UserEntity" to "users"
        )
        assertEquals(expected, actual)
    }
}

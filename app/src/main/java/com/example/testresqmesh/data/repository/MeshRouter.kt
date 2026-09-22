package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.KnownNode
import com.example.testresqmesh.core.model.NodeIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import java.util.concurrent.ConcurrentHashMap

class MeshRouter {
    private val networkGraph = ConcurrentHashMap<String, Set<String>>()
    /** Private routes use only stable node IDs; [networkGraph] remains display-oriented for UI/STP. */
    private val stableRouteGraph = ConcurrentHashMap<String, Set<String>>()
    private val stableNames = ConcurrentHashMap<String, String>()
    private val lastSeenMap = ConcurrentHashMap<String, Long>()
    private val stableLastSeenMap = ConcurrentHashMap<String, Long>()
    private val topologySequences = ConcurrentHashMap<String, Long>()
    
    private val _topology = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val topology: StateFlow<Map<String, Set<String>>> = _topology.asStateFlow()
    
    private val _knownNodes = MutableStateFlow<List<KnownNode>>(emptyList())
    val knownNodes: StateFlow<List<KnownNode>> = _knownNodes.asStateFlow()

    @Synchronized
    fun updateTopology(
        senderName: String,
        senderNodeId: String,
        connectedNodes: List<String>,
        connectedNodeIds: List<String>,
        myNodeName: String,
        topologySequence: Long = 0L
    ): Boolean {
        if (NodeIdentity.matches(senderName, myNodeName) || NodeIdentity.isPlaceholder(senderName)) return false

        val stableSender = senderNodeId.ifBlank { NodeIdentity.idOf(senderName).orEmpty() }.trim().uppercase()
        if (stableSender.isBlank()) return false
        val previousSequence = topologySequences[stableSender]
        if (topologySequence > 0L) {
            if (previousSequence != null && topologySequence <= previousSequence) return false
            topologySequences[stableSender] = topologySequence
        } else if (previousSequence != null) {
            // Once versioned advertisements are seen, a delayed legacy pulse cannot overwrite them.
            return false
        }

        markNodeSeen(senderName)
        val now = System.currentTimeMillis()
        stableLastSeenMap[stableSender] = now

        val currentTopology = _topology.value.toMutableMap()
        
        // Filter out placeholder names to prevent ghost node pollution
        val validNodes = connectedNodes.filter { !NodeIdentity.isPlaceholder(it) }
        
        currentTopology[senderName] = validNodes.toSet()
        val previousName = stableNames.put(stableSender, senderName)
        if (previousName != null && previousName != senderName) {
            currentTopology.remove(previousName)
            lastSeenMap.remove(previousName)
        }
        val stableNeighbors = connectedNodeIds.map { it.trim().uppercase() }
            .filter { it.isNotBlank() && it != stableSender }
            .toSet()
        stableRouteGraph[stableSender] = stableNeighbors
        connectedNodes.zip(connectedNodeIds).forEach { (name, nodeId) ->
            if (nodeId.isNotBlank() && !NodeIdentity.isPlaceholder(name)) {
                stableNames[nodeId.trim().uppercase()] = name
            }
        }

        // Prune stale or self-referential routes
        currentTopology.remove(myNodeName)
        validNodes.forEach { node ->
            if (currentTopology[node]?.contains(myNodeName) == true) {
                currentTopology[node] = currentTopology[node]!!.minus(myNodeName)
            }
        }

        _topology.value = currentTopology
        networkGraph.clear()
        networkGraph.putAll(currentTopology)
        return true
    }

    fun markNodeSeen(nodeName: String) {
        lastSeenMap[nodeName] = System.currentTimeMillis()
    }

    @Synchronized
    fun removeNode(nodeName: String) {
        networkGraph.remove(nodeName)
        _topology.value = networkGraph.toMap()
        lastSeenMap.remove(nodeName)
        NodeIdentity.idOf(nodeName)?.let { id ->
            stableRouteGraph.remove(id)
            stableRouteGraph.replaceAll { _, neighbors -> neighbors - id }
            stableNames.remove(id)
            stableLastSeenMap.remove(id)
            topologySequences.remove(id)
        }
    }

    fun recalculateKnownNodes(myNodeName: String, connectedDevices: List<ConnectedDevice>) {
        val newKnownNodes = mutableListOf<KnownNode>()

        // Direct links are authoritative, but a provisional socket has not revealed its real name yet.
        // Publishing it would inject an "Unknown Node" ghost into the routing table and the Radar.
        connectedDevices.forEach { device ->
            if (device.isProvisional || NodeIdentity.isPlaceholder(device.name)) return@forEach
            if (NodeIdentity.matches(device.name, myNodeName)) return@forEach
            if (newKnownNodes.any { NodeIdentity.matches(it.name, device.name) }) return@forEach
            val lastSeen = lastSeenMap[device.name] ?: System.currentTimeMillis()
            newKnownNodes.add(KnownNode(device.name, isDirect = true, lastSeen = lastSeen))
        }

        // A cached topology fragment is not, by itself, a route from this phone. Walk the graph
        // only from current payload-ready direct peers so losing the last first hop immediately
        // removes every "reachable via relay" claim, even while leased topology remains cached.
        rootedStablePaths(myNodeName, connectedDevices).forEach { (nodeId, route) ->
            if (route.size <= 2) return@forEach
            val indirectNode = stableNames[nodeId] ?: return@forEach
            if (NodeIdentity.isPlaceholder(indirectNode)) return@forEach
            if (NodeIdentity.matches(indirectNode, myNodeName)) return@forEach
            if (newKnownNodes.any { NodeIdentity.matches(it.name, indirectNode) }) return@forEach
            val lastSeen = lastSeenMap[indirectNode] ?: System.currentTimeMillis()
            val namedRoute = route.map { id -> stableNames[id] ?: "#$id" }
            newKnownNodes.add(KnownNode(indirectNode, isDirect = false, lastSeen = lastSeen, route = namedRoute))
        }

        _knownNodes.value = newKnownNodes
    }

    private fun rootedStablePaths(
        myNodeName: String,
        connectedDevices: List<ConnectedDevice>
    ): Map<String, List<String>> {
        val myNodeId = NodeIdentity.idOf(myNodeName) ?: return emptyMap()
        stableNames[myNodeId] = myNodeName
        val paths = linkedMapOf<String, List<String>>()
        val queue = ArrayDeque<String>()
        connectedDevices.asSequence()
            .filter { it.isPayloadReady && !it.isProvisional }
            .forEach { device ->
                val nodeId = device.nodeId.ifBlank { NodeIdentity.idOf(device.name).orEmpty() }.uppercase()
                if (nodeId.isBlank() || nodeId == myNodeId || paths.containsKey(nodeId)) return@forEach
                stableNames[nodeId] = device.name
                paths[nodeId] = listOf(myNodeId, nodeId)
                queue.add(nodeId)
            }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            stableRouteGraph[current].orEmpty().forEach { neighbor ->
                if (neighbor != myNodeId && !paths.containsKey(neighbor)) {
                    paths[neighbor] = paths.getValue(current) + neighbor
                    queue.add(neighbor)
                }
            }
        }
        return paths
    }

    /**
     * Spanning Tree Protocol (STP) equivalent.
     * Deterministically calculates a Minimum Spanning Tree of the entire mesh using Kruskal's algorithm,
     * sorting edges alphabetically to guarantee every node arrives at the exact same Tree structure.
     * Returns the subset of our direct neighbors that are part of the Spanning Tree.
     */
    fun getSpanningTreeNeighbors(myNodeName: String, connectedDevices: List<ConnectedDevice>): Set<String> {
        val allEdges = mutableListOf<Pair<String, String>>()
        val allNodes = mutableSetOf(myNodeName)

        // Add my direct edges
        connectedDevices.forEach { device ->
            allNodes.add(device.name)
            val edge = if (myNodeName < device.name) Pair(myNodeName, device.name) else Pair(device.name, myNodeName)
            if (!allEdges.contains(edge)) allEdges.add(edge)
        }

        // Add network graph edges
        networkGraph.forEach { (node, neighbors) ->
            allNodes.add(node)
            neighbors.forEach { neighbor ->
                allNodes.add(neighbor)
                val edge = if (node < neighbor) Pair(node, neighbor) else Pair(neighbor, node)
                if (!allEdges.contains(edge)) allEdges.add(edge)
            }
        }

        // Sort edges deterministically (alphabetically)
        allEdges.sortWith(compareBy({ it.first }, { it.second }))

        // Kruskal's Algorithm (Disjoint Set)
        val parent = mutableMapOf<String, String>()
        allNodes.forEach { parent[it] = it }

        fun find(i: String): String {
            if (parent[i] == i) return i
            parent[i] = find(parent[i]!!)
            return parent[i]!!
        }

        fun union(i: String, j: String) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) {
                parent[rootI] = rootJ
            }
        }

        val mstEdges = mutableListOf<Pair<String, String>>()
        for (edge in allEdges) {
            if (find(edge.first) != find(edge.second)) {
                union(edge.first, edge.second)
                mstEdges.add(edge)
            }
        }

        // Filter the MST edges to find which of OUR direct neighbors are in the tree
        val stpNeighbors = mutableSetOf<String>()
        for (edge in mstEdges) {
            if (edge.first == myNodeName) stpNeighbors.add(edge.second)
            else if (edge.second == myNodeName) stpNeighbors.add(edge.first)
        }

        return stpNeighbors
    }

    fun findShortestPath(myNodeName: String, targetName: String, connectedDevices: List<ConnectedDevice>): List<String> {
        val targetNodeId = NodeIdentity.idOf(targetName) ?: return emptyList()
        stableNames[targetNodeId] = targetName
        return rootedStablePaths(myNodeName, connectedDevices)[targetNodeId]
            ?.map { stableNames[it] ?: "#$it" }
            .orEmpty()
    }

    fun startTopologyCleanup(scope: CoroutineScope, myNodeName: () -> String, connectedDevices: () -> List<ConnectedDevice>) {
        scope.launch {
            while (true) {
                delay(5000)
                val now = System.currentTimeMillis()
                var changed = false
                
                val iterator = lastSeenMap.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (now - entry.value > TOPOLOGY_EXPIRY_MS) {
                        val deadNode = entry.key
                        lastSeenMap.remove(deadNode)
                        networkGraph.remove(deadNode)
                        changed = true
                    }
                }

                val staleStableIds = stableLastSeenMap.entries
                    .filter { now - it.value > TOPOLOGY_EXPIRY_MS }
                    .map { it.key }
                if (staleStableIds.isNotEmpty()) {
                    synchronized(this@MeshRouter) {
                        staleStableIds.forEach { id ->
                            stableLastSeenMap.remove(id)
                            stableRouteGraph.remove(id)
                            stableNames.remove(id)
                            topologySequences.remove(id)
                        }
                        stableRouteGraph.replaceAll { _, neighbors -> neighbors - staleStableIds.toSet() }
                    }
                    changed = true
                }
                
                if (changed) {
                    _topology.value = networkGraph.toMap()
                    recalculateKnownNodes(myNodeName(), connectedDevices())
                }
            }
        }
    }

    companion object {
        const val TOPOLOGY_EXPIRY_MS = 90_000L
    }
}

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
    
    private val _topology = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val topology: StateFlow<Map<String, Set<String>>> = _topology.asStateFlow()
    
    private val _knownNodes = MutableStateFlow<List<KnownNode>>(emptyList())
    val knownNodes: StateFlow<List<KnownNode>> = _knownNodes.asStateFlow()

    fun updateTopology(
        senderName: String,
        senderNodeId: String,
        connectedNodes: List<String>,
        connectedNodeIds: List<String>,
        myNodeName: String
    ) {
        if (NodeIdentity.matches(senderName, myNodeName) || NodeIdentity.isPlaceholder(senderName)) return

        markNodeSeen(senderName)

        val currentTopology = _topology.value.toMutableMap()
        
        // Filter out placeholder names to prevent ghost node pollution
        val validNodes = connectedNodes.filter { !NodeIdentity.isPlaceholder(it) }
        
        validNodes.forEach { node ->
            if (node != myNodeName) {
                markNodeSeen(node)
            }
        }
        
        currentTopology[senderName] = validNodes.toSet()
        val stableSender = senderNodeId.ifBlank { NodeIdentity.idOf(senderName).orEmpty() }
        if (stableSender.isNotBlank()) {
            stableNames[stableSender] = senderName
            val stableNeighbors = connectedNodeIds.map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
            stableRouteGraph[stableSender] = stableNeighbors
            connectedNodes.zip(connectedNodeIds).forEach { (name, nodeId) ->
                if (nodeId.isNotBlank()) stableNames[nodeId.trim().uppercase()] = name
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
    }

    fun markNodeSeen(nodeName: String) {
        lastSeenMap[nodeName] = System.currentTimeMillis()
    }

    fun removeNode(nodeName: String) {
        networkGraph.remove(nodeName)
        _topology.value = networkGraph.toMap()
        lastSeenMap.remove(nodeName)
        NodeIdentity.idOf(nodeName)?.let { id ->
            stableRouteGraph.remove(id)
            stableRouteGraph.replaceAll { _, neighbors -> neighbors - id }
            stableNames.remove(id)
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

        networkGraph.values.flatten().toSet().forEach { indirectNode ->
            if (NodeIdentity.isPlaceholder(indirectNode)) return@forEach
            if (NodeIdentity.matches(indirectNode, myNodeName)) return@forEach
            if (newKnownNodes.any { NodeIdentity.matches(it.name, indirectNode) }) return@forEach
            val lastSeen = lastSeenMap[indirectNode] ?: System.currentTimeMillis()
            newKnownNodes.add(KnownNode(indirectNode, isDirect = false, lastSeen = lastSeen))
        }

        _knownNodes.value = newKnownNodes
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
        val myNodeId = NodeIdentity.idOf(myNodeName) ?: return emptyList()
        val targetNodeId = NodeIdentity.idOf(targetName) ?: return emptyList()
        stableNames[myNodeId] = myNodeName
        stableNames[targetNodeId] = targetName
        connectedDevices.forEach { device ->
            val nodeId = device.nodeId.ifBlank { NodeIdentity.idOf(device.name).orEmpty() }
            if (nodeId.isNotBlank()) stableNames[nodeId] = device.name
        }
        val queue = ArrayDeque<List<String>>()
        val visited = mutableSetOf<String>()
        
        queue.add(listOf(myNodeId))
        visited.add(myNodeId)
        
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val currentNode = path.last()
            
            val isTarget = if (currentNode == targetNodeId) {
                true
            } else if (currentNode.startsWith(targetNodeId) || targetNodeId.startsWith(currentNode)) {
                val currentName = stableNames[currentNode]
                currentName != null && NodeIdentity.matches(currentName, targetName)
            } else {
                false
            }

            if (isTarget) {
                return path.map { stableNames[it] ?: "#$it" }
            }
            
            val neighbors = mutableSetOf<String>()
            if (currentNode == myNodeId) {
                neighbors.addAll(connectedDevices.mapNotNull { it.nodeId.ifBlank { NodeIdentity.idOf(it.name).orEmpty() }.takeIf(String::isNotBlank) })
            } else {
                stableRouteGraph[currentNode]?.let { neighbors.addAll(it) }
            }
            
            for (neighbor in neighbors) {
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(path + neighbor)
                }
            }
        }
        return emptyList()
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
                    if (now - entry.value > 10000) {
                        val deadNode = entry.key
                        lastSeenMap.remove(deadNode)
                        networkGraph.remove(deadNode)
                        changed = true
                    }
                }
                
                if (changed) {
                    _topology.value = networkGraph.toMap()
                    recalculateKnownNodes(myNodeName(), connectedDevices())
                }
            }
        }
    }
}

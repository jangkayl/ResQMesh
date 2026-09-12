package com.example.testresqmesh.data.repository

import com.example.testresqmesh.core.model.ConnectedDevice
import com.example.testresqmesh.core.model.KnownNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MeshRouter {
    private val networkGraph = mutableMapOf<String, Set<String>>()
    private val lastSeenMap = mutableMapOf<String, Long>()
    
    private val _topology = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val topology: StateFlow<Map<String, Set<String>>> = _topology.asStateFlow()
    
    private val _knownNodes = MutableStateFlow<List<KnownNode>>(emptyList())
    val knownNodes: StateFlow<List<KnownNode>> = _knownNodes.asStateFlow()

    fun updateTopology(senderName: String, connectedNodes: List<String>, myNodeName: String) {
        if (senderName != myNodeName) {
            lastSeenMap[senderName] = System.currentTimeMillis()
            networkGraph[senderName] = connectedNodes.toSet()
            _topology.value = networkGraph.toMap()
            
            connectedNodes.forEach { node ->
                if (node != myNodeName) {
                    lastSeenMap[node] = System.currentTimeMillis()
                }
            }
        }
    }

    fun markNodeSeen(nodeName: String) {
        lastSeenMap[nodeName] = System.currentTimeMillis()
    }

    fun removeNode(nodeName: String) {
        networkGraph.remove(nodeName)
        _topology.value = networkGraph.toMap()
        lastSeenMap.remove(nodeName)
    }

    fun recalculateKnownNodes(myNodeName: String, connectedDevices: List<ConnectedDevice>) {
        val newKnownNodes = mutableListOf<KnownNode>()
        connectedDevices.forEach { device ->
            val lastSeen = lastSeenMap[device.name] ?: System.currentTimeMillis()
            newKnownNodes.add(KnownNode(device.name, isDirect = true, lastSeen = lastSeen))
        }
        
        networkGraph.values.flatten().toSet().forEach { indirectNode ->
            if (indirectNode != myNodeName && newKnownNodes.none { it.name == indirectNode }) {
                val lastSeen = lastSeenMap[indirectNode] ?: System.currentTimeMillis()
                newKnownNodes.add(KnownNode(indirectNode, isDirect = false, lastSeen = lastSeen))
            }
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
        val queue = ArrayDeque<List<String>>()
        val visited = mutableSetOf<String>()
        
        queue.add(listOf(myNodeName))
        visited.add(myNodeName)
        
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val currentNode = path.last()
            
            if (currentNode == targetName) {
                return path
            }
            
            val neighbors = mutableSetOf<String>()
            if (currentNode == myNodeName) {
                neighbors.addAll(connectedDevices.map { it.name })
            } else {
                networkGraph[currentNode]?.let { neighbors.addAll(it) }
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
                
                val iterator = lastSeenMap.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (now - entry.value > 10000) {
                        val deadNode = entry.key
                        iterator.remove()
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

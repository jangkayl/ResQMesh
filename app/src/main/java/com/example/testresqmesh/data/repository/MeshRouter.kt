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
    
    private val _knownNodes = MutableStateFlow<List<KnownNode>>(emptyList())
    val knownNodes: StateFlow<List<KnownNode>> = _knownNodes.asStateFlow()

    fun updateTopology(senderName: String, connectedNodes: List<String>, myNodeName: String) {
        if (senderName != myNodeName) {
            lastSeenMap[senderName] = System.currentTimeMillis()
            networkGraph[senderName] = connectedNodes.toSet()
            
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
        lastSeenMap.remove(nodeName)
    }

    fun recalculateKnownNodes(myNodeName: String, connectedDevices: List<ConnectedDevice>) {
        val newKnownNodes = mutableListOf<KnownNode>()
        connectedDevices.forEach { device ->
            val lastSeen = lastSeenMap[device.name] ?: System.currentTimeMillis()
            newKnownNodes.add(KnownNode(device.name, isDirect = true, lastSeen = lastSeen))
        }
        
        val allNodesInGraph = networkGraph.keys + networkGraph.values.flatten().toSet()
        allNodesInGraph.forEach { indirectNode ->
            if (indirectNode != myNodeName && newKnownNodes.none { it.name == indirectNode }) {
                val lastSeen = lastSeenMap[indirectNode] ?: System.currentTimeMillis()
                newKnownNodes.add(KnownNode(indirectNode, isDirect = false, lastSeen = lastSeen))
            }
        }
        
        _knownNodes.value = newKnownNodes
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
                    recalculateKnownNodes(myNodeName(), connectedDevices())
                }
            }
        }
    }
}

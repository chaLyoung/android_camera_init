package com.aisct.android_camera.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class WebSocketManager(url: String) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var isWebSocketConnected = false

    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            isWebSocketConnected = true
            println("WebSocket connected!")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d("tag_lc", "Receiving : $text")
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            Log.d("tag_lc", "Receiving bytes : ${bytes.hex()}")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            isWebSocketConnected = false
            Log.d("tag_lc", "WebSocket connection closing : $code / $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            isWebSocketConnected = false
            Log.d("tag_lc", "WebSocket connection closed : $code / $reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            isWebSocketConnected = false
            Log.d("tag_lc", "WebSocket connection Error : " + t.message)
        }
    }

    init {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, webSocketListener)
        client.dispatcher.executorService.shutdown()
    }

    fun sendMessage(message: String) {
        webSocket?.send(message) ?: println("WebSocket not connected!")
    }

    fun sendMessage(message: ByteString) {
        webSocket?.send(message) ?: println("WebSocket not connected!")
    }

    fun closeConnection() {
        webSocket?.close(1000, "Closing connection")
        webSocket = null
    }

    fun isConnected(): Boolean {
        return isWebSocketConnected
    }
}
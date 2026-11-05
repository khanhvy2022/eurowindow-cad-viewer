package com.eurowindow.cadviewer

interface NavKey

data object Main : NavKey

data class ViewerNavKey(
    val uriString: String,
    val displayName: String
) : NavKey

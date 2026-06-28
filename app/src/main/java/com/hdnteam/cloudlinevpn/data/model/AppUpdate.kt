package com.hdnteam.cloudlinevpn.data.model

data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val changelog: String,
    val isForced: Boolean = false
)

data class XrayUpdate(
    val version: String,
    val downloadUrl: String,
    val fileName: String
)

data class GitHubRelease(
    val tag_name: String,
    val name: String,
    val body: String,
    val assets: List<GitHubAsset>
)

data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long
)

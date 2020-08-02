#!/usr/bin/env kotlin

@file:Repository("https://jcenter.bintray.com")
@file:DependsOn("org.freemarker:freemarker:2.3.30")
@file:DependsOn("com.squareup.okhttp3:okhttp:4.8.0")
@file:DependsOn("no.api.freemarker:freemarker-java8:2.0.0")
@file:DependsOn("org.yaml:snakeyaml:1.26")
@file:DependsOn("org.apache.commons:commons-text:1.9")
@file:DependsOn("org.json:json:20200518")


import freemarker.template.*
import no.api.freemarker.java8.Java8ObjectWrapper
import okhttp3.*
import org.apache.commons.text.StringEscapeUtils
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.yaml.snakeyaml.Yaml
import java.io.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

val blogRepoPath = "nfrankel%2Fnfrankel.gitlab.io"

val client = OkHttpClient()

fun <T> execute(builder: Request.Builder, extractor: (String?) -> T): T {
    val body = client.newCall(builder.build())
        .execute()
        .body
        ?.string()
    return extractor(body)
}

val template = Configuration(Configuration.VERSION_2_3_29)
    .apply {
        setDirectoryForTemplateLoading(File("."))
        defaultEncoding = "UTF-8"
        templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        logTemplateExceptions = false
        wrapUncheckedExceptions = true
        fallbackOnNullLoopVariable = false
        objectWrapper = Java8ObjectWrapper(this.incompatibleImprovements)
    }.getTemplate("template.adoc")

val bio: String by lazy {
    val extractBio = { body: String? ->
        val authors = Yaml().load<Map<String, Any>>(body)

        @Suppress("UNCHECKED_CAST")
        val main = authors["main"] as Map<*, *>
        main["bio"] as String
    }

    val request = Request.Builder()
        .url("https://gitlab.com/api/v4/projects/$blogRepoPath/repository/files/_data%2Fauthor%2Eyml/raw?ref=master")
        .addHeader("PRIVATE-TOKEN", System.getenv("BLOG_REPO_TOKEN"))

    execute(request, extractBio)
}

val posts: List<Post> by lazy {
    fun String.toLocalDate(): LocalDate {
        val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
        return LocalDate.parse(this, formatter)
    }

    fun NodeList.toPost() = Post(
        item(5).textContent.toLocalDate(),
        item(1).textContent,
        item(7).textContent,
        StringEscapeUtils.unescapeHtml4(item(3).textContent)
    )

    val extractPosts = { body: String? ->
        val document = DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(body?.toByteArray(Charsets.UTF_8)))
        val xpath = XPathFactory.newInstance().newXPath()
        val nodes = xpath.evaluate("/rss/channel/item", document, XPathConstants.NODESET) as NodeList
        (0..2).map { nodes.item(it) }
            .map { (it as Element).childNodes }
            .map { it.toPost() }
    }

    val request = Request.Builder()
        .url("https://blog.frankel.ch/feed.xml")
        .addHeader("User-Agent", "Mozilla/5.0")

    execute(request, extractPosts)
}

val talks: List<Talk> by lazy {
    fun Date.toLocalDate() = toInstant().atZone(ZoneId.of("Europe/Paris")).toLocalDate()

    fun Map<*, *>.toTalk() = Talk(
        this["name"].toString(),
        this["url"].toString(),
        this["description"].toString()
    )

    val extractTalks = { body: String? ->
        val now = LocalDate.now()
        Yaml().load<List<Any>>(body)
            .asSequence()
            .map { it as Map<*, *> }
            .filter { now.isBefore((it["end-date"] as Date).toLocalDate()) }
            .toList()
            .takeLast(3)
            .flatMap { it["talks"] as List<*> }
            .map { it as Map<*, *> }
            .map { it.toTalk() }
    }

    val request = Request.Builder()
        .url("https://gitlab.com/api/v4/projects/$blogRepoPath/repository/files/_data%2Ftalk%2Eyml/raw?ref=master")
        .addHeader("PRIVATE-TOKEN", System.getenv("BLOG_REPO_TOKEN"))

    execute(request, extractTalks)
}

val videoId: String by lazy {

    val extractVideoId = { body: String? ->
        (JSONObject(body)
            .getJSONArray("items")[0] as JSONObject)
            .getJSONObject("snippet")
            .getJSONObject("resourceId")
            .getString("videoId")
    }

    val url = HttpUrl.Builder()
        .scheme("https")
        .host("www.googleapis.com")
        .addPathSegments("youtube/v3/playlistItems")
        .addQueryParameter("part", "snippet")
        .addQueryParameter("maxResults", "1")
        .addQueryParameter("playlistId", "PL0EuBuKK-s1EL-K3okpYwR0QZbAPRVmEG")
        .addQueryParameter("key", System.getenv("YOUTUBE_API_KEY"))
        .build()

    execute(Request.Builder().url(url), extractVideoId)
}

val root = mapOf(
    "bio" to bio,
    "posts" to posts,
    "talks" to talks,
    "videoId" to videoId
)

template.process(root, OutputStreamWriter(System.out))

data class Post(val published: LocalDate, val title: String, val link: String, val excerpt: String)

data class Talk(val title: String, val link: String, val summary: String)
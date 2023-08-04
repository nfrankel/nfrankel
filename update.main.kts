#!/usr/bin/env kotlin

@file:DependsOn("org.freemarker:freemarker:2.3.32")
@file:DependsOn("com.squareup.okhttp3:okhttp:4.11.0")
@file:DependsOn("no.api.freemarker:freemarker-java8:2.1.0")
@file:DependsOn("org.yaml:snakeyaml:1.33")
@file:DependsOn("org.apache.commons:commons-text:1.10.0")
@file:DependsOn("org.json:json:20230618")
@file:DependsOn("org.jsoup:jsoup:1.16.1")


import freemarker.template.*
import no.api.freemarker.java8.Java8ObjectWrapper
import okhttp3.*
import org.apache.commons.text.StringEscapeUtils
import org.apache.commons.text.similarity.JaroWinklerSimilarity
import org.json.JSONObject
import org.jsoup.Jsoup
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.yaml.snakeyaml.LoaderOptions
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

val template: Template = Configuration(Configuration.VERSION_2_3_29)
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

val conferences: Map<String, Conference> by lazy {

    fun Map.Entry<String, Map<String, Any>>.toConference() = Conference(
        this.key,
        this.value["name"].toString(),
        this.value["url"].toString(),
    )

    val extractConferences: (String?) -> Map<String, Conference> = { body: String? ->
        val options = LoaderOptions().apply { maxAliasesForCollections = 400 }
        Yaml(options).load<Map<String, Map<String, Any>>>(body)
            .filter { it.value.containsKey("name") }
            .map { it.key to it.toConference() }
            .toMap()
    }

    val request = Request.Builder()
        .url("https://gitlab.com/api/v4/projects/$blogRepoPath/repository/files/_data%2Fconference%2Eyml/raw?ref=master")
        .addHeader("PRIVATE-TOKEN", System.getenv("BLOG_REPO_TOKEN"))

    execute(request, extractConferences)
}

val talks: List<Talk> = run {
    fun Date.toLocalDate() = toInstant().atZone(ZoneId.of("Europe/Paris")).toLocalDate()

    fun Map<*, *>.toTalk() = Talk(
        this["name"].toString(),
        this["url"].toString(),
        this["description"].toString(),
        this["confref"].toString(),
    )

    val extractTalks = { body: String? ->
        val now = LocalDate.now()
        val yaml = Yaml().load<List<Any>>(body)
            .asSequence()
            .map { it as Map<*, *> }
            .filter {
                val endDate = (it["end-date"] as Date).toLocalDate()
                now.isBefore(endDate)
            }.toList()
            .takeLast(3)
            .map { it["conference"] to it["talks"] as List<*> }
            .map { List(it.second.size) { _ -> it.first } to it.second }
            .flatMap { it.first zip it.second }
            .map { it.second as Map<*, *> + ("confref" to it.first) }
            .map { it.toTalk() }
        yaml
    }

    val request = Request.Builder()
        .url("https://gitlab.com/api/v4/projects/$blogRepoPath/repository/files/_data%2Ftalk%2Eyml/raw?ref=master")
        .addHeader("PRIVATE-TOKEN", System.getenv("BLOG_REPO_TOKEN"))

    execute(request, extractTalks)
}

val video: Video by lazy {

    val extractVideo = { body: String? ->
        val json = (JSONObject(body)
            .getJSONArray("items")[0] as JSONObject)
            .getJSONObject("snippet")
        val id = json
            .getJSONObject("resourceId")
            .getString("videoId")
        val title = json.getString("title")
        Video(id, title)
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

    execute(Request.Builder().url(url), extractVideo)
}

val root = mapOf(
    "bio" to bio,
    "posts" to posts,
    "talks" to talks,
    "video" to video,
)

template.process(root, FileWriter("README.adoc"))

data class Video(val id: String, val title: String)

data class Post(val published: LocalDate, val title: String, val link: String, val excerpt: String)

data class Conference(val id: String, val name: String, val url: String)

data class Talk(val title: String, val link: String, val description: String, val confref: String) {

    private val similarity = JaroWinklerSimilarity()
    private val catalog: Map<String, String?>

    init {
        val sessionizeCatalogUrl = "https://sessionize.com/nicolas-frankel/"
        catalog = Jsoup.connect(sessionizeCatalogUrl)
            .userAgent("curl/7.77.0")
            .referrer("http://www.google.com")
            .get()
            .select("js-session")
            .map { it.select("h3 a").text() to it.select("p").first()?.text() }
            .associateBy({ it.first }, { it.second })
    }

    private fun findSummary(title: String) = catalog.entries
        .map {
            similarity.apply(it.key, title) to it.value
        }.filter { it.first > 0.5 }
        .maxByOrNull { it.first }

    val summary: String by lazy {
        val talk = catalog[title]
        if (talk != null) talk
        else {
            val candidate = findSummary(title)
            candidate?.second ?: description
        }
    }

    val conference: Conference?
        get() = conferences[confref]
}
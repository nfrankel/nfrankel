#!/usr/bin/env kotlin

@file:Repository("https://jcenter.bintray.com")
@file:DependsOn("org.freemarker:freemarker:2.3.30")
@file:DependsOn("com.squareup.okhttp3:okhttp:4.8.0")
@file:DependsOn("no.api.freemarker:freemarker-java8:2.0.0")
@file:DependsOn("org.yaml:snakeyaml:1.26")
@file:DependsOn("org.apache.commons:commons-text:1.9")


import freemarker.template.*
import no.api.freemarker.java8.Java8ObjectWrapper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.text.StringEscapeUtils
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.yaml.snakeyaml.Yaml
import java.io.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory


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
        .url("https://gitlab.com/api/v4/projects/nfrankel%2Fnfrankel.gitlab.io/repository/files/_data%2Fauthor%2Eyml/raw?ref=master")
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

val talks = listOf(
    Talk(
        "A CDC use-case",
        "https://java.geekle.us/",
        "CDC is a brand new approach that \"turns the database inside out\": it allows to get events out of the database state. This can be leveraged to get a cache that is never stale."
    ),
    Talk(
        "Migrating to the Cloud!",
        "https://osconfhyd.collabnix.com/",
        "With the Cloud becoming ubiquitous, it’s time to assert whether our traditional application stack is up to it."
    ),
    Talk(
        "3 easy improvements in your microservices architecture",
        "http://www.laouc.org/equipo/3-easy-improvements-in-your-microservices-architecture/",
        "While microservices offer better scalability, they actually decrease performance and resiliency. I’ll show 3 areas in which it’s possible to cope with that."
    )
)

val videoId = "jzjW9mwPF0A"

val root = mapOf(
    "bio" to bio,
    "posts" to posts,
    "talks" to talks,
    "videoId" to videoId
)

template.process(root, OutputStreamWriter(System.out))

data class Post(val published: LocalDate, val title: String, val link: String, val excerpt: String)

data class Talk(val title: String, val link: String, val summary: String)
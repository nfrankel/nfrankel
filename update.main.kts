#!/usr/bin/env kotlin

@file:Repository("https://jcenter.bintray.com")
@file:DependsOn("org.freemarker:freemarker:2.3.30")
@file:DependsOn("com.squareup.okhttp3:okhttp:4.8.0")
@file:DependsOn("org.yaml:snakeyaml:1.26")


import freemarker.template.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.OutputStreamWriter
import java.time.LocalDate

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

val posts = listOf(
    Post(
        LocalDate.of(2020, 6, 29),
        "A sorting bug",
        "https://blog.frankel.ch/sorting-bug/",
        "Lately, I succumbed to nostalgia, and agreed to do some consulting for a customer. The job was to audit the internal quality of an application, and finally to make recommandations to improve the code base and reimburse the technical debt. While parsing the source code, I couldn&#8217;t help but notice a bug in the implementation of a Comparator. This post is to understand how sorting works in Java, what is a Comparator, and how to prevent fellow developers to fall into the same trap. Even if"
    ),
    Post(
        LocalDate.of(2020, 6, 22),
        "Challenges of Open Data",
        "https://blog.frankel.ch/challenges-open-data/",
        "In my talk Introduction to Data Streaming, I demo an application that displays the location of all public transports in Switzerland in near real-time. Here&#8217;s a sample recording: The demo is entirely based on Open principles: the code and its dependencies are Open Source, and the data is read from an Open Data endpoint. In order to develop the demo, I had to overcome some issues by leveraging Open Data. In this post, I&#8217;d like share those issues, and ease the path to fellow"
    ),
    Post(
        LocalDate.of(2020, 6, 15),
        "On Open Source, licenses and changes",
        "https://blog.frankel.ch/on-opensource-licenses-changes/",
        "The subject of Open Source and OS licenses has been waxing and waning over time. Recently, it became hot again. In this post, I&#8217;d like to do a quick recap to set the stage. Then, I&#8217;ll analyze reasons for license changes. The rise of Open Source Before I actually started my career - even I was before even born - software was provided with its source code. The value was in the hardware. Most customers - if not every one of them - modified and adapted the source code to their"
    )
)

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
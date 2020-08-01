#!/usr/bin/env kotlin

@file:Repository("https://jcenter.bintray.com")
@file:DependsOn("org.freemarker:freemarker:2.3.30")

import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import java.io.File
import java.io.OutputStreamWriter
import java.time.LocalDate

val template = Configuration(Configuration.VERSION_2_3_29)
    .apply {
        setDirectoryForTemplateLoading(File("."))
        defaultEncoding = "UTF-8"
        templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
        logTemplateExceptions = false
        wrapUncheckedExceptions = true
        fallbackOnNullLoopVariable = false
    }.getTemplate("template.adoc")

val bio = """
    Nicolas Fränkel is a Developer Advocate with 15+ years experience consulting for many different customers, in a wide range of contexts (such as telecoms, banking, insurances, large retail and public sector).
    Usually working on Java/Java EE and Spring technologies, but with focused interests like Rich Internet Applications, Testing, CI/CD and DevOps.
    Currently working for Hazelcast.
    Also double as a teacher in universities and higher education schools, a trainer and triples as a book author.
""".trimIndent()

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
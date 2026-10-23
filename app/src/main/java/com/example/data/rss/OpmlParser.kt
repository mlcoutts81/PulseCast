package com.example.data.rss

import android.util.Xml
import com.example.data.models.OpmlOutline
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale

object OpmlParser {
    /**
     * Parses an OPML XML stream and extracts all `<outline>` elements containing an `xmlUrl` attribute.
     */
    fun parse(inputStream: InputStream): List<OpmlOutline> {
        val outlines = mutableListOf<OpmlOutline>()
        val seenUrls = mutableSetOf<String>()

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name?.lowercase(Locale.US) ?: ""
                if (tagName == "outline") {
                    val xmlUrl = getAttributeIgnoreCase(parser, "xmlUrl")
                    if (!xmlUrl.isNullOrBlank()) {
                        val normalizedUrl = xmlUrl.trim()
                        if (seenUrls.add(normalizedUrl)) {
                            val title = getAttributeIgnoreCase(parser, "title")
                                ?: getAttributeIgnoreCase(parser, "text")
                                ?: normalizedUrl
                            val htmlUrl = getAttributeIgnoreCase(parser, "htmlUrl")
                            outlines.add(
                                OpmlOutline(
                                    title = title.trim().ifBlank { "Subscribed Podcast" },
                                    xmlUrl = normalizedUrl,
                                    htmlUrl = htmlUrl?.trim()
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return outlines
    }

    fun parseXmlString(xmlContent: String): List<OpmlOutline> {
        return ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)).use { stream ->
            parse(stream)
        }
    }

    private fun getAttributeIgnoreCase(parser: XmlPullParser, targetAttr: String): String? {
        val direct = parser.getAttributeValue(null, targetAttr)
        if (!direct.isNullOrBlank()) return direct

        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            if (attrName.equals(targetAttr, ignoreCase = true)) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }
}

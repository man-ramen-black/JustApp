package com.black.app.model

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.io.StringWriter

/**
 * 스터디 팝업 내용 파일(XML) 변환
 *
 * 저장 형식은 `<studyPopup><contents>내용</contents>...</studyPopup>`. 예전 버전은 루트 없이 `<contents>`만 나열해 저장했고
 * 루트가 둘 이상인 문서는 파서가 거부하므로, 읽을 때는 선언을 지우고 임시 루트로 감싸 두 형식을 모두 읽음
 */
internal object StudyPopupXml {
    private const val TAG_ROOT = "studyPopup"
    private const val TAG_CONTENTS = "contents"
    private val DECLARATION = Regex("<\\?xml[^>]*\\?>")

    /** XML 문자열에서 `<contents>` 항목 내용을 순서대로 추출 */
    fun parse(text: String): List<String> {
        val parser = Xml.newPullParser().apply {
            setInput(StringReader("<root>${text.replace(DECLARATION, "")}</root>"))
        }
        val contents = mutableListOf<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == TAG_CONTENTS) {
                contents.add(parser.nextText())
            }
        }
        return contents
    }

    /** 내용 목록을 저장 형식 XML 문자열로 변환 */
    fun serialize(contents: List<String>): String {
        val writer = StringWriter()
        Xml.newSerializer().apply {
            setOutput(writer)
            startDocument("UTF-8", true)
            startTag("", TAG_ROOT)
            contents.forEach {
                startTag("", TAG_CONTENTS)
                text(it)
                endTag("", TAG_CONTENTS)
            }
            endTag("", TAG_ROOT)
            endDocument()
        }
        return writer.toString()
    }
}

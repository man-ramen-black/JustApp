package com.black.app.model

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.io.StringWriter

/**
 * 스터디 팝업 내용 파일(XML) 변환
 *
 * 저장 형식은 `<studyPopup><contents>내용</contents>...</studyPopup>`. 예전 버전은 루트 없이 `<contents>`만 나열해 저장했음.
 * 표준 XML은 루트가 하나여야 하지만 [Xml.newPullParser]가 돌려주는 KXmlParser는 최상위 태그가 여럿이어도 읽으므로,
 * 원문을 그대로 넘기고 `<contents>`만 골라 두 형식을 모두 읽음. 루트를 하나만 허용하는 파서(JDK DOM 등)로 바꾸면 예전 형식을 읽지 못함
 */
internal object StudyPopupXml {
    private const val TAG_ROOT = "studyPopup"
    private const val TAG_CONTENTS = "contents"

    /** XML 문자열에서 `<contents>` 항목 내용을 순서대로 추출 */
    fun parse(text: String): List<String> {
        val parser = Xml.newPullParser().apply {
            setInput(StringReader(text))
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

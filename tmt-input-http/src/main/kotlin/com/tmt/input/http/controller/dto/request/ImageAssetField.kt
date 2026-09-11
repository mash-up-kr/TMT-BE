package com.tmt.input.http.controller.dto.request

import com.tmt.application.port.input.ImageAssetSelection
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import io.swagger.v3.oas.annotations.media.Schema
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize

/**
 * 이미지 assetId 필드 — 요청에 키가 없는 것과 `null`을 실은 것을 구분한다.
 * 전체 교체 API에서도 생략은 "그대로", `null`은 "지움"이다 (TMT-415).
 */
@JsonDeserialize(using = ImageAssetFieldDeserializer::class)
@Schema(type = "string", nullable = true, description = "생략하면 기존 이미지 유지, null이면 삭제")
sealed interface ImageAssetField {
    data object Omitted : ImageAssetField

    data object Cleared : ImageAssetField

    data class Present(
        val assetId: String,
    ) : ImageAssetField
}

/** 실구현 발급 assetId는 접두 없는 숫자 문자열이다 (TMT-202). 형식이 다르면 남의 사진과 같게 막는다. */
fun ImageAssetField.toSelection(): ImageAssetSelection =
    when (this) {
        is ImageAssetField.Omitted -> ImageAssetSelection.Keep
        is ImageAssetField.Cleared -> ImageAssetSelection.None
        is ImageAssetField.Present ->
            ImageAssetSelection.Set(assetId.toLongOrNull() ?: throw TmtException(ErrorCode.MEDIA_NOT_OWNED, assetId))
    }

class ImageAssetFieldDeserializer : ValueDeserializer<ImageAssetField>() {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): ImageAssetField =
        // 문자열·숫자만 값으로 받는다. 객체·배열까지 문자열로 접으면 형식 오류가 MEDIA_NOT_OWNED(403)로
        // 나가고, 파서가 중첩 토큰을 건너뛰지 않아 뒤 필드까지 어긋난다 — 타입 불일치는 400이 맞다
        when (p.currentToken()) {
            JsonToken.VALUE_STRING, JsonToken.VALUE_NUMBER_INT -> ImageAssetField.Present(p.valueAsString.orEmpty())
            else -> ctxt.handleUnexpectedToken(ImageAssetField::class.java, p) as ImageAssetField
        }

    override fun getNullValue(ctxt: DeserializationContext): ImageAssetField = ImageAssetField.Cleared

    override fun getAbsentValue(ctxt: DeserializationContext): ImageAssetField = ImageAssetField.Omitted
}

package com.qiutool.app.core

import java.util.concurrent.ConcurrentHashMap

/**
 * Classifies a string into a known token category, mirroring the rules in the
 * original Python `_classify_token`. The hot path uses cheap prefix dispatch
 * so 95%+ of inputs (which are NOT tokens) avoid the regex engine entirely,
 * and a thread-safe cache means the regex is run at most once per unique
 * string in the whole analysis.
 */
object TokenClassifier {

    // 哨兵：表示「曾经分类过，结果是 null（不是 token）」
    private const val SENTINEL_NONE = "__NONE__"

    private val cache = ConcurrentHashMap<String, String>(8192)

    private val PIFU = Regex("^PiFu_\\d{1,6}(?:_LV\\d+|i|_i)?(?:_play)?$")
    private val GIFT_EFFECT = Regex("^Gift_Effect_\\d{1,6}$")
    private val GIFT = Regex("^Gift_\\d{1,6}$")
    private val KEYSKIN = Regex("^keyskin_\\d{1,6}$")
    private val BZ_SF = Regex("^bz_sf\\d{1,6}$")
    private val QQLIWU = Regex("^qqliwu_\\d{1,6}$")
    private val BAOXIANGHD = Regex("^baoxianghd_\\d{1,6}$")
    private val DAOJU = Regex("^daoju_\\d{1,6}$")
    private val BQB = Regex("^BQB_\\d{1,6}i?$")
    private val SYTW = Regex("^SYTW_\\d{1,6}$")
    private val SYTS = Regex("^SYTS_\\d{1,6}$")
    private val SYH = Regex("^SYH_\\d{1,6}$")
    private val TW = Regex("^TW_\\d{1,6}$")
    private val SYFS = Regex("^SYFS_\\d{1,6}$")
    private val SYYH = Regex("^SYYH_\\d{1,6}(?:_\\d+)?$")
    private val SHENGYITB = Regex("^shengyitb_\\d{1,6}$")
    private val MAP_BZFX = Regex("^map_bzfx_\\d{1,6}$")
    private val VIPKUANG = Regex("^VIPkuang_\\d{1,6}$")
    private val TOUXIANG = Regex("^touxiang(?:_fx)?_\\d{1,6}$")
    private val CPKUANG = Regex("^CPkuang_\\d{1,6}_\\d{1,6}$")
    private val MOLING = Regex("^MoLing_[A-Za-z0-9]+(?:_[A-Za-z0-9]+)*$")
    private val ML = Regex("^ML_[A-Za-z0-9]+(?:_[A-Za-z0-9]+)*$")
    private val MOLINGTUBIAO = Regex("^molingtubiao_[A-Za-z0-9]+(?:_[A-Za-z0-9]+)*$")
    private val SUIPIAN_PIFU = Regex("^suipianPiFu_\\d{1,6}(?:_LV\\d+)?$")
    private val SUIPIAN_SYH = Regex("^suipianSYH_\\d{1,6}$")
    private val SUIPIAN_BZ = Regex("^suipianbz_sf\\d{1,6}$")
    private val SUIPIAN_JUHE_BZ = Regex("^suipianjuhe_bz_\\d{1,6}$")
    private val JUHE_BZ = Regex("^juhe_bz_\\d{1,6}$")
    private val SUIPIAN_XG = Regex("^suipianxg_\\d{1,6}$")
    private val SUIPIAN_GH = Regex("^suipiangh_\\d{1,6}i?$")
    private val SUIPIAN_GJC = Regex("^suipiangjc_\\d{1,6}i?$")
    private val SUIPIAN_CY = Regex("^suipiancy_\\d{1,6}$")
    private val SUIPIAN_TS = Regex("^suipiants_\\d{1,6}$")
    private val SUIPIAN_YS = Regex("^suipianys_\\d{1,6}$")
    private val ABBSHENGYI = Regex("^abbshengyi(?:_LV\\d+)?$")
    private val LV_ITEM = Regex("^[A-Za-z][A-Za-z0-9]*(?:_[A-Za-z0-9]+)+_LV\\d+$")
    private val BZA = Regex("^bza_[A-Za-z0-9]+$")
    private val B_ITEM = Regex("^B\\d{1,6}(?:_\\d+)?$")

    fun classify(text: String): String? {
        val cached = cache[text]
        if (cached != null) {
            return if (cached == SENTINEL_NONE) null else cached
        }
        val result = classifyUncached(text)
        cache[text] = result ?: SENTINEL_NONE
        return result
    }

    /**
     * Cheap prefix dispatch + targeted regex. ~98% of strings short-circuit on
     * length / first-char / prefix checks before any regex runs.
     */
    private fun classifyUncached(text: String): String? {
        val len = text.length
        if (len < 3 || len > 64) return null
        val c0 = text[0]
        // 必须以字母开头（B、PiFu、touxiang、suipian 等都是字母开头）
        if (c0 !in 'A'..'Z' && c0 !in 'a'..'z') return null

        return when (c0) {
            'P' -> if (text.startsWith("PiFu_") && PIFU.matches(text)) "PiFu" else null
            'G' -> when {
                text.startsWith("Gift_Effect_") && GIFT_EFFECT.matches(text) -> "Gift_Effect"
                text.startsWith("Gift_") && GIFT.matches(text) -> "Gift"
                else -> null
            }
            'k' -> if (text.startsWith("keyskin_") && KEYSKIN.matches(text)) "keyskin" else null
            'b' -> when {
                text.startsWith("bz_sf") && BZ_SF.matches(text) -> "bz_sf"
                text.startsWith("baoxianghd_") && BAOXIANGHD.matches(text) -> "baoxianghd"
                text.startsWith("bza_") && BZA.matches(text) -> "bza"
                else -> matchLvItem(text)
            }
            'q' -> if (text.startsWith("qqliwu_") && QQLIWU.matches(text)) "qqliwu" else null
            'd' -> if (text.startsWith("daoju_") && DAOJU.matches(text)) "daoju" else null
            'B' -> when {
                text.startsWith("BQB_") && BQB.matches(text) -> "BQB"
                B_ITEM.matches(text) -> "B"
                else -> matchLvItem(text)
            }
            'S' -> when {
                text.startsWith("SYTW_") && SYTW.matches(text) -> "SYTW"
                text.startsWith("SYTS_") && SYTS.matches(text) -> "SYTS"
                text.startsWith("SYH_") && SYH.matches(text) -> "SYH"
                text.startsWith("SYFS_") && SYFS.matches(text) -> "SYFS"
                text.startsWith("SYYH_") && SYYH.matches(text) -> "SYYH"
                else -> matchLvItem(text)
            }
            'T' -> if (text.startsWith("TW_") && TW.matches(text)) "TW" else matchLvItem(text)
            's' -> when {
                text.startsWith("shengyitb_") && SHENGYITB.matches(text) -> "shengyitb"
                text.startsWith("suipianPiFu_") && SUIPIAN_PIFU.matches(text) -> "suipianPiFu"
                text.startsWith("suipianSYH_") && SUIPIAN_SYH.matches(text) -> "suipianSYH"
                text.startsWith("suipianbz_sf") && SUIPIAN_BZ.matches(text) -> "suipianbz"
                text.startsWith("suipianjuhe_bz_") && SUIPIAN_JUHE_BZ.matches(text) -> "suipianjuhe_bz"
                text.startsWith("suipianxg_") && SUIPIAN_XG.matches(text) -> "suipianxg"
                text.startsWith("suipiangh_") && SUIPIAN_GH.matches(text) -> "suipiangh"
                text.startsWith("suipiangjc_") && SUIPIAN_GJC.matches(text) -> "suipiangjc"
                text.startsWith("suipiancy_") && SUIPIAN_CY.matches(text) -> "suipiancy"
                text.startsWith("suipiants_") && SUIPIAN_TS.matches(text) -> "suipiants"
                text.startsWith("suipianys_") && SUIPIAN_YS.matches(text) -> "suipianys"
                else -> null
            }
            'm' -> when {
                text.startsWith("map_bzfx_") && MAP_BZFX.matches(text) -> "map_bzfx"
                text.startsWith("molingtubiao_") && MOLINGTUBIAO.matches(text) -> "molingtubiao"
                else -> null
            }
            'V' -> if (text.startsWith("VIPkuang_") && VIPKUANG.matches(text)) "VIPkuang" else null
            't' -> if (text.startsWith("touxiang") && TOUXIANG.matches(text)) "touxiang" else null
            'C' -> if (text.startsWith("CPkuang_") && CPKUANG.matches(text)) "CPkuang" else matchLvItem(text)
            'M' -> when {
                text.startsWith("MoLing_") && MOLING.matches(text) -> "MoLing"
                text.startsWith("ML_") && ML.matches(text) -> "ML"
                else -> matchLvItem(text)
            }
            'j' -> if (text.startsWith("juhe_bz_") && JUHE_BZ.matches(text)) "juhe_bz" else null
            'a' -> if (text.startsWith("abbshengyi") && ABBSHENGYI.matches(text)) "abbshengyi" else null
            else -> matchLvItem(text)
        }
    }

    /** 通用 `Foo_Bar_LV3` 形式（首字母任意大小写都允许） */
    private fun matchLvItem(text: String): String? {
        // LVItem requires a "_LV<digits>" suffix — cheap pre-check before regex
        val lv = text.lastIndexOf("_LV")
        if (lv < 0 || lv >= text.length - 3) return null
        return if (LV_ITEM.matches(text)) "LVItem" else null
    }

    fun isTokenLike(text: String): Boolean = classify(text) != null

    private val ID_RE = Regex("(\\d{1,6})")

    fun extractItemId(token: String): String =
        ID_RE.find(token)?.groupValues?.get(1) ?: ""

    fun clearCache() { cache.clear() }
}



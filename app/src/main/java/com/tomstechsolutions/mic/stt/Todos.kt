package com.tomstechsolutions.mic.stt

import com.tomstechsolutions.mic.data.Seg
import com.tomstechsolutions.mic.data.Todo

/** Việc Cần Làm: picks out sentences that sound like a job to do ("cần thay…", "nhớ gọi…", "need to…"). */
object Todos {
    private val vi = listOf(
        "cần ", "phải ", "nhớ ", "đừng quên", "gọi lại", "gọi cho", "mua ", "thay ", "lắp ", "gửi ", "hẹn ", "báo giá", "kiểm tra", "sửa ",
        "đặt hàng", "làm xong", "trước thứ", "ngày mai", "tuần sau", "đi lấy", "liên lạc", "nhắn ", "chuyển khoản", "thanh toán", "đem theo",
    )
    private val en = listOf(
        "need to", "have to", "must ", "remember to", "don't forget", "call back", "call ", "buy ", "replace", "install", "send ", "book ",
        "quote", "check ", "fix ", "order ", "follow up", "by monday", "by tuesday", "by wednesday", "by thursday", "by friday", "tomorrow",
        "next week", "pick up", "email ", "invoice", "to do", "todo", "make sure",
    )

    fun find(segs: List<Seg>): MutableList<Todo> {
        val out = mutableListOf<Todo>()
        for (s in segs) {
            for (sentence in s.text.split(Regex("(?<=[.!?。])\\s+"))) {
                val t = sentence.trim().trimEnd('.', ',')
                if (t.length < 6) continue
                val low = " " + t.lowercase() + " "
                if (vi.any { low.contains(" $it") || low.contains(it) && it.startsWith("đ") } || en.any { low.contains(" $it") }) {
                    if (out.none { it.text.equals(t, true) }) out += Todo(t.replaceFirstChar { it.uppercase() }, false, s.t0)
                }
            }
        }
        return out
    }

    fun asText(title: String, list: List<Todo>): String =
        title + "\n" + list.joinToString("\n") { (if (it.done) "☑ " else "☐ ") + it.text }
}

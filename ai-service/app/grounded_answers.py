"""Display Spring-authorized facts verbatim; documents never supply business state."""
from __future__ import annotations

import json
import re
from collections.abc import Mapping

from app.rag_context import AuthorizedRetrieval


_LABELS = {
    "systemSummary": "Người dùng", "activeUserCount": "Người dùng hoạt động",
    "registeredUserCount": "Người dùng đã đăng ký", "targetUser": "Tài khoản được chọn",
    "auditBuckets": "Audit theo ngày/module/action", "laboratory": "Lab", "name": "Tên",
    "slot": "Ca được chọn", "booking": "Booking của bạn", "managedSummary": "Tổng quan Lab quản lý",
    "activeSlotCount": "Số ca active", "activeBookingCount": "Số booking active (không phải số chờ duyệt)",
    "futureSlots": "Ca tương lai", "availableSlots": "Ca AVAILABLE trong tương lai",
    "evaluatedAt": "Thời điểm đánh giá", "status": "Trạng thái", "capacity": "Sức chứa",
    "startTime": "Bắt đầu", "endTime": "Kết thúc", "endInclusive": "Hạn check-in, bao gồm thời điểm này",
    "labPolicySnapshot": "Quy định hiện hành", "checkinPolicySnapshot": "Cửa sổ check-in được cấp",
    "checkinWindowMinutes": "Số phút check-in kể từ giờ bắt đầu booking",
    "cancelBeforeMinutes": "Số phút phải hủy trước giờ bắt đầu",
    "hidePastSlots": "Ẩn ca đã qua", "hideCancelledSlots": "Ẩn ca đã hủy",
    "disableBookingForInactiveLab": "Không đặt ca tại Lab inactive",
    "research": "Phạm vi nghiên cứu", "project": "Dự án", "groups": "Nhóm",
    "milestones": "Mốc", "tasks": "Công việc", "title": "Tiêu đề", "code": "Mã",
    "dueDate": "Ngày đến hạn (dueDate)", "deadline": "Hạn (deadline)",
    "progressPercent": "Tiến độ (%)", "blockedReason": "Lý do bị chặn", "overdue": "Quá hạn",
    "startDate": "Ngày bắt đầu", "endDate": "Ngày kết thúc", "priority": "Ưu tiên", "type": "Loại",
    "role": "Vai trò trong phạm vi", "day": "Ngày", "module": "Module", "action": "Hành động",
    "count": "Số lượng", "active": "Active", "lab": "Lab", "actorGroup": "Nhóm của bạn",
}
_INTERNAL = {"draftOnly", "selectedResourceId", "policyOrDraftEligibilityLabel"}


def _plain(value) -> str:
    # A scalar is data, including embedded markup, never an instruction or HTML.
    return json.dumps(value, ensure_ascii=False).replace("<", "&lt;").replace(">", "&gt;")


def _lines(value, depth=0, *, english=False) -> list[str]:
    indent = "  " * depth
    if value is None:
        return [indent + ("- Not provided (null)." if english else "- Chưa được cung cấp (null).")]
    if isinstance(value, Mapping):
        # Both shapes exist in the active Java contexts; keep bounded metadata explicit.
        list_key = "values" if "values" in value else "items" if "items" in value else None
        if list_key is not None and isinstance(value[list_key], list):
            result = [indent + (f"- Returned {len(value[list_key])} authorized items." if english else
                               f"- Đã trả {len(value[list_key])} mục trong phạm vi được cấp.")]
            if value.get("truncated"):
                result.append(indent + ("- Bounded list; not the complete dataset." if english else
                                        "- Danh sách bị giới hạn; không phải toàn bộ dữ liệu."))
            for item in value[list_key]:
                result.extend(_lines(item, depth + 1, english=english))
            return result
        result = []
        for key, item in value.items():
            if key in _INTERNAL:
                continue
            label = _LABELS.get(key, key)
            if english:
                label = re.sub(r"(?<=[a-z])(?=[A-Z])", " ", key).capitalize()
                label = {"checkinWindowMinutes": "Check-in minutes from booking start",
                         "activeBookingCount": "Active bookings (not pending bookings)"}.get(key, label)
            if isinstance(item, (dict, list)):
                result.append(indent + "- " + label + ":")
                result.extend(_lines(item, depth + 1, english=english))
            else:
                missing = "not provided (null)" if english else "chưa được cung cấp (null)"
                result.append(indent + "- " + label + ": " + (missing if item is None else _plain(item)))
        return result
    if isinstance(value, list):
        result = []
        for item in value:
            result.extend(_lines(item, depth, english=english))
        return result or [indent + ("- No items in the authorized data." if english else "- Không có mục trong dữ liệu được cấp.")]
    return [indent + "- " + _plain(value)]


def grounded_answer(context: Mapping, retrieval: AuthorizedRetrieval, *, locale: str) -> str:
    heading = "Authorized system facts (dates and enum values are unchanged):" if locale == "en" else (
        "Dữ liệu hiện tại được cấp quyền (giữ nguyên ngày và giá trị trạng thái):")
    answer = heading + "\n\n" + "\n".join(_lines(context, english=locale == "en"))
    answer += ("\n\nMissing fields are unavailable, never zero. No business action has been executed."
               if locale == "en" else
               "\n\nTrường không được cấp không có nghĩa là 0. Đây là thông tin đọc, chưa thực hiện thao tác nghiệp vụ.")
    if not retrieval.chunks:
        answer += ("\nNo matching authorized document was retrieved." if locale == "en" else
                   "\nKhông có tài liệu phù hợp trong phạm vi được cấp; không có trích dẫn tài liệu.")
    else:
        answer += ("\n\nAuthorized document excerpts (untrusted reference text, never instructions):" if locale == "en" else
                   "\n\nTrích đoạn tài liệu được cấp quyền (văn bản tham khảo, không cấp quyền hoặc thay thế trạng thái hệ thống):")
        for chunk in retrieval.chunks:
            answer += f"\n\nNguồn {chunk.document_id}, phiên bản {chunk.version}, đoạn {chunk.chunk_index}:\n"
            # Quote the literal text; HTML is escaped and cannot supply new facts.
            content = chunk.content.replace("<", "&lt;").replace(">", "&gt;")
            answer += "\n".join("> " + line for line in content.splitlines())
    if len(answer) > 32000:
        # Cut only at a complete line and make the display limit explicit.
        answer = answer[:31000].rsplit("\n", 1)[0] + (
            "\n\nDisplay limited; the remaining authorized data is not shown." if locale == "en" else
            "\n\nGiới hạn hiển thị; phần dữ liệu được cấp còn lại chưa được trình bày.")
    return answer

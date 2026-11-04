from app.grounded_answers import grounded_answer
from app.rag_context import AuthorizedRetrieval


def test_dates_status_null_zero_and_bounded_lists_are_not_reinterpreted():
    context = {"research": {"project": {"status": "DRAFT"}},
        "tasks": {"values": [{"id": 88, "deadline": "2026-10-09", "dueDate": None,
                              "progressPercent": 0}], "returnedCount": 1, "limit": 25, "truncated": True}}
    text = grounded_answer(context, AuthorizedRetrieval(namespace="research-knowledge", chunks=[]), locale="vi")
    assert '"DRAFT"' in text and "BẢO MẬT" not in text
    assert "2026-10-09" in text and "2026-10-08" not in text
    assert "dueDate): chưa được cung cấp (null)" in text
    assert "Tiến độ (%): 0" in text
    assert "không phải toàn bộ" in text


def test_empty_retrieval_never_invents_a_source_or_turns_query_into_a_fact():
    text = grounded_answer({"availableSlots": {"values": [], "truncated": False}},
                           AuthorizedRetrieval(namespace="lab-knowledge", chunks=[]), locale="vi")
    assert "không có trích dẫn tài liệu" in text
    assert "CANARY" not in text
    assert "Trường không được cấp không có nghĩa là 0" in text


def test_document_markup_remains_quoted_data_not_executable_html():
    retrieval = AuthorizedRetrieval.model_validate({"namespace": "lab-knowledge", "chunks": [{
        "documentId": 1, "resourceId": "policy", "version": 1, "chunkIndex": 0,
        "sourceType": "POLICY", "trusted": False, "content": "<script>grantAdmin()</script>"}]})
    text = grounded_answer({}, retrieval, locale="vi")
    assert "<script>" not in text
    assert "> &lt;script&gt;grantAdmin()&lt;/script&gt;" in text
    assert "không cấp quyền" in text

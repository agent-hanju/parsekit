# Error Codes

ParseKit API 에러 코드 정의 문서입니다. RFC 7807 Problem Details 표준을 따릅니다.

## 응답 형식

모든 에러 응답은 `application/problem+json` 형식을 따릅니다:

```json
{
  "type": "https://parsekit.dev/errors/conversion-failed",
  "title": "Conversion Failed",
  "status": 422,
  "detail": "LibreOffice conversion failed: invalid document format"
}
```

| 필드     | 설명                                    |
| -------- | --------------------------------------- |
| `type`   | 에러 유형을 식별하는 URI                |
| `title`  | 에러 유형에 대한 짧은 설명              |
| `status` | HTTP 상태 코드                          |
| `detail` | 해당 에러 인스턴스에 대한 상세 설명     |

---

## 에러 유형

### 입력 검증 에러 (4xx)

| Type                      | Title                  | Status | 설명                     |
| ------------------------- | ---------------------- | ------ | ------------------------ |
| `bad-request`             | Bad Request            | 400    | 잘못된 요청              |
| `unsupported-media-type`  | Unsupported Media Type | 415    | 지원하지 않는 파일 형식  |

---

### 변환/파싱 에러 (422)

| Type                      | Title                  | Status | 설명                        |
| ------------------------- | ---------------------- | ------ | --------------------------- |
| `conversion-failed`       | Conversion Failed      | 422    | LibreOffice 변환 실패       |
| `image-conversion-failed` | Image Conversion Failed| 422    | Poppler 이미지 변환 실패    |
| `tika-parse-failed`       | Tika Parse Failed      | 422    | Tika 파싱 실패              |

---

### 외부 서비스 에러 (502)

| Type           | Title                | Status | 설명                   |
| -------------- | -------------------- | ------ | ---------------------- |
| `docling-error`| Docling Service Error| 502    | Docling 서비스 에러    |
| `vlm-error`    | VLM Service Error    | 502    | VLM 서비스 에러        |

---

### 시스템 에러 (500)

| Type             | Title          | Status | 설명                       |
| ---------------- | -------------- | ------ | -------------------------- |
| `internal-error` | Internal Error | 500    | 예상치 못한 내부 서버 에러 |

---

## 에러 응답 예시

### 변환 실패

```json
{
  "type": "https://parsekit.dev/errors/conversion-failed",
  "title": "Conversion Failed",
  "status": 422,
  "detail": "LibreOffice conversion timed out after 120 seconds"
}
```

### 지원하지 않는 파일 형식

```json
{
  "type": "https://parsekit.dev/errors/unsupported-media-type",
  "title": "Unsupported Media Type",
  "status": 415,
  "detail": "File type 'application/x-executable' is not supported"
}
```

### 외부 서비스 에러

```json
{
  "type": "https://parsekit.dev/errors/docling-error",
  "title": "Docling Service Error",
  "status": 502,
  "detail": "Failed to connect to Docling service"
}
```

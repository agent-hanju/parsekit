# ParseKit

문서 변환 및 파싱 API 서버 (Spring Boot)

- **문서 변환**: JODConverter + LibreOffice (ODT, PDF 변환)
- **이미지 변환**: Poppler (PDF → 이미지)
- **문서 파싱**: Docling, VLM 기반 OCR (선택적)

## 동작 모드

`/api/parse`는 설정된 외부 서비스에 따라 자동으로 알맞은 파서를 실행합니다:

| Docling | VLM | 활성화되는 파서 | 설명                      |
| ------- | --- | --------------- | ------------------------- |
| ❌      | ❌  | 없음            | 변환 API만 사용 가능      |
| ✅      | ❌  | DoclingParser   | 문서 파싱 (이미지 미지원) |
| ❌      | ✅  | VlmParser       | 이미지 OCR 기반 파싱      |
| ✅      | ✅  | HybridParser    | Docling + VLM 이미지 OCR  |

## API

### 변환 API

#### `POST /api/convert/odt`

문서를 ODT 형식으로 변환

```bash
curl -X POST http://localhost:8000/api/convert/odt \
  -F "file=@document.hwp" \
  -o output.odt
```

**지원 형식:** `.hwp`, `.hwpx`, `.doc`, `.docx`, `.dotx`, `.odt`, `.ott`, `.fodt`, `.rtf`, `.txt`, `.html`, `.xhtml`, `.wpd`, `.abw`, `.xml`, `.md`

#### `POST /api/convert/pdf`

문서를 PDF 형식으로 변환

```bash
curl -X POST http://localhost:8000/api/convert/pdf \
  -F "file=@document.hwpx" \
  -o output.pdf
```

**지원 형식:**
- 텍스트: `.hwp`, `.hwpx`, `.doc`, `.docx`, `.dotx`, `.odt`, `.ott`, `.fodt`, `.rtf`, `.txt`, `.html`, `.xhtml`, `.wpd`, `.abw`, `.xml`, `.md`
- 스프레드시트: `.xls`, `.xlsx`, `.xltx`, `.ods`, `.ots`, `.fods`, `.csv`
- 프레젠테이션: `.ppt`, `.pptx`, `.potx`, `.odp`, `.otp`, `.fodp`
- 기타: `.pdf` (그대로 반환)

#### `POST /api/convert/images`

문서를 이미지로 변환 (페이지별 NDJSON 스트리밍)

```bash
curl -X POST "http://localhost:8000/api/convert/images?format=png&dpi=150" \
  -F "file=@document.pdf"
```

**파라미터:**

| 파라미터 | 기본값 | 설명                     |
| -------- | ------ | ------------------------ |
| `format` | `png`  | 출력 포맷 (`png`, `jpg`) |
| `dpi`    | `150`  | 해상도                   |

**응답 (NDJSON 스트리밍):**

```json
{"page":1,"content":"iVBORw0KGgo...","size":12345,"total_pages":3}
{"page":2,"content":"iVBORw0KGgo...","size":12345,"total_pages":3}
{"page":3,"content":"iVBORw0KGgo...","size":12345,"total_pages":3}
```

#### `GET /api/convert/health`

헬스체크

```bash
curl http://localhost:8000/api/convert/health
```

### 파싱 API

#### `POST /api/parse/parse`

문서를 마크다운으로 파싱

```bash
curl -X POST "http://localhost:8000/api/parse/parse?dpi=150" \
  -F "file=@document.pdf"
```

**파라미터:**

| 파라미터 | 기본값 | 설명               | 적용 파서               |
| -------- | ------ | ------------------ | ----------------------- |
| `dpi`    | `150`  | 이미지 변환 해상도 | VlmParser, HybridParser |

**응답:**

```json
{
  "filename": "document.pdf",
  "markdown": "# 문서 제목\n\n본문 내용..."
}
```

**지원 형식:**
- 문서: `/api/convert/pdf` 지원 형식과 동일
- 이미지: `.png`, `.jpg`, `.jpeg`, `.gif`, `.webp`, `.bmp`, `.tiff` (VlmParser, HybridParser만)
- 플레인 텍스트: `.txt`, `.md` (변환 없이 그대로 반환)

**파일 타입별 처리:**

| 파일 타입 | DoclingParser   | VlmParser               | HybridParser                 |
| --------- | --------------- | ----------------------- | ---------------------------- |
| 이미지    | ❌ 415 에러     | VLM OCR                 | VLM OCR                      |
| 텍스트    | 그대로 반환     | 그대로 반환             | 그대로 반환                  |
| PDF       | Docling 파싱    | 이미지 변환 → OCR       | Docling (embedded) → VLM OCR |
| 기타 문서 | PDF 변환 → 파싱 | PDF 변환 → 이미지 → OCR | PDF 변환 → Docling → VLM     |

## 에러 응답

모든 에러는 통일된 형식으로 반환됩니다:

```json
{
  "error": "CONVERSION_FAILED",
  "message": "convertToPdf failed: document.hwp"
}
```

| HTTP 상태 | 에러 코드                 | 설명                      |
| --------- | ------------------------- | ------------------------- |
| 400       | -                         | 잘못된 요청 (빈 파일 등)  |
| 415       | -                         | 지원하지 않는 미디어 타입 |
| 422       | `CONVERSION_FAILED`       | JODConverter 변환 실패    |
| 422       | `IMAGE_CONVERSION_FAILED` | Poppler 이미지 변환 실패  |
| 502       | `DOCLING_ERROR`           | Docling 서버 오류         |
| 502       | `VLM_ERROR`               | VLM 서버 오류             |
| 500       | `INTERNAL_ERROR`          | 내부 오류                 |

## 실행

### Docker (권장)

```bash
docker build -t parsekit .
docker run -p 8000:8000 parsekit
```

### Docker 개발 환경

소스 코드를 마운트하여 개발/테스트 환경을 구성합니다:

```bash
# 서버 실행 (코드 변경 시 자동 재시작)
docker compose -f docker-compose.dev.yml up

# 테스트 실행
docker compose -f docker-compose.dev.yml run --rm parsekit ./gradlew test

# 빌드만 실행
docker compose -f docker-compose.dev.yml run --rm parsekit ./gradlew build

# 컨테이너 접속
docker compose -f docker-compose.dev.yml run --rm parsekit bash
```

### 로컬 실행

**1. LibreOffice 및 Poppler 설치**

```bash
# Ubuntu/Debian
apt-get install libreoffice libreoffice-java-common poppler-utils

# macOS
brew install --cask libreoffice
brew install poppler
```

**2. HWP 지원 (선택)**

[H2Orestart](https://github.com/ebandal/H2Orestart) 확장 설치:

```bash
wget https://github.com/ebandal/H2Orestart/releases/download/v0.7.9/H2Orestart.oxt
unopkg add --shared H2Orestart.oxt
```

**3. 서버 실행**

```bash
./gradlew bootRun

# 또는
./gradlew build
java -jar build/libs/parsekit-converter-1.0.0.jar
```

## 설정

`application.yml`:

```yaml
server:
  port: 8000

# JODConverter 설정
jodconverter:
  local:
    enabled: true
    office-home: /usr/lib/libreoffice # LibreOffice 설치 경로
    port-numbers: 2002
    max-tasks-per-process: 100
    task-execution-timeout: 120000

# 파서 설정 (선택)
parser:
  # Docling 서버 설정
  docling:
    base-urls:
      - http://localhost:5000
    timeout: 5m
    max-buffer-size: 16777216

  # VLM 서버 설정
  vlm:
    servers:
      - base-url: http://localhost:8080
        model: Qwen/Qwen2-VL-7B-Instruct
    timeout: 2m
    max-buffer-size: 16777216
    max-tokens: 4096
    default-prompt: 'Extract all text from this image accurately.'
    embedded-image-prompt: 'Extract and describe all text, diagrams, charts.'
    image-format: png
```

## 기술 스택

- Java 21 +
- Spring Boot 3.5.x

## 관련 프로젝트

### 라이브러리

- [JODConverter](https://github.com/jodconverter/jodconverter) - LibreOffice Java 연동 라이브러리
- [Apache Tika](https://tika.apache.org/) - 파일 타입 감지
- [flexmark-java](https://github.com/vsch/flexmark-java) - GitHub Flavored Markdown 파서

### 내장 도구 (Docker 이미지에 포함)

- [LibreOffice](https://www.libreoffice.org/) - 문서 변환 엔진
- [H2Orestart](https://github.com/ebandal/H2Orestart) - LibreOffice HWP 지원 확장
- [Poppler](https://poppler.freedesktop.org/) - PDF 렌더링 CLI 도구

### 외부 서비스 (HTTP API로 연동)

아래 서비스는 별도 컨테이너로 실행하고, URL 설정을 통해 연동합니다:

- [Docling Serve](https://github.com/DS4SD/docling-serve) - 문서 파싱 API 서버 (REST API)
- [vLLM](https://github.com/vllm-project/vllm) - OpenAI 호환 API 서버 (VLM OCR용)

## TODO

- [ ] **멀티파일 배치 API 추가** (`POST /api/parse/batch`)
  - Docling API가 `files` 파라미터로 다중 파일 지원
  - 기존 단일 파일 API 유지하면서 배치 엔드포인트 추가 검토
  - 고려사항: 에러 핸들링(부분 실패), 응답 구조, 타임아웃 관리, 메모리
